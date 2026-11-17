package com.example.training.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CsvParser 的单元测试。
 * 换行符・BOM・GBK 的数据在代码里生成：
 * .gitattributes 会把文本文件统一成 LF，编辑器也可能改掉 BOM・编码，做成文件不可靠。
 */
class CsvParserTest {

    private static final String HEADER = "name,email,affiliation,joinedOn";

    private static List<CsvRecord> parse(String text) {
        return CsvParser.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<CsvRecord> parse(byte[] bytes) {
        return CsvParser.parse(new ByteArrayInputStream(bytes));
    }

    private List<CsvRecord> parseResource(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/csv/" + name)) {
            assertThat(in).as("测试用 CSV 不存在：" + name).isNotNull();
            return CsvParser.parse(in);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    // ---- 正常数据 ----

    @Test
    void 普通数据_按行切分并带行号() throws IOException {
        List<CsvRecord> records = parseResource("ok.csv");

        assertThat(records).hasSize(3);
        assertThat(records.get(0)).isEqualTo(
                new CsvRecord(1, List.of("name", "email", "affiliation", "joinedOn")));
        assertThat(records.get(1)).isEqualTo(
                new CsvRecord(2, List.of("山田 太郎", "taro.yamada@example.com", "A 社 开发部", "2026-11-01")));
        assertThat(records.get(2)).isEqualTo(
                new CsvRecord(3, List.of("李 明", "ming.li@example.com", "A 社 开发部", "2026-11-01")));
    }

    @Test
    void 字段不做trim_原样保存() {
        List<CsvRecord> records = parse(HEADER + "\n 山田 太郎 , a@example.com ,,2026-11-01\n");

        assertThat(records.get(1).fields()).containsExactly(" 山田 太郎 ", " a@example.com ", "", "2026-11-01");
    }

    @Test
    void 末尾逗号_最后一个空字段也算一列() {
        List<CsvRecord> records = parse(HEADER + "\n山田 太郎,a@example.com,2026-11-01,\n");

        assertThat(records.get(1).fields()).containsExactly("山田 太郎", "a@example.com", "2026-11-01", "");
    }

    // ---- BOM ----

    @Test
    void 带BOM_去掉BOM后表头正常() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = (HEADER + "\n山田 太郎,a@example.com,A 社,2026-11-01\n").getBytes(StandardCharsets.UTF_8);

        List<CsvRecord> records = parse(concat(bom, body));

        assertThat(records).hasSize(2);
        assertThat(records.get(0).fields().get(0)).isEqualTo("name");
    }

    @Test
    void 只有BOM_结果为0条() {
        assertThat(parse(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF})).isEmpty();
    }

    @Test
    void 空文件_结果为0条() {
        assertThat(parse(new byte[0])).isEmpty();
    }

    // ---- 引号 ----

    @Test
    void 引号内含逗号_算一个字段() throws IOException {
        List<CsvRecord> records = parseResource("quoted_comma.csv");

        assertThat(records.get(1).fields())
                .containsExactly("山田 太郎", "taro.yamada@example.com", "A 社, 开发部", "2026-11-01");
    }

    @Test
    void 两个双引号_转义为一个双引号() throws IOException {
        List<CsvRecord> records = parseResource("quoted_comma.csv");

        assertThat(records.get(2).fields().get(2)).isEqualTo("B 社 \"总部\"");
    }

    @Test
    void 空的引号字段_是空字符串() {
        List<CsvRecord> records = parse(HEADER + "\n山田 太郎,a@example.com,\"\",2026-11-01\n");

        assertThat(records.get(1).fields().get(2)).isEmpty();
    }

    @Test
    void 引号内换行_算字段内容_行号是记录的开始行() throws IOException {
        List<CsvRecord> records = parseResource("multiline_quoted.csv");

        assertThat(records).hasSize(3);
        assertThat(records.get(1).lineNo()).isEqualTo(2);
        assertThat(records.get(1).fields().get(2)).isEqualTo("A 社\n开发部");
        // 第 2 条记录占了第 2・3 行，下一条从第 4 行开始
        assertThat(records.get(2).lineNo()).isEqualTo(4);
    }

