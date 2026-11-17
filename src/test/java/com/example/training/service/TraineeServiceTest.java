package com.example.training.service;

import com.example.training.domain.Trainee;
import com.example.training.domain.TraineeStatus;
import com.example.training.repository.EnrollmentRepository;
import com.example.training.repository.TraineeRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Service 单元测试的范本：Repository 用 Mock 代替，不连数据库 */
@ExtendWith(MockitoExtension.class)
class TraineeServiceTest {

    @Mock
    TraineeRepository traineeRepository;
    @Mock
    EnrollmentRepository enrollmentRepository;

    /** 邮箱格式检查用真实的 Validator（和画面的 @Email 规则一致） */
    static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    TraineeService service;

    @BeforeEach
    void setUp() {
        service = new TraineeService(traineeRepository, enrollmentRepository, VALIDATOR);
    }

    private Trainee trainee(String name, String email) {
        return new Trainee(name, email, "A 社", TraineeStatus.ACTIVE, LocalDate.of(2026, 10, 1));
    }

    @Test
    void 无条件检索返回全部() {
        List<Trainee> all = List.of(trainee("山田 太郎", "a@example.com"));
        when(traineeRepository.findAllByOrderByIdAsc()).thenReturn(all);

        assertThat(service.search("  ", null)).isEqualTo(all);
    }

    @Test
    void 只按状态检索() {
        service.search(null, TraineeStatus.ON_LEAVE);
        verify(traineeRepository).findByStatusOrderByIdAsc(TraineeStatus.ON_LEAVE);
    }

