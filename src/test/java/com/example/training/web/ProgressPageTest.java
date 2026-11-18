package com.example.training.web;

import com.example.training.domain.Attendance;
import com.example.training.domain.AttendanceStatus;
import com.example.training.domain.CourseSession;
import com.example.training.domain.Trainee;
import com.example.training.domain.TraineeStatus;
import com.example.training.repository.AttendanceRepository;
import com.example.training.repository.CourseSessionRepository;
import com.example.training.repository.TraineeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * #9 学员进度看板的画面（H2＋种子数据）。
 * 种子数据里没有出勤和得分，所以需要「要关注」的学员时，在测试里加出勤记录（@Transactional 测试后回滚）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProgressPageTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TraineeRepository traineeRepository;
    @Autowired
    CourseSessionRepository sessionRepository;
    @Autowired
    AttendanceRepository attendanceRepository;
    @Autowired
    Clock clock;

    @Test
    void 未登录访问看板_跳转到登录页() throws Exception {
        mvc.perform(get("/progress"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 管理员可以打开看板_只显示在籍学员() throws Exception {
        mvc.perform(get("/progress"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("学员进度看板")))
                .andExpect(content().string(containsString("山田 太郎")))      // 在籍
                .andExpect(content().string(not(containsString("鈴木 一郎"))))  // 休学
                .andExpect(content().string(not(containsString("张 伟"))));    // 结业
    }

    @Test
    @WithMockUser(username = "teacher", roles = "INSTRUCTOR")
    void 讲师也可以打开看板() throws Exception {
        mvc.perform(get("/progress"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("山田 太郎")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 表格下方的说明写出出勤的统计截止日() throws Exception {
        // 「今天」用 Clock 的日期具体写出来，避免「今天」这个说法暧昧
        mvc.perform(get("/progress"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("仅统计 " + LocalDate.now(clock) + " 及以前的课次")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 没有出勤和评分时显示横线() throws Exception {
        mvc.perform(get("/progress"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("—")))
                .andExpect(content().string(not(containsString("要关注</span>"))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 只看要关注_只显示要关注的学员并标红() throws Exception {
        Trainee 山田 = traineeRepository.findByStatusOrderByIdAsc(TraineeStatus.ACTIVE).get(0);
        CourseSession past = sessionRepository.findAll().stream()
                .filter(s -> s.getDate().isBefore(LocalDate.now(clock)))
                .min(Comparator.comparing(CourseSession::getDate))
                .orElseThrow();
        attendanceRepository.save(new Attendance(past, 山田, AttendanceStatus.ABSENT));  // 0% → 要关注

        mvc.perform(get("/progress").param("attention", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("山田 太郎")))
                .andExpect(content().string(containsString("0%")))
                .andExpect(content().string(containsString("要关注</span>")))
                .andExpect(content().string(not(containsString("李 明"))));   // 在籍但不要关注
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 只看要关注_没有要关注的学员时显示提示() throws Exception {
        mvc.perform(get("/progress").param("attention", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("没有要关注的学员")))
                .andExpect(content().string(not(containsString("山田 太郎"))));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 主菜单的学员进度看板可以点击() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/progress\"")));
    }
}
