package com.example.training.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 解析器（F10 CSV 导入学员用）。
 * <ul>
 *   <li>按 UTF-8 严格解码，不是 UTF-8 时报错（不悄悄变成乱码）</li>
 *   <li>去掉开头的 BOM（Excel 另存的 CSV 经常带 BOM）</li>
 *   <li>用状态机切分：引号内的逗号・换行算字段内容，{@code ""} 表示一个 {@code "}</li>
 *   <li>换行支持 {@code \r\n}・{@code \n}・{@code \r}</li>
 *   <li>完全空白的行跳过，但行号照常计数</li>
 * </ul>
 * 只负责把文件切成记录，不做业务校验（必填・格式等由 Service 检查）。
 */
public final class CsvParser {

    private static final char BOM = '\uFEFF';

    /** 状态机的状态 */
    private enum State {
        /** 字段开头 */
        FIELD_START,
        /** 引号外的字段中 */
        UNQUOTED,
        /** 引号内 */
        QUOTED,
        /** 引号内刚读到一个 {@code "}（可能是闭合引号，也可能是 {@code ""} 的前一半） */
        QUOTE_IN_QUOTED
    }

    private CsvParser() {
    }

    /**
     * 把整个 CSV 文件解析成记录列表。
     *
     * @throws BusinessException     不是 UTF-8、引号没有闭合、闭合引号后面直接跟了其他字符
     * @throws UncheckedIOException 读取文件失败
     */
    public static List<CsvRecord> parse(InputStream in) {
        return split(decode(in));
    }

    /** 严格按 UTF-8 解码，并去掉开头的 BOM */
    private static String decode(InputStream in) {
        byte[] bytes;
        try {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("读取 CSV 文件失败", e);
        }
        // 默认的 InputStreamReader 遇到非法字节会换成 �，不报错；这里改成报错
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        String text;
        try {
            text = decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new BusinessException(
                    "文件不是 UTF-8 编码。请在 Excel 里「另存为」→「CSV UTF-8（逗号分隔）」后重新上传。");
        }
        if (!text.isEmpty() && text.charAt(0) == BOM) {
            text = text.substring(1);
        }
        return text;
    }

    /** 用状态机切分记录和字段，同时记录每条记录的开始行号 */
    private static List<CsvRecord> split(String text) {
        List<CsvRecord> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        State state = State.FIELD_START;
        int line = 1;              // 当前读到的物理行号
        int recordStartLine = 1;   // 当前记录开始的行号

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean newline = c == '\n' || c == '\r';
            // \r\n 当作一个换行
            boolean crlf = c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n';

            // 引号外（包括闭合引号之后）的逗号・换行：结束当前字段，换行时再结束当前记录
            if (state != State.QUOTED && (c == ',' || newline)) {
                fields.add(field.toString());
                field.setLength(0);
                if (newline) {
                    addRecord(records, recordStartLine, fields);
                    fields = new ArrayList<>();
                    if (crlf) {
                        i++;
                    }
                    line++;
                    recordStartLine = line;
                }
                state = State.FIELD_START;
                continue;
            }

            switch (state) {
                case FIELD_START, UNQUOTED -> {
                    if (c == '"' && state == State.FIELD_START) {
                        state = State.QUOTED;
                    } else {
                        // 引号外字段中间出现的 " 按普通字符处理（Excel 不会生成这种写法）
                        field.append(c);
                        state = State.UNQUOTED;
                    }
                }
                case QUOTED -> {
                    if (c == '"') {
                        state = State.QUOTE_IN_QUOTED;
                    } else {
                        // 引号内的逗号・换行都算字段内容；换行照常计数
                        field.append(c);
                        if (c == '\n' || (c == '\r' && !crlf)) {
                            line++;
                        }
                    }
                }
                case QUOTE_IN_QUOTED -> {
                    if (c == '"') {
                        // "" → 一个 "
                        field.append('"');
                        state = State.QUOTED;
                    } else {
                        // 闭合引号后的逗号・换行已在上面处理，到这里的都是不允许的字符
                        throw new BusinessException("第 " + recordStartLine + " 行：引号后面不能直接跟其他字符（"
                                + "字段内容里的 \" 要写成 \"\"）");
                    }
                }
            }
        }

        // 文件末尾没有换行时，收尾最后一条记录
        if (state == State.QUOTED) {
            throw new BusinessException("第 " + recordStartLine + " 行：引号没有闭合");
        }
        if (state != State.FIELD_START || !fields.isEmpty()) {
            fields.add(field.toString());
            addRecord(records, recordStartLine, fields);
        }
        return records;
    }

    /** 加入一条记录。完全空白的行（只有一个空白字段，包括只写了 "" 的行）跳过 */
    private static void addRecord(List<CsvRecord> records, int lineNo, List<String> fields) {
        boolean blankLine = fields.size() == 1 && fields.get(0).isBlank();
        if (!blankLine) {
            records.add(new CsvRecord(lineNo, fields));
        }
    }
}