    @Test
    void 新增_邮箱重复则报业务错误() {
        when(traineeRepository.existsByEmail("a@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(trainee("山田 太郎", "a@example.com")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("a@example.com");
        verify(traineeRepository, never()).save(any());
    }

    @Test
    void 新增_正常保存() {
        Trainee t = trainee("山田 太郎", "a@example.com");
        when(traineeRepository.existsByEmail("a@example.com")).thenReturn(false);
        when(traineeRepository.save(t)).thenReturn(t);

        assertThat(service.create(t)).isSameAs(t);
    }

    @Test
    void 删除_有报名记录则不能删() {
        Trainee t = trainee("山田 太郎", "a@example.com");
        when(traineeRepository.findById(1L)).thenReturn(Optional.of(t));
        when(enrollmentRepository.existsByTraineeId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BusinessException.class);
        verify(traineeRepository, never()).delete(any());
    }

    @Test
    void 不存在的ID报NotFound() {
        when(traineeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(NotFoundException.class);
    }

    // ================= F10 CSV 导入 =================

    private static final String HEADER = "name,email,affiliation,joinedOn";

    /** 表头＋数据行（LF 换行）做成输入流 */
    private static InputStream csv(String... dataLines) {
        String text = HEADER + "\n" + String.join("\n", dataLines) + "\n";
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static InputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private InputStream resource(String name) {
        InputStream in = getClass().getResourceAsStream("/csv/" + name);
        assertThat(in).as("测试用 CSV 不存在：" + name).isNotNull();
        return in;
    }

    /** 导入失败时，取出错误列表；同时确认一条也没有保存 */
    private List<String> importErrors(InputStream in) {
        Throwable thrown = catchThrowable(() -> service.importCsv(in));
        assertThat(thrown).isInstanceOf(CsvImportException.class);
        verify(traineeRepository, never()).saveAll(any());
        return ((CsvImportException) thrown).getErrors();
    }

    /** 文件级错误（BusinessException，不是 CsvImportException）的消息；同时确认一条也没有保存 */
    private String fileError(InputStream in) {
        Throwable thrown = catchThrowable(() -> service.importCsv(in));
        assertThat(thrown).isExactlyInstanceOf(BusinessException.class);
        verify(traineeRepository, never()).saveAll(any());
        verify(traineeRepository, never()).findByEmailIn(any());
        return thrown.getMessage();
    }

    @SuppressWarnings("unchecked")
    private List<Trainee> savedTrainees() {
        ArgumentCaptor<List<Trainee>> captor = ArgumentCaptor.forClass(List.class);
        verify(traineeRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static String repeat(String s, int n) {
        return s.repeat(n);
    }

    // ---- 正常 ----

    @Test
    void 导入_全部正确_全部登记且状态为在籍() throws IOException {
        int count;
        try (InputStream in = resource("ok.csv")) {
            count = service.importCsv(in);
        }

        assertThat(count).isEqualTo(2);
        List<Trainee> saved = savedTrainees();
        assertThat(saved).extracting(Trainee::getName).containsExactly("山田 太郎", "李 明");
        assertThat(saved).extracting(Trainee::getEmail)
                .containsExactly("taro.yamada@example.com", "ming.li@example.com");
        assertThat(saved).extracting(Trainee::getAffiliation).containsOnly("A 社 开发部");
        assertThat(saved).extracting(Trainee::getJoinedOn).containsOnly(LocalDate.of(2026, 11, 1));
        assertThat(saved).extracting(Trainee::getStatus).containsOnly(TraineeStatus.ACTIVE);
        assertThat(saved).extracting(Trainee::getId).containsOnlyNulls();
    }

    @Test
    void 导入_引号内含逗号的所属_原样登记() throws IOException {
        try (InputStream in = resource("quoted_comma.csv")) {
            service.importCsv(in);
        }

        assertThat(savedTrainees()).extracting(Trainee::getAffiliation)
                .containsExactly("A 社, 开发部", "B 社 \"总部\"");
    }

    @Test
    void 导入_带BOM_正常登记() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = (HEADER + "\n张 三,zhang.san@example.com,A 社,2026-11-01\n").getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(body, 0, all, bom.length, body.length);

        assertThat(service.importCsv(new ByteArrayInputStream(all))).isEqualTo(1);
    }

    @Test
    void 导入_前后空白和全角空格_去掉后登记() {
        service.importCsv(csv(" 张 三　, zhang.san@example.com ,　A 社 , 2026-11-01 "));

        Trainee t = savedTrainees().get(0);
        assertThat(t.getName()).isEqualTo("张 三");
        assertThat(t.getEmail()).isEqualTo("zhang.san@example.com");
        assertThat(t.getAffiliation()).isEqualTo("A 社");
        assertThat(t.getJoinedOn()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void 导入_所属为空_可以登记() {
        assertThat(service.importCsv(csv("张 三,zhang.san@example.com,,2026-11-01"))).isEqualTo(1);
    }

    @Test
    void 导入_表头前后有空格_也算正确() {
        InputStream in = bytes(" name , email ,affiliation, joinedOn\n张 三,zhang.san@example.com,A 社,2026-11-01\n");

        assertThat(service.importCsv(in)).isEqualTo(1);
    }

    @Test
    void 导入_只是大小写不同的邮箱_不算重复() {
        int count = service.importCsv(csv(
                "张 三,Zhang.San@example.com,A 社,2026-11-01",
                "李 四,zhang.san@example.com,A 社,2026-11-01"));

        assertThat(count).isEqualTo(2);
    }

    @Test
    void 导入_已有学员的检查只查一次数据库() {
        service.importCsv(csv(
                "张 三,zhang.san@example.com,A 社,2026-11-01",
                "李 四,li.si@example.com,A 社,2026-11-01"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(traineeRepository, times(1)).findByEmailIn(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder("zhang.san@example.com", "li.si@example.com");
        verify(traineeRepository, never()).existsByEmail(any());
    }

    // ---- 文件级错误 ----

    @Test
    void 导入_空文件_报错() throws IOException {
        try (InputStream in = resource("empty.csv")) {
            assertThat(fileError(in)).isEqualTo("文件是空的。请上传包含表头和学员数据的 CSV 文件。");
        }
    }

    @Test
    void 导入_只有BOM_当作空文件() {
        InputStream in = new ByteArrayInputStream(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        assertThat(fileError(in)).startsWith("文件是空的");
    }

    @Test
    void 导入_只有空行_当作空文件() {
        assertThat(fileError(bytes("\n\n  \n"))).startsWith("文件是空的");
    }

    @Test
    void 导入_只有表头_报错() throws IOException {
        try (InputStream in = resource("header_only.csv")) {
            assertThat(fileError(in)).isEqualTo("只有表头，没有学员数据。");
        }
    }

    @Test
    void 导入_表头列名不对_报错() {
        InputStream in = bytes("name,mail,affiliation,joinedOn\n张 三,zhang.san@example.com,A 社,2026-11-01\n");

        assertThat(fileError(in))
                .isEqualTo("表头不正确，应为：name,email,affiliation,joinedOn（列顺序固定）。");
    }

    @Test
    void 导入_表头列顺序不对_报错() {
        InputStream in = bytes("email,name,affiliation,joinedOn\nzhang.san@example.com,张 三,A 社,2026-11-01\n");

        assertThat(fileError(in)).startsWith("表头不正确");
    }

    @Test
    void 导入_没有表头直接是数据_报表头错误() {
        InputStream in = bytes("张 三,zhang.san@example.com,A 社,2026-11-01\n");

        assertThat(fileError(in)).startsWith("表头不正确");
    }

    @Test
    void 导入_正好500行_可以登记() {
        String[] lines = new String[500];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = "学员 " + i + ",u" + i + "@example.com,A 社,2026-11-01";
        }

        assertThat(service.importCsv(csv(lines))).isEqualTo(500);
    }

    @Test
    void 导入_501行_报错() {
        String[] lines = new String[501];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = "学员 " + i + ",u" + i + "@example.com,A 社,2026-11-01";
        }

        assertThat(fileError(csv(lines))).isEqualTo("数据行有 501 行，超过上限 500 行，请分批导入。");
    }

    @Test
    void 导入_引号没有闭合_解析器的错误直接报出() {
        Throwable thrown = catchThrowable(
                () -> service.importCsv(resource("unclosed_quote.csv")));

        assertThat(thrown).isExactlyInstanceOf(BusinessException.class).hasMessage("第 3 行：引号没有闭合");
        verify(traineeRepository, never()).saveAll(any());
    }

    // ---- 行级错误：每类 1 个 ----

    @Test
    void 导入_姓名为空_报错() {
        assertThat(importErrors(csv(",zhang.san@example.com,A 社,2026-11-01")))
                .containsExactly("第 2 行：姓名（name）不能为空");
    }

    @Test
    void 导入_姓名只有空白_当作空() {
        assertThat(importErrors(csv("　 ,zhang.san@example.com,A 社,2026-11-01")))
                .containsExactly("第 2 行：姓名（name）不能为空");
    }

    @Test
    void 导入_邮箱为空_报错且不做其他邮箱检查() {
        assertThat(importErrors(csv("张 三,,A 社,2026-11-01")))
                .containsExactly("第 2 行：邮箱（email）不能为空");
    }

    @Test
    void 导入_入学日期为空_报错() {
        assertThat(importErrors(csv("张 三,zhang.san@example.com,A 社,")))
                .containsExactly("第 2 行：入学日期（joinedOn）不能为空");
    }

    @Test
    void 导入_邮箱格式不正确_报错() {
        assertThat(importErrors(csv("张 三,zhang.san.example.com,A 社,2026-11-01")))
                .containsExactly("第 2 行：邮箱格式不正确（zhang.san.example.com）");
    }

    @Test
    void 导入_日期格式不正确_报错() {
        assertThat(importErrors(csv("张 三,zhang.san@example.com,A 社,2026/11/01")))
                .containsExactly("第 2 行：入学日期不正确（2026/11/01），应为 yyyy-MM-dd 格式的有效日期，如 2026-11-01");
    }

    @Test
    void 导入_不存在的日期_报错() {
        assertThat(importErrors(csv("张 三,zhang.san@example.com,A 社,2026-02-30")))
                .containsExactly("第 2 行：入学日期不正确（2026-02-30），应为 yyyy-MM-dd 格式的有效日期，如 2026-11-01");
    }

    @Test
    void 导入_文件内邮箱重复_报出第一次出现的行号() {
        assertThat(importErrors(csv(
                "张 三,zhang.san@example.com,A 社,2026-11-01",
                "李 四,li.si@example.com,A 社,2026-11-01",
                "王 五,zhang.san@example.com,A 社,2026-11-01",
                "赵 六,zhang.san@example.com,A 社,2026-11-01")))
                .containsExactly(
                        "第 4 行：邮箱与第 2 行重复（zhang.san@example.com）",
                        "第 5 行：邮箱与第 2 行重复（zhang.san@example.com）");
    }

    @Test
    void 导入_与已有学员邮箱重复_报错() {
        when(traineeRepository.findByEmailIn(any()))
                .thenReturn(List.of(trainee("已有 学员", "zhang.san@example.com")));

        assertThat(importErrors(csv("张 三,zhang.san@example.com,A 社,2026-11-01")))
                .containsExactly("第 2 行：该邮箱已被已有学员使用（zhang.san@example.com）");
    }

    @Test
    void 导入_列数太少_报错且这一行不再做其他检查() {
        assertThat(importErrors(csv("张 三,not-an-email,A 社")))
                .containsExactly("第 2 行：列数应为 4 列，实际 3 列");
    }

    @Test
    void 导入_列数太多_报错() {
        // 所属里有逗号却没加引号 → 被拆成 5 列
        assertThat(importErrors(csv("张 三,zhang.san@example.com,A 社, 开发部,2026-11-01")))
                .containsExactly("第 2 行：列数应为 4 列，实际 5 列");
    }

    @Test
    void 导入_只有逗号的行_必填项全部报错() {
        assertThat(importErrors(csv(",,,")))
                .containsExactly(
                        "第 2 行：姓名（name）不能为空",
                        "第 2 行：邮箱（email）不能为空",
                        "第 2 行：入学日期（joinedOn）不能为空");
    }

    // ---- 长度（边界值）----

    @Test
    void 导入_姓名正好50字_可以登记() {
        assertThat(service.importCsv(csv(repeat("张", 50) + ",zhang.san@example.com,A 社,2026-11-01")))
                .isEqualTo(1);
    }

    @Test
    void 导入_姓名51字_报错() {
        assertThat(importErrors(csv(repeat("张", 51) + ",zhang.san@example.com,A 社,2026-11-01")))
                .containsExactly("第 2 行：姓名（name）不能超过 50 个字符（当前 51 个，扩展汉字按 2 个字符计算）");
    }

    // 「𠮷」是扩展汉字，String.length() 为 2：25 个 = 50（OK）、26 个 = 52（NG）。和 F8（#23）的规则一致
    @Test
    void 导入_姓名为扩展汉字25个_按50字可以登记() {
        assertThat(service.importCsv(csv(repeat("𠮷", 25) + ",zhang.san@example.com,A 社,2026-11-01")))
                .isEqualTo(1);
    }

    @Test
    void 导入_姓名为扩展汉字26个_按52字报错() {
        assertThat(importErrors(csv(repeat("𠮷", 26) + ",zhang.san@example.com,A 社,2026-11-01")))
                .containsExactly("第 2 行：姓名（name）不能超过 50 个字符（当前 52 个，扩展汉字按 2 个字符计算）");
    }

    @Test
    void 导入_所属正好100字_可以登记() {
        assertThat(service.importCsv(csv("张 三,zhang.san@example.com," + repeat("部", 100) + ",2026-11-01")))
                .isEqualTo(1);
    }

    @Test
    void 导入_所属101字_报错() {
        assertThat(importErrors(csv("张 三,zhang.san@example.com," + repeat("部", 101) + ",2026-11-01")))
                .containsExactly("第 2 行：所属（affiliation）不能超过 100 个字符（当前 101 个，扩展汉字按 2 个字符计算）");
    }

    @Test
    void 导入_邮箱正好100字_可以登记() {
        // @Email 规定 @ 前面最多 64 字，所以把长度放在域名上：10 + 41 + 1 + 40 + 8 = 100
        String email = "zhang.san@" + repeat("a", 41) + "." + repeat("b", 40) + ".example";
        assertThat(service.importCsv(csv("张 三," + email + ",A 社,2026-11-01"))).isEqualTo(1);
    }

    @Test
    void 导入_邮箱101字_报错() {
        String email = "zhang.san@" + repeat("a", 42) + "." + repeat("b", 40) + ".example";   // 101
        assertThat(importErrors(csv("张 三," + email + ",A 社,2026-11-01")))
                .containsExactly("第 2 行：邮箱（email）不能超过 100 个字符（当前 101 个，扩展汉字按 2 个字符计算）");
    }

    // ---- 多处错误・行号 ----

    @Test
    void 导入_多行有错误_全部列出且行号按文件行号() throws IOException {
        when(traineeRepository.findByEmailIn(any()))
                .thenReturn(List.of(trainee("已有 学员", "existing@example.com")));

        Throwable thrown;
        try (InputStream in = resource("errors_mixed.csv")) {
            thrown = catchThrowable(() -> service.importCsv(in));
        }

        assertThat(thrown).isInstanceOf(CsvImportException.class)
                .hasMessage("CSV 中有 6 处错误，未登记任何学员。请修改后重新上传。");
        // 第 5 行是空行（跳过但计数），第 2 行正确，但因为其他行有错误也不登记
        assertThat(((CsvImportException) thrown).getErrors()).containsExactly(
                "第 3 行：姓名（name）不能为空",
                "第 4 行：邮箱格式不正确（wang.wu.example.com）",
                "第 6 行：入学日期不正确（2026/11/01），应为 yyyy-MM-dd 格式的有效日期，如 2026-11-01",
                "第 7 行：邮箱与第 2 行重复（zhang.san@example.com）",
                "第 8 行：该邮箱已被已有学员使用（existing@example.com）",
                "第 9 行：列数应为 4 列，实际 3 列");
        verify(traineeRepository, never()).saveAll(any());
    }

    @Test
    void 导入_同一行有多处错误_全部列出() {
        assertThat(importErrors(csv(",bad-email,A 社,2026-13-01")))
                .containsExactly(
                        "第 2 行：姓名（name）不能为空",
                        "第 2 行：邮箱格式不正确（bad-email）",
                        "第 2 行：入学日期不正确（2026-13-01），应为 yyyy-MM-dd 格式的有效日期，如 2026-11-01");
    }

    @Test
    void 导入_格式不正确的邮箱重复_也报重复() {
        assertThat(importErrors(csv(
                "张 三,bad-email,A 社,2026-11-01",
                "李 四,bad-email,A 社,2026-11-01")))
                .containsExactly(
                        "第 2 行：邮箱格式不正确（bad-email）",
                        "第 3 行：邮箱格式不正确（bad-email）",
                        "第 3 行：邮箱与第 2 行重复（bad-email）");
    }

    @Test
    void 导入_错误列表不能从外部修改() {
        List<String> errors = importErrors(csv(",zhang.san@example.com,A 社,2026-11-01"));

        assertThatThrownBy(() -> errors.add("x")).isInstanceOf(UnsupportedOperationException.class);
    }
}