    @Test
    void 引号没有闭合_报错并带开始行号() {
        assertThatThrownBy(() -> parseResource("unclosed_quote.csv"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("第 3 行：引号没有闭合");
    }

    @Test
    void 闭合引号后面直接跟其他字符_报错并带行号() {
        assertThatThrownBy(() -> parse(HEADER + "\n山田 太郎,a@example.com,\"A 社\"x,2026-11-01\n"))
                .isInstanceOf(BusinessException.class)
                .hasMessageStartingWith("第 2 行：引号后面不能直接跟其他字符");
    }

    @Test
    void 引号外字段中间的双引号_按普通字符处理() {
        List<CsvRecord> records = parse(HEADER + "\n山田 太郎,a@example.com,A\"社,2026-11-01\n");

        assertThat(records.get(1).fields().get(2)).isEqualTo("A\"社");
    }

    // ---- 换行符 ----

    @Test
    void CRLF换行_正常切分() {
        List<CsvRecord> records = parse(HEADER + "\r\n山田 太郎,a@example.com,A 社,2026-11-01\r\n"
                + "李 明,b@example.com,A 社,2026-11-01\r\n");

        assertThat(records).extracting(CsvRecord::lineNo).containsExactly(1, 2, 3);
        assertThat(records.get(1).fields().get(3)).isEqualTo("2026-11-01");
    }

    @Test
    void 单独的CR换行_正常切分() {
        List<CsvRecord> records = parse(HEADER + "\r山田 太郎,a@example.com,A 社,2026-11-01\r");

        assertThat(records).extracting(CsvRecord::lineNo).containsExactly(1, 2);
    }

    @Test
    void CRLF_引号内换行也只算一行() {
        List<CsvRecord> records = parse(HEADER + "\r\n山田 太郎,a@example.com,\"A 社\r\n开发部\",2026-11-01\r\n"
                + "李 明,b@example.com,A 社,2026-11-01\r\n");

        assertThat(records.get(2).lineNo()).isEqualTo(4);
    }

    @Test
    void 末尾没有换行_最后一行也能读到() {
        List<CsvRecord> records = parse(HEADER + "\n山田 太郎,a@example.com,A 社,2026-11-01");

        assertThat(records).hasSize(2);
        assertThat(records.get(1).fields().get(3)).isEqualTo("2026-11-01");
    }

    @Test
    void 末尾引号字段且没有换行_也能读到() {
        List<CsvRecord> records = parse(HEADER + "\n山田 太郎,a@example.com,2026-11-01,\"A 社\"");

        assertThat(records.get(1).fields().get(3)).isEqualTo("A 社");
    }

    // ---- 空行 ----

    @Test
    void 末尾空行_跳过() {
        List<CsvRecord> records = parse(HEADER + "\n山田 太郎,a@example.com,A 社,2026-11-01\n\n\r\n  \n");

        assertThat(records).hasSize(2);
    }

    @Test
    void 中间空行_跳过但行号照常计数() {
        List<CsvRecord> records = parse(HEADER + "\n山田 太郎,a@example.com,A 社,2026-11-01\n\n"
                + "李 明,b@example.com,A 社,2026-11-01\n");

        assertThat(records).extracting(CsvRecord::lineNo).containsExactly(1, 2, 4);
    }

    @Test
    void 只有逗号的行_不算空行() {
        List<CsvRecord> records = parse(HEADER + "\n,,,\n");

        assertThat(records).hasSize(2);
        assertThat(records.get(1).fields()).containsExactly("", "", "", "");
    }

    @Test
    void 只有一对引号的行_当作空行跳过() {
        List<CsvRecord> records = parse(HEADER + "\n\"\"\n山田 太郎,a@example.com,A 社,2026-11-01\n");

        assertThat(records).extracting(CsvRecord::lineNo).containsExactly(1, 3);
    }

    // ---- 边界值・补充分支 ----

    @Test
    void 只有一列的行_不算空行() {
        List<CsvRecord> records = parse(HEADER + "\n山田 太郎\n");

        assertThat(records).hasSize(2);
        assertThat(records.get(1).fields()).containsExactly("山田 太郎");
    }

    @Test
    void 只有一行且没有换行_也能读到() {
        List<CsvRecord> records = parse(HEADER);

        assertThat(records).containsExactly(
                new CsvRecord(1, List.of("name", "email", "affiliation", "joinedOn")));
    }

    @Test
    void 末尾逗号且没有换行_最后一个空字段也算一列() {
        List<CsvRecord> records = parse(HEADER + "\n山田 太郎,a@example.com,2026-11-01,");

        assertThat(records.get(1).fields()).containsExactly("山田 太郎", "a@example.com", "2026-11-01", "");
    }

    @Test
    void 只有一个转义引号的字段_是一个双引号() {
        List<CsvRecord> records = parse(HEADER + "\n山田 太郎,a@example.com,\"\"\"\",2026-11-01\n");

        assertThat(records.get(1).fields().get(2)).isEqualTo("\"");
    }

    @Test
    void 转义引号在字段末尾_正确闭合() {
        List<CsvRecord> records = parse(HEADER + "\n山田 太郎,a@example.com,\"A 社\"\"\",2026-11-01\n");

        assertThat(records.get(1).fields().get(2)).isEqualTo("A 社\"");
    }

    @Test
    void 闭合引号后接CRLF_正常切分() {
        List<CsvRecord> records = parse(HEADER + "\r\n山田 太郎,a@example.com,2026-11-01,\"A 社\"\r\n"
                + "李 明,b@example.com,2026-11-01,\"A 社\"\r\n");

        assertThat(records).extracting(CsvRecord::lineNo).containsExactly(1, 2, 3);
        assertThat(records.get(2).fields().get(3)).isEqualTo("A 社");
    }

    @Test
    void 引号内单独的CR换行_也计入行号() {
        List<CsvRecord> records = parse(HEADER + "\r山田 太郎,a@example.com,\"A 社\r开发部\",2026-11-01\r"
                + "李 明,b@example.com,A 社,2026-11-01\r");

        assertThat(records.get(1).fields().get(2)).isEqualTo("A 社\r开发部");
        assertThat(records.get(2).lineNo()).isEqualTo(4);
    }

    @Test
    void 引号没有闭合且在最后一个字符_报错() {
        assertThatThrownBy(() -> parse(HEADER + "\n山田 太郎,a@example.com,A 社,\""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("第 2 行：引号没有闭合");
    }

    @Test
    void 中间位置的BOM_不去掉() {
        List<CsvRecord> records = parse(HEADER + "\n﻿山田 太郎,a@example.com,A 社,2026-11-01\n");

        assertThat(records.get(1).fields().get(0)).isEqualTo("﻿山田 太郎");
    }

    // ---- 编码 ----

    @Test
    void 末尾的UTF8多字节字符被截断_报错() {
        // 「山」的 UTF-8 是 E5 B1 B1，只留前 2 个字节
        byte[] truncated = {(byte) 0xE5, (byte) 0xB1};
        byte[] bytes = concat((HEADER + "\n").getBytes(StandardCharsets.UTF_8), truncated);

        assertThatThrownBy(() -> parse(bytes))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是 UTF-8 编码");
    }

    @Test
    void 不是UTF8_报错并提示另存为CSV_UTF8() {
        // 「测试」的 GBK 编码（B2 E2 CA D4），不是合法的 UTF-8
        byte[] gbk = {(byte) 0xB2, (byte) 0xE2, (byte) 0xCA, (byte) 0xD4};
        byte[] bytes = concat((HEADER + "\n").getBytes(StandardCharsets.UTF_8), gbk);

        assertThatThrownBy(() -> parse(bytes))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是 UTF-8 编码")
                .hasMessageContaining("CSV UTF-8");
    }

    @Test
    void 读取失败_包成UncheckedIOException() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("模拟读取失败");
            }
        };

        assertThatThrownBy(() -> CsvParser.parse(broken))
                .isInstanceOf(UncheckedIOException.class);
    }
}
