package com.example.training.service;

import com.example.training.domain.Course;
import com.example.training.domain.CourseCategory;
import com.example.training.domain.CourseSession;
import com.example.training.domain.Instructor;
import com.example.training.domain.SessionMode;
import com.example.training.repository.AttendanceRepository;
import com.example.training.repository.CourseSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    CourseSessionRepository sessionRepository;
    @Mock
    CourseService courseService;
    @Mock
    InstructorService instructorService;
    @Mock
    AttendanceRepository attendanceRepository;
    @InjectMocks
    SessionService service;

    private CourseSession session(LocalTime start, LocalTime end) {
        return new CourseSession(new Course("S2", "AI 辅助编码", CourseCategory.COMMON, true, 16),
                new Instructor("田中 诚", "t@example.com", null),
                LocalDate.of(2026, 11, 10), start, end, SessionMode.ONLINE, "Zoom");
    }

    @Test
    void 结束时间早于开始时间则报错() {
        assertThatThrownBy(() -> service.create(session(LocalTime.of(21, 0), LocalTime.of(19, 30))))
                .isInstanceOf(BusinessException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void 结束时间等于开始时间也报错() {
        assertThatThrownBy(() -> service.create(session(LocalTime.of(19, 30), LocalTime.of(19, 30))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 本周课次数_按周一到周日统计() {
        // 2026-11-12 是周四 → 本周 = 11/9（周一）〜11/15（周日）
        service.countThisWeek(LocalDate.of(2026, 11, 12));
        verify(sessionRepository).countByDateBetween(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15));
    }

    @Test
    void 删除_有出勤记录则报业务错误() {
        CourseSession s = session(LocalTime.of(19, 30), LocalTime.of(21, 0));
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(s));
        when(attendanceRepository.existsBySessionId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("出勤记录");
        verify(sessionRepository, never()).delete(any());
    }

    @Test
    void 删除_没有出勤记录则正常删除() {
        CourseSession s = session(LocalTime.of(19, 30), LocalTime.of(21, 0));
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(s));
        when(attendanceRepository.existsBySessionId(1L)).thenReturn(false);

        service.delete(1L);

        verify(sessionRepository).delete(s);
    }

    /**
     * #7 排课冲突检查。
     * 前提：已有课次 id=1，讲师 A，2026-11-10 10:00–12:00，课程「AI 辅助编码」。
     */
    // 类名用英文：@Nested 会生成 SessionServiceTest$类名.class，
    // 中文类名在日文 Windows（MS932）上 clean 后第一次编译会失败。显示名用 @DisplayName
    @Nested
    @DisplayName("排课冲突")
    class ConflictCheck {

        static final LocalDate DAY = LocalDate.of(2026, 11, 10);
        static final Long A_ID = 10L;
        static final Long B_ID = 20L;

        final Course aiCourse = course(100L, "S2", "AI 辅助编码");
        final Course springCourse = course(200L, "S3", "Spring 入门");
        final Instructor instructorA = instructor(A_ID, "讲师 甲", "a@example.com");
        final Instructor instructorB = instructor(B_ID, "讲师 乙", "b@example.com");

        /** 已有课次 id=1 */
        final CourseSession existing = saved(1L, aiCourse, instructorA, DAY, t(10, 0), t(12, 0));

        private static LocalTime t(int h, int m) {
            return LocalTime.of(h, m);
        }

        private static Course course(Long id, String code, String name) {
            Course c = new Course(code, name, CourseCategory.COMMON, true, 16);
            c.setId(id);
            return c;
        }

        private static Instructor instructor(Long id, String name, String email) {
            Instructor i = new Instructor(name, email, null);
            i.setId(id);
            return i;
        }

        private static CourseSession saved(Long id, Course course, Instructor instructor,
                                           LocalDate date, LocalTime start, LocalTime end) {
            CourseSession s = new CourseSession(course, instructor, date, start, end, SessionMode.ONLINE, "Zoom");
            s.setId(id);
            return s;
        }

        /** 画面传过来的新课次（id 为空，课程・讲师只带 id） */
        private CourseSession input(Instructor instructor, LocalDate date, LocalTime start, LocalTime end) {
            return new CourseSession(aiCourse, instructor, date, start, end, SessionMode.ONLINE, "Zoom");
        }

        /** 讲师 A 在 DAY 已有的课次 */
        private void aHas(CourseSession... sessions) {
            when(sessionRepository.findByInstructorIdAndDate(A_ID, DAY)).thenReturn(List.of(sessions));
        }

        /**
         * 讲师 A 在 DAY 已有的课次（lenient 版）。
         * 「不报错」的测试只看结果，不要求这个 stub 一定被调用，所以用 lenient，
         * 避免 Mockito 严格模式报 UnnecessaryStubbingException。
         */
        private void aHasLeniently(CourseSession... sessions) {
            lenient().when(sessionRepository.findByInstructorIdAndDate(A_ID, DAY)).thenReturn(List.of(sessions));
        }

        private void assertConflictOnCreate(LocalTime start, LocalTime end) {
            aHas(existing);
            assertThatThrownBy(() -> service.create(input(instructorA, DAY, start, end)))
                    .isInstanceOf(BusinessException.class);
        }

        private void assertNoConflictOnCreate(LocalTime start, LocalTime end) {
            aHasLeniently(existing);
            assertThatCode(() -> service.create(input(instructorA, DAY, start, end)))
                    .doesNotThrowAnyException();
        }

        // ---- 时段的边界 ----

        @Test
        void 后半段重叠则报错() {
            assertConflictOnCreate(t(11, 0), t(13, 0));
        }

        @Test
        void 前半段重叠则报错() {
            assertConflictOnCreate(t(9, 0), t(11, 0));
        }

        @Test
        void 接在后面不算冲突() {
            assertNoConflictOnCreate(t(12, 0), t(13, 0));
        }

        @Test
        void 接在前面不算冲突() {
            assertNoConflictOnCreate(t(9, 0), t(10, 0));
        }

        @Test
        void 被已有课次完全包含则报错() {
            assertConflictOnCreate(t(10, 30), t(11, 0));
        }

        @Test
        void 完全包含已有课次则报错() {
            assertConflictOnCreate(t(9, 0), t(13, 0));
        }

        @Test
        void 时段完全相同则报错() {
            assertConflictOnCreate(t(10, 0), t(12, 0));
        }

        // ---- 讲师・日期 ----

        @Test
        void 不同讲师同一时段可以() {
            lenient().when(sessionRepository.findByInstructorIdAndDate(B_ID, DAY)).thenReturn(List.of());
            // 如果实现错用讲师 A 去查，就会查到 existing 而报错
            aHasLeniently(existing);
            assertThatCode(() -> service.create(input(instructorB, DAY, t(10, 0), t(12, 0))))
                    .doesNotThrowAnyException();
        }

        @Test
        void 同一讲师不同日期可以() {
            LocalDate nextDay = DAY.plusDays(1);
            lenient().when(sessionRepository.findByInstructorIdAndDate(A_ID, nextDay)).thenReturn(List.of());
            // 如果实现错用 DAY 去查，就会查到 existing 而报错
            aHasLeniently(existing);
            assertThatCode(() -> service.create(input(instructorA, nextDay, t(10, 0), t(12, 0))))
                    .doesNotThrowAnyException();
        }

        @Test
        void 当天没有课次则不冲突() {
            aHasLeniently();
            assertThatCode(() -> service.create(input(instructorA, DAY, t(10, 0), t(12, 0))))
                    .doesNotThrowAnyException();
        }

        // ---- 修改时排除自己 ----

        @Test
        void 修改时只改地点不跟自己冲突() {
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(existing));
            // 查询结果里有自己：实现如果没排除自己就会报错
            aHasLeniently(existing);
            CourseSession edited = input(instructorA, DAY, t(10, 0), t(12, 0));  // 画面传来的 id 为空
            edited.setLocation("会议室 B");

            assertThatCode(() -> service.update(1L, edited)).doesNotThrowAnyException();
            assertThat(existing.getLocation()).isEqualTo("会议室 B");
        }

        @Test
        void 修改时跟其他课次冲突则报错() {
            CourseSession other = saved(2L, springCourse, instructorA, DAY, t(13, 0), t(15, 0));
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(existing));
            aHas(existing, other);
            CourseSession edited = input(instructorA, DAY, t(13, 30), t(14, 30));
            edited.setLocation("会议室 B");

            assertThatThrownBy(() -> service.update(1L, edited))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Spring 入门");
            // 报错时原课次不能被改掉
            assertThat(existing.getStartTime()).isEqualTo(t(10, 0));
            assertThat(existing.getEndTime()).isEqualTo(t(12, 0));
            assertThat(existing.getLocation()).isEqualTo("Zoom");
        }

        @Test
        void 新增时不因表单带id而排除() {
            aHas(existing);
            CourseSession created = input(instructorA, DAY, t(11, 0), t(13, 0));
            created.setId(1L);  // 表单里混进了已有课次的 id

            assertThatThrownBy(() -> service.create(created))
                    .isInstanceOf(BusinessException.class);
        }

        // ---- 错误消息 ----

        @Test
        void 错误消息包含课程名日期时段() {
            aHas(existing);
            assertThatThrownBy(() -> service.create(input(instructorA, DAY, t(11, 0), t(13, 0))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("AI 辅助编码")
                    .hasMessageContaining("2026-11-10")
                    .hasMessageContaining("10:00–12:00");
        }

        @Test
        void 多场中只报冲突的那一场() {
            CourseSession other = saved(2L, springCourse, instructorA, DAY, t(14, 0), t(16, 0));
            aHas(existing, other);
            assertThatThrownBy(() -> service.create(input(instructorA, DAY, t(15, 0), t(17, 0))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Spring 入门")
                    .hasMessageContaining("14:00–16:00")
                    .hasMessageNotContaining("AI 辅助编码");
        }

        // ---- 校验顺序 ----

        @Test
        void 时间不正确时先报时间错误() {
            // 当天有会冲突的课次：如果先查冲突，消息就会变成冲突消息
            aHasLeniently(existing);
            assertThatThrownBy(() -> service.create(input(instructorA, DAY, t(12, 0), t(10, 0))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("结束时间必须晚于开始时间");
        }
    }
}
