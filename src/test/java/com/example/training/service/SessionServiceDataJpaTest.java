package com.example.training.service;

import com.example.training.domain.Course;
import com.example.training.domain.CourseCategory;
import com.example.training.domain.CourseSession;
import com.example.training.domain.Instructor;
import com.example.training.domain.SessionMode;
import com.example.training.repository.CourseRepository;
import com.example.training.repository.CourseSessionRepository;
import com.example.training.repository.InstructorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #7 排课冲突检查：用真实的数据库（H2）确认。
 * Mockito 测试（SessionServiceTest）里 Repository 是 mock 的，
 * 这里确认「按讲师・日期查出来的数据」和「修改时排除自己」在真实查询下也成立。
 */
@DataJpaTest
@Import({SessionService.class, CourseService.class, InstructorService.class})
class SessionServiceDataJpaTest {

    static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    @Autowired
    SessionService service;
    @Autowired
    CourseSessionRepository sessionRepository;
    @Autowired
    CourseRepository courseRepository;
    @Autowired
    InstructorRepository instructorRepository;

    Course aiCourse;
    Instructor instructorA;
    Instructor instructorB;
    /** 已有课次：讲师 A，DAY 10:00–12:00，「AI 辅助编码」 */
    CourseSession existing;

    @BeforeEach
    void setUp() {
        aiCourse = courseRepository.save(new Course("S2", "AI 辅助编码", CourseCategory.COMMON, true, 16));
        instructorA = instructorRepository.save(new Instructor("讲师 甲", "a@example.com", null));
        instructorB = instructorRepository.save(new Instructor("讲师 乙", "b@example.com", null));
        existing = sessionRepository.save(session(instructorA, DAY, t(10, 0), t(12, 0)));
    }

    private static LocalTime t(int h, int m) {
        return LocalTime.of(h, m);
    }

    private CourseSession session(Instructor instructor, LocalDate date, LocalTime start, LocalTime end) {
        return new CourseSession(aiCourse, instructor, date, start, end, SessionMode.ONLINE, "Zoom");
    }

    @Test
    void 同一讲师同一天时段重叠则报错且不保存() {
        assertThatThrownBy(() -> service.create(session(instructorA, DAY, t(11, 0), t(13, 0))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 辅助编码")
                .hasMessageContaining("2026-11-10")
                .hasMessageContaining("10:00–12:00");
        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    void 完全包含则报错() {
        assertThatThrownBy(() -> service.create(session(instructorA, DAY, t(10, 30), t(11, 0))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 相接不算冲突() {
        assertThatCode(() -> {
            service.create(session(instructorA, DAY, t(12, 0), t(13, 0)));
            service.create(session(instructorA, DAY, t(9, 0), t(10, 0)));
        }).doesNotThrowAnyException();
        assertThat(sessionRepository.count()).isEqualTo(3);
    }

    @Test
    void 不同讲师同一时段可以() {
        assertThatCode(() -> service.create(session(instructorB, DAY, t(10, 0), t(12, 0))))
                .doesNotThrowAnyException();
        assertThat(sessionRepository.count()).isEqualTo(2);
    }

    @Test
    void 同一讲师不同日期可以() {
        assertThatCode(() -> service.create(session(instructorA, DAY.plusDays(1), t(10, 0), t(12, 0))))
                .doesNotThrowAnyException();
        assertThat(sessionRepository.count()).isEqualTo(2);
    }

    @Test
    void 修改时只改地点不跟自己冲突() {
        CourseSession edited = session(instructorA, DAY, t(10, 0), t(12, 0));  // 画面传来的 id 为空
        edited.setLocation("会议室 B");

        assertThatCode(() -> service.update(existing.getId(), edited)).doesNotThrowAnyException();
        assertThat(sessionRepository.findById(existing.getId()).orElseThrow().getLocation())
                .isEqualTo("会议室 B");
    }

    @Test
    void 修改时跟其他课次冲突则报错() {
        CourseSession other = sessionRepository.save(session(instructorA, DAY, t(13, 0), t(15, 0)));
        CourseSession edited = session(instructorA, DAY, t(13, 30), t(14, 30));

        assertThatThrownBy(() -> service.update(existing.getId(), edited))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("13:00–15:00");
        assertThat(other.getId()).isNotEqualTo(existing.getId());
    }
}
