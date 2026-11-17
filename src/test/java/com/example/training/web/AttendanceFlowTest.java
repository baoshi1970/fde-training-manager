package com.example.training.web;

import com.example.training.domain.Attendance;
import com.example.training.domain.AttendanceStatus;
import com.example.training.domain.CourseSession;
import com.example.training.domain.Trainee;
import com.example.training.repository.AttendanceRepository;
import com.example.training.repository.CourseRepository;
import com.example.training.repository.CourseSessionRepository;
import com.example.training.repository.TraineeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** F6 出勤登记的画面测试（种子数据：S2 的报名者有高橋 健太，鈴木 一郎没报名）。每个测试结束后回滚 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "admin")
class AttendanceFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    CourseRepository courseRepository;
    @Autowired
    CourseSessionRepository sessionRepository;
    @Autowired
    TraineeRepository traineeRepository;
    @Autowired
    AttendanceRepository attendanceRepository;

    CourseSession s2Session;
    Trainee kenta;    // S2 报名者
    Trainee ichiro;   // 没报名

    @BeforeEach
    void setUp() {
        Long s2 = courseRepository.findAllByOrderByCodeAsc().stream()
                .filter(c -> c.getCode().equals("S2")).findFirst().orElseThrow().getId();
        s2Session = sessionRepository.findByCourseIdOrderByDateAscStartTimeAsc(s2).get(0);
        kenta = traineeByEmail("kenta.takahashi@example.com");
        ichiro = traineeByEmail("ichiro.suzuki@example.com");
    }

    private Trainee traineeByEmail(String email) {
        return traineeRepository.findAllByOrderByIdAsc().stream()
                .filter(t -> t.getEmail().equals(email)).findFirst().orElseThrow();
    }

    private String formUrl() {
        return "/attendance/sessions/" + s2Session.getId();
    }

    @Test
    void 主菜单的出勤登记可以点击() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("href=\"/attendance\"")));
    }

    @Test
    void 课次一览显示登记出勤按钮() throws Exception {
        mvc.perform(get("/attendance"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(formUrl())));
    }

    @Test
    void 课次排期一览每行有出勤入口() throws Exception {
        // issue：「从课次一览进入某个课次」（讲师建议吸收 #18 的设计）
        mvc.perform(get("/sessions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"" + formUrl() + "\"")));
    }

    @Test
    void 登记表单只列出该课程的报名者() throws Exception {
        mvc.perform(get(formUrl()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("高橋 健太")))
                .andExpect(content().string(not(containsString("鈴木 一郎"))));
    }

    @Test
    void 再次保存是更新_只有一条且回显已保存的状态() throws Exception {
        String field = "statuses[" + kenta.getId() + "]";
        mvc.perform(post(formUrl()).with(csrf()).param(field, "PRESENT"))
                .andExpect(redirectedUrl(formUrl()));
        mvc.perform(post(formUrl()).with(csrf()).param(field, "LATE"))
                .andExpect(redirectedUrl(formUrl()));

        assertThat(attendanceRepository.findBySessionId(s2Session.getId()))
                .singleElement()
                .extracting(Attendance::getStatus).isEqualTo(AttendanceStatus.LATE);
        mvc.perform(get(formUrl()))
                .andExpect(content().string(containsString("value=\"LATE\" selected=\"selected\"")));
    }

    @Test
    void 改回未记录则删除记录() throws Exception {
        String field = "statuses[" + kenta.getId() + "]";
        mvc.perform(post(formUrl()).with(csrf()).param(field, "PRESENT"));
        mvc.perform(post(formUrl()).with(csrf()).param(field, ""))
                .andExpect(redirectedUrl(formUrl()));

        assertThat(attendanceRepository.findBySessionId(s2Session.getId())).isEmpty();
    }

    @Test
    void 非报名者被拒绝并显示错误() throws Exception {
        mvc.perform(post(formUrl()).with(csrf()).param("statuses[" + ichiro.getId() + "]", "PRESENT"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("没有报名")));

        assertThat(attendanceRepository.findBySessionId(s2Session.getId())).isEmpty();
    }

    @Test
    void 提交非法状态值_显示业务错误而不是400_什么都不保存() throws Exception {
        // 绕过画面的下拉框直接 POST（#17 review 第 4 点）
        mvc.perform(post(formUrl()).with(csrf())
                        .param("statuses[" + kenta.getId() + "]", "PRESENT")
                        .param("statuses[" + ichiro.getId() + "]", "XXX"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("提交的出勤状态不正确")));

        assertThat(attendanceRepository.findBySessionId(s2Session.getId())).isEmpty();
    }

    @Test
    void 提交非法学员id_显示业务错误而不是400() throws Exception {
        mvc.perform(post(formUrl()).with(csrf()).param("statuses[abc]", "PRESENT"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("提交的出勤状态不正确")));

        assertThat(attendanceRepository.findBySessionId(s2Session.getId())).isEmpty();
    }

    @Test
    void 删除有出勤记录的课次_显示业务错误而不是500() throws Exception {
        attendanceRepository.save(new Attendance(s2Session, kenta, AttendanceStatus.PRESENT));

        mvc.perform(post("/sessions/" + s2Session.getId() + "/delete").with(csrf()))
                .andExpect(redirectedUrl("/sessions"))
                .andExpect(flash().attribute("error", containsString("出勤记录")));

        assertThat(sessionRepository.existsById(s2Session.getId())).isTrue();
    }

    @Test
    void 不存在的课次返回404() throws Exception {
        mvc.perform(get("/attendance/sessions/9999"))
                .andExpect(status().isNotFound());
    }
}
