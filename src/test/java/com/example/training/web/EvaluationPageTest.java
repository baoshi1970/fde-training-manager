package com.example.training.web;

import com.example.training.domain.Course;
import com.example.training.domain.Enrollment;
import com.example.training.domain.EvaluationCriterion;
import com.example.training.repository.CourseRepository;
import com.example.training.repository.EnrollmentRepository;
import com.example.training.repository.EvaluationCriterionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * F8 加权评分：画面测试（权限 W1・W2 ＋ 画面显示）。
 * 每个测试结束后回滚，不影响其他测试类共用的 H2 数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EvaluationPageTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    CourseRepository courseRepository;
    @Autowired
    EnrollmentRepository enrollmentRepository;
    @Autowired
    EvaluationCriterionRepository criterionRepository;

    private Course course(String code) {
        return courseRepository.findAllByOrderByCodeAsc().stream()
                .filter(c -> c.getCode().equals(code)).findFirst().orElseThrow();
    }

    private Enrollment firstEnrollmentOf(Course course) {
        return enrollmentRepository.findByCourseIdOrderByIdAsc(course.getId()).get(0);
    }

    private List<EvaluationCriterion> criteriaOf(Course course) {
        return criterionRepository.findByCourseIdOrderBySortOrderAscIdAsc(course.getId());
    }

    // ---------- 种子数据 ----------

    @Test
    void 种子数据_S2有6个维度且权重合计100_其他课程没有() {
        List<EvaluationCriterion> s2 = criteriaOf(course("S2"));
        assertThat(s2).extracting(EvaluationCriterion::getName)
                .containsExactly("三段法", "给上下文・拆小", "让 AI 跑测试", "不盲信・读懂 diff", "Skill 创建", "协作・主动性");
        assertThat(s2.stream().mapToInt(EvaluationCriterion::getWeight).sum()).isEqualTo(100);
        assertThat(criteriaOf(course("S3"))).isEmpty();
    }

    // ---------- 打分（ADMIN・INSTRUCTOR 都可以：#16） ----------

    /** 按 Issue #8 的例子（4,3,5,4,3,5）给 S2 的第 1 个报名者打分，然后检查一览 */
    private void scoreIssueExampleAndCheckList() throws Exception {
        Course s2 = course("S2");
        Enrollment e = firstEnrollmentOf(s2);
        List<EvaluationCriterion> c = criteriaOf(s2);
        int[] example = {4, 3, 5, 4, 3, 5};
        MockHttpServletRequestBuilder req = post("/evaluations/{id}", e.getId()).with(csrf());
        for (int i = 0; i < c.size(); i++) {
            req.param("score_" + c.get(i).getId(), String.valueOf(example[i]));
        }

        mvc.perform(req)
                .andExpect(redirectedUrl("/evaluations?courseId=" + s2.getId()));

        mvc.perform(get("/evaluations").param("courseId", s2.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("79.0（B）")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void W1_ADMIN在一览看到打分按钮_能打开打分画面() throws Exception {
        Course s2 = course("S2");
        Enrollment e = firstEnrollmentOf(s2);
        mvc.perform(get("/evaluations").param("courseId", s2.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/evaluations/" + e.getId() + "/edit")))
                .andExpect(content().string(not(containsString("打分只有讲师可以进行"))));
        mvc.perform(get("/evaluations/{id}/edit", e.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("三段法")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void W1_ADMIN按Issue例子打分_一览显示79点0和B() throws Exception {
        scoreIssueExampleAndCheckList();
    }

    @Test
    void 未登录访问打分画面_跳转到登录页() throws Exception {
        Enrollment e = firstEnrollmentOf(course("S2"));
        mvc.perform(get("/evaluations/{id}/edit", e.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "teacher", roles = "INSTRUCTOR")
    void W2_INSTRUCTOR按Issue例子打分_一览显示79点0和B() throws Exception {
        scoreIssueExampleAndCheckList();
    }

    @Test
    @WithMockUser(username = "teacher", roles = "INSTRUCTOR")
    void W2_INSTRUCTOR在一览看到打分按钮() throws Exception {
        Course s2 = course("S2");
        Enrollment e = firstEnrollmentOf(s2);
        mvc.perform(get("/evaluations").param("courseId", s2.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/evaluations/" + e.getId() + "/edit")));
    }

    @Test
    @WithMockUser(username = "teacher", roles = "INSTRUCTOR")
    void 得分输入小数时回到表单并显示错误() throws Exception {
        Course s2 = course("S2");
        Enrollment e = firstEnrollmentOf(s2);
        Long criterionId = criteriaOf(s2).get(0).getId();

        mvc.perform(post("/evaluations/{id}", e.getId()).with(csrf())
                        .param("score_" + criterionId, "3.5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1〜5 的整数")));
    }

    @Test
    @WithMockUser(username = "teacher", roles = "INSTRUCTOR")
    void 没打完的报名者显示未完成() throws Exception {
        Course s2 = course("S2");
        mvc.perform(get("/evaluations").param("courseId", s2.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("未完成")));
    }

    @Test
    @WithMockUser(username = "teacher", roles = "INSTRUCTOR")
    void 没设置维度的课程显示未设置评价维度() throws Exception {
        mvc.perform(get("/evaluations").param("courseId", course("S3").getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("未设置评价维度")));
    }

    // ---------- 评价维度设置（只有 ADMIN） ----------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void ADMIN在课程一览看到评价维度链接() throws Exception {
        mvc.perform(get("/courses"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/courses/" + course("S2").getId() + "/criteria")));
    }

    @Test
    @WithMockUser(username = "teacher", roles = "INSTRUCTOR")
    void INSTRUCTOR在课程一览看不到评价维度链接() throws Exception {
        mvc.perform(get("/courses"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/criteria"))));
    }

    @Test
    @WithMockUser(username = "teacher", roles = "INSTRUCTOR")
    void INSTRUCTOR打开评价维度设置是403() throws Exception {
        mvc.perform(get("/admin/courses/{id}/criteria", course("S2").getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void ADMIN打开评价维度设置_显示S2的维度() throws Exception {
        mvc.perform(get("/admin/courses/{id}/criteria", course("S2").getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("三段法")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void ADMIN给S3设置维度_空行被忽略() throws Exception {
        Course s3 = course("S3");
        mvc.perform(post("/admin/courses/{id}/criteria", s3.getId()).with(csrf())
                        .param("rows[0].name", "理解").param("rows[0].weight", "60")
                        .param("rows[1].name", "实践").param("rows[1].weight", "40")
                        .param("rows[2].name", "").param("rows[2].weight", ""))
                .andExpect(redirectedUrl("/admin/courses/" + s3.getId() + "/criteria"));

        assertThat(criteriaOf(s3)).extracting(EvaluationCriterion::getName).containsExactly("理解", "实践");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void ADMIN保存维度_权重合计不是100时显示错误() throws Exception {
        Course s3 = course("S3");
        mvc.perform(post("/admin/courses/{id}/criteria", s3.getId()).with(csrf())
                        .param("rows[0].name", "理解").param("rows[0].weight", "60")
                        .param("rows[1].name", "实践").param("rows[1].weight", "30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("权重合计必须为 100（当前：90）")));

        assertThat(criteriaOf(s3)).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void ADMIN保存维度_权重不是整数时显示错误() throws Exception {
        Course s3 = course("S3");
        mvc.perform(post("/admin/courses/{id}/criteria", s3.getId()).with(csrf())
                        .param("rows[0].name", "理解").param("rows[0].weight", "abc"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("权重必须是整数")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void ADMIN保存维度_名称超过50字时显示错误_不报500() throws Exception {
        // #21：绕过画面的 maxlength 直接 POST
        Course s3 = course("S3");
        mvc.perform(post("/admin/courses/{id}/criteria", s3.getId()).with(csrf())
                        .param("rows[0].name", "评".repeat(51)).param("rows[0].weight", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("维度名称不能超过 50 个字符（当前：51 字）")));

        assertThat(criteriaOf(s3)).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void ADMIN保存维度_同一维度id提交2行时显示错误_原来的维度不变() throws Exception {
        // #22：改写隐藏的 id 直接 POST
        Course s3 = course("S3");
        mvc.perform(post("/admin/courses/{id}/criteria", s3.getId()).with(csrf())
                .param("rows[0].name", "理解").param("rows[0].weight", "60")
                .param("rows[1].name", "实践").param("rows[1].weight", "40"));
        String id = criteriaOf(s3).get(0).getId().toString();

        mvc.perform(post("/admin/courses/{id}/criteria", s3.getId()).with(csrf())
                        .param("rows[0].id", id).param("rows[0].name", "X").param("rows[0].weight", "50")
                        .param("rows[1].id", id).param("rows[1].name", "Y").param("rows[1].weight", "50"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("评价维度重复提交（id：" + id + "）")));

        assertThat(criteriaOf(s3)).extracting(EvaluationCriterion::getName, EvaluationCriterion::getWeight)
                .containsExactly(tuple("理解", 60), tuple("实践", 40));
    }
}
