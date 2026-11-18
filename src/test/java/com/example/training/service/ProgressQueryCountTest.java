package com.example.training.service;

import com.example.training.domain.Attendance;
import com.example.training.domain.AttendanceStatus;
import com.example.training.domain.Course;
import com.example.training.domain.CourseCategory;
import com.example.training.domain.CourseSession;
import com.example.training.domain.CriterionScore;
import com.example.training.domain.Enrollment;
import com.example.training.domain.EvaluationCriterion;
import com.example.training.domain.Instructor;
import com.example.training.domain.SessionMode;
import com.example.training.domain.Trainee;
import com.example.training.domain.TraineeStatus;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #9 验收条件「学员 20 人・课次 50 场，不要每个学员发 N 次查询」：
 * 用 Hibernate 的统计功能数 SQL 条数，学员 2 人和 20 人时条数必须相同（#9 计划第 3 节）。
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import({ProgressService.class, ProgressQueryCountTest.FixedClockConfig.class})
class ProgressQueryCountTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    static final int SESSIONS = 50;

    /** 内部类名用英文：中文类名在日文 Windows 上 clean 构建会失败（F7 328e1cc） */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE);
        }
    }

    @Autowired
    ProgressService service;
    @Autowired
    TestEntityManager em;
    @Autowired
    EntityManagerFactory emf;

    final List<CourseSession> sessions = new ArrayList<>();
    final List<Course> courses = new ArrayList<>();
    final List<List<EvaluationCriterion>> criteriaOfCourse = new ArrayList<>();
    int traineeNo = 0;

    @BeforeEach
    void setUp() {
        Instructor instructor = em.persist(new Instructor("讲师 甲", "teacher@example.com", null));
        // 课程 2 门，各有 2 个评价维度（权重 60・40）
        for (int c = 0; c < 2; c++) {
            Course course = em.persist(new Course("S" + c, "课程 " + c, CourseCategory.COMMON, true, 16));
            courses.add(course);
            criteriaOfCourse.add(List.of(
                    em.persist(new EvaluationCriterion(course, "维度 A", 60, 0)),
                    em.persist(new EvaluationCriterion(course, "维度 B", 40, 1))));
        }
        // 课次 50 场，都在今天以前
        for (int i = 0; i < SESSIONS; i++) {
            Course course = courses.get(i % courses.size());
            sessions.add(em.persist(new CourseSession(course, instructor, TODAY.minusDays(i),
                    LocalTime.of(10, 0), LocalTime.of(12, 0), SessionMode.ONLINE, "Zoom")));
        }
    }

    /** 加 n 名在籍学员：每人报名全部课程、全部维度打分、50 场课次全部有出勤记录 */
    private void addTrainees(int n) {
        AttendanceStatus[] statuses = AttendanceStatus.values();
        for (int k = 0; k < n; k++) {
            traineeNo++;
            Trainee t = em.persist(new Trainee("学员 " + traineeNo, "t" + traineeNo + "@example.com", null,
                    TraineeStatus.ACTIVE, TODAY.minusMonths(1)));
            for (int c = 0; c < courses.size(); c++) {
                Enrollment e = em.persist(new Enrollment(t, courses.get(c), TODAY.minusMonths(1)));
                for (EvaluationCriterion ec : criteriaOfCourse.get(c)) {
                    em.persist(new CriterionScore(e, ec, 1 + (traineeNo + c) % 5));
                }
            }
            for (int i = 0; i < SESSIONS; i++) {
                em.persist(new Attendance(sessions.get(i), t, statuses[(traineeNo + i) % statuses.length]));
            }
        }
    }

    /** 清空一级缓存后执行看板，返回发出的 SQL 条数 */
    private long countStatements() {
        em.flush();
        em.clear();   // 不清的话，刚 persist 的实体都在缓存里，测不出 EAGER 关联的连带查询
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        long start = System.nanoTime();
        List<ProgressRow> rows = service.board(false);
        long millis = (System.nanoTime() - start) / 1_000_000;
        long count = stats.getPrepareStatementCount();
        System.out.printf("[F9] 学员 %d 人・课次 %d 场：SQL %d 条，%d ms%n", rows.size(), SESSIONS, count, millis);
        return count;
    }

    @Test
    void 学员2人和20人时SQL条数相同() {
        addTrainees(2);
        long for2 = countStatements();

        addTrainees(18);
        long for20 = countStatements();

        assertThat(for20).isEqualTo(for2);
    }
}
