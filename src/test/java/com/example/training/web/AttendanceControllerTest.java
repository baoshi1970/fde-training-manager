package com.example.training.web;

import com.example.training.domain.AttendanceStatus;
import com.example.training.domain.CourseSession;
import com.example.training.domain.Enrollment;
import com.example.training.domain.Trainee;
import com.example.training.domain.TraineeStatus;
import com.example.training.repository.AttendanceRepository;
import com.example.training.repository.CourseSessionRepository;
import com.example.training.repository.EnrollmentRepository;
import com.example.training.repository.TraineeRepository;
import com.example.training.service.AttendanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 出勤登记的画面级测试（H2＋种子数据：S2 有 6 位报名者，高橋 健太也报了 S2，鈴木 一郎没报名任何课程） */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional   // 每个用例结束后回滚，避免互相污染共享的 H2 数据
class AttendanceControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    CourseSessionRepository sessionRepository;
    @Autowired
    EnrollmentRepository enrollmentRepository;
    @Autowired
    TraineeRepository traineeRepository;
    @Autowired
    AttendanceService attendanceService;
    @Autowired
    AttendanceRepository attendanceRepository;

    private CourseSession s2Session() {
        return sessionRepository.findAll().stream()
                .filter(s -> "S2".equals(s.getCourse().getCode())).findFirst().orElseThrow();
    }

    @Test
    @WithMockUser(username = "admin")
    void 课次一览可以进入出勤登记() throws Exception {
        mvc.perform(get("/attendance"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("登记出勤")))
                .andExpect(content().string(containsString("人已记录")));
    }

    @Test
    @WithMockUser(username = "admin")
    void 登记页显示各状态人数和全部出席按钮() throws Exception {
        CourseSession s = s2Session();
        mvc.perform(get("/attendance/" + s.getId()))
                .andExpect(content().string(containsString("未记录")))
                .andExpect(content().string(containsString("全部设为出席")));
    }

    @Test
    @WithMockUser(username = "admin")
    void 只列出该课程的报名者() throws Exception {
        CourseSession s = s2Session();
        mvc.perform(get("/attendance/" + s.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("山田 太郎")))
                .andExpect(content().string(containsString("高橋 健太")))
                .andExpect(content().string(not(containsString("鈴木 一郎"))));
    }

    @Test
    @WithMockUser(username = "admin")
    void 保存后再次打开显示已保存的状态_再次保存是更新() throws Exception {
        CourseSession s = s2Session();
        Long taroId = enrollmentRepository.findByCourseIdOrderByIdAsc(s.getCourse().getId()).get(0).getTrainee().getId();

        mvc.perform(post("/attendance/" + s.getId()).with(csrf()).param("status_" + taroId, "LATE"))
                .andExpect(redirectedUrl("/attendance/" + s.getId()));
        assertThat(attendanceService.findStatuses(s.getId())).containsEntry(taroId, AttendanceStatus.LATE);

        mvc.perform(post("/attendance/" + s.getId()).with(csrf()).param("status_" + taroId, "ABSENT"));
        assertThat(attendanceService.findStatuses(s.getId())).containsOnly(Map.entry(taroId, AttendanceStatus.ABSENT));
    }

    @Test
    @WithMockUser(username = "admin")
    void 提交非报名者_显示业务错误而不是500() throws Exception {
        CourseSession s = s2Session();
        Trainee notEnrolled = traineeRepository.findAll().stream()
                .filter(t -> enrollmentRepository.findByTraineeIdOrderByIdAsc(t.getId()).isEmpty())
                .findFirst().orElseThrow();

        mvc.perform(post("/attendance/" + s.getId()).with(csrf()).param("status_" + notEnrolled.getId(), "PRESENT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertThat(attendanceService.findStatuses(s.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin")
    void 提交非法状态值_显示业务错误而不是500() throws Exception {
        CourseSession s = s2Session();
        mvc.perform(post("/attendance/" + s.getId()).with(csrf()).param("status_1", "BOGUS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @WithMockUser(username = "admin")
    void 删除有出勤记录的课次_不报500_出勤一起删除() throws Exception {
        CourseSession s = s2Session();
        Long taroId = enrollmentRepository.findByCourseIdOrderByIdAsc(s.getCourse().getId()).get(0).getTrainee().getId();
        attendanceService.save(s.getId(), Map.of(taroId, AttendanceStatus.PRESENT));

        mvc.perform(post("/sessions/" + s.getId() + "/delete").with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(sessionRepository.existsById(s.getId())).isFalse();
    }

    @Test
    @WithMockUser(username = "admin")
    void 取消报名后删除学员_不报500_出勤一起删除() throws Exception {
        CourseSession s = s2Session();
        Trainee t = traineeRepository.save(new Trainee("测试 学员", "attendance-del@example.com", "A 社",
                TraineeStatus.ACTIVE, LocalDate.of(2026, 10, 1)));
        Enrollment e = enrollmentRepository.save(new Enrollment(t, s.getCourse(), LocalDate.of(2026, 11, 1)));
        attendanceService.save(s.getId(), Map.of(t.getId(), AttendanceStatus.PRESENT));

        // 取消报名：出勤记录还在（学员已经不在登记画面里）
        mvc.perform(post("/enrollments/" + e.getId() + "/cancel").param("courseId", String.valueOf(s.getCourse().getId())).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(attendanceRepository.existsByTraineeId(t.getId())).isTrue();

        mvc.perform(post("/trainees/" + t.getId() + "/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "已删除学员"));

        traineeRepository.flush();   // 让外键约束在这里生效（测试在事务里，不 flush 就发现不了 500）
        assertThat(traineeRepository.existsById(t.getId())).isFalse();
        assertThat(attendanceRepository.existsByTraineeId(t.getId())).isFalse();
    }

    @Test
    @WithMockUser(username = "admin")
    void 删除确认提示出勤记录也会一起删除() throws Exception {
        mvc.perform(get("/sessions"))
                .andExpect(content().string(containsString("出勤记录也会一起删除")));
        mvc.perform(get("/trainees"))
                .andExpect(content().string(containsString("出勤记录也会一起删除")));
    }

    @Test
    @WithMockUser(username = "admin")
    void 不存在的课次返回404() throws Exception {
        mvc.perform(get("/attendance/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin")
    void 主菜单出现出勤登记() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("出勤登记")));
    }
}
