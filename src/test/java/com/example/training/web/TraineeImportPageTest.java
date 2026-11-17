package com.example.training.web;

import com.example.training.domain.Trainee;
import com.example.training.domain.TraineeStatus;
import com.example.training.repository.TraineeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * F10 CSV 导入学员：画面测试。
 * 每个测试结束后回滚，不影响其他测试类共用的 H2 数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "admin")
class TraineeImportPageTest {

    private static final String HEADER = "name,email,affiliation,joinedOn\n";

    @Autowired
    MockMvc mvc;
    @Autowired
    TraineeRepository traineeRepository;

    /** 上传文件（文件名、内容都可以指定） */
    private ResultActions upload(String filename, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "text/csv", content);
        return mvc.perform(multipart("/trainees/import").file(file).with(csrf()));
    }

    private ResultActions upload(String text) throws Exception {
        return upload("trainees.csv", text.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] resource(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/csv/" + name)) {
            assertThat(in).as("测试用 CSV 不存在：" + name).isNotNull();
            return in.readAllBytes();
        }
    }

    // ---- 画面显示 ----

    @Test
    void 一览画面有CSV导入按钮() throws Exception {
        mvc.perform(get("/trainees"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/trainees/import\"")))
                .andExpect(content().string(containsString("CSV 导入")));
    }

    @Test
    void 导入画面_显示上传表单和格式说明() throws Exception {
        mvc.perform(get("/trainees/import"))
                .andExpect(status().isOk())
                .andExpect(view().name("trainees/import"))
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(containsString("name=\"file\"")))
                .andExpect(content().string(containsString("name,email,affiliation,joinedOn")))
                .andExpect(content().string(containsString("UTF-8")))
                .andExpect(content().string(containsString("500 行")))
                .andExpect(content().string(containsString("在籍")));
    }

    // ---- 成功 ----

    @Test
    void 导入成功_重定向到一览_能看到新学员() throws Exception {
        long before = traineeRepository.count();

        upload(HEADER
                + "张 三,zhang.san@example.com,\"X 社, 开发部\",2026-11-01\n"
                + "李 四,li.si@example.com,X 社 开发部,2026-11-01\n")
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/trainees"))
                .andExpect(flash().attribute("message", "已从 CSV 登记 2 名学员"));

        assertThat(traineeRepository.count()).isEqualTo(before + 2);
        mvc.perform(get("/trainees"))
                .andExpect(content().string(containsString("张 三")))
                .andExpect(content().string(containsString("X 社, 开发部")))
                .andExpect(content().string(containsString("李 四")));
    }

    // ---- 失败：一条也不登记，回到上传画面 ----

    @Test
    void 有行错误_列出全部错误且一条也不登记() throws Exception {
        // 已有学员由测试自己准备，不依赖种子数据（测试结束后回滚）
        traineeRepository.save(new Trainee("三 张", "san.zhang@example.com", "G 社",
                TraineeStatus.ACTIVE, LocalDate.of(2026, 10, 1)));
        long before = traineeRepository.count();

        // 第 2 行正确，第 3 行与已有学员的邮箱重复，第 4 行邮箱格式不正确
        upload(HEADER
                + "张 三,zhang.san@example.com,X 社,2026-11-01\n"
                + "李 四,san.zhang@example.com,X 社,2026-11-01\n"
                + "王 五,wang.wu.example.com,X 社,2026-11-01\n")
                .andExpect(status().isOk())
                .andExpect(view().name("trainees/import"))
                .andExpect(content().string(containsString("CSV 中有 2 处错误，未登记任何学员。")))
                .andExpect(content().string(containsString("第 3 行：该邮箱已被已有学员使用（san.zhang@example.com）")))
                .andExpect(content().string(containsString("第 4 行：邮箱（email）格式不正确（wang.wu.example.com）")));

        assertThat(traineeRepository.count()).isEqualTo(before);
    }

    @Test
    void 文件级错误_显示消息且一条也不登记() throws Exception {
        long before = traineeRepository.count();

        upload("header_only.csv", resource("header_only.csv"))
                .andExpect(status().isOk())
                .andExpect(view().name("trainees/import"))
                .andExpect(content().string(containsString("只有表头，没有学员数据。")));

        assertThat(traineeRepository.count()).isEqualTo(before);
    }

    @Test
    void 不是UTF8_显示另存为的方法() throws Exception {
        long before = traineeRepository.count();
        byte[] gbk = (HEADER + "张 三,zhang.san@example.com,X 社,2026-11-01\n").getBytes("GBK");

        upload("gbk.csv", gbk)
                .andExpect(status().isOk())
                .andExpect(view().name("trainees/import"))
                .andExpect(content().string(containsString("文件不是 UTF-8 编码")));

        assertThat(traineeRepository.count()).isEqualTo(before);
    }

    @Test
    void 没选文件_提示选择文件() throws Exception {
        // 浏览器在没选文件时，也会发送一个文件名为空、内容为空的 file
        upload("", new byte[0])
                .andExpect(status().isOk())
                .andExpect(view().name("trainees/import"))
                .andExpect(content().string(containsString("请选择 CSV 文件")));
    }

    @Test
    void 请求里没有file_也提示选择文件而不是400() throws Exception {
        mvc.perform(multipart("/trainees/import").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("请选择 CSV 文件")));
    }

    @Test
    void 选了0字节的文件_报空文件而不是没选文件() throws Exception {
        upload("empty.csv", resource("empty.csv"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("文件是空的")))
                .andExpect(content().string(not(containsString("请选择 CSV 文件"))));
    }

    @Test
    void 错误消息里的值_做HTML转义() throws Exception {
        upload(HEADER + "张 三,<b>x</b>,X 社,2026-11-01\n")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("（&lt;b&gt;x&lt;/b&gt;）")))
                .andExpect(content().string(not(containsString("<b>x</b>"))));
    }
}
