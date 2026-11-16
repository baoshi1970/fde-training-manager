package com.example.training.service;

import com.example.training.domain.Attendance;
import com.example.training.domain.AttendanceStatus;
import com.example.training.domain.Course;
import com.example.training.domain.CourseCategory;
import com.example.training.domain.CourseSession;
import com.example.training.domain.Enrollment;
import com.example.training.domain.Instructor;
import com.example.training.domain.SessionMode;
import com.example.training.domain.Trainee;
import com.example.training.domain.TraineeStatus;
import com.example.training.repository.AttendanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final Long SESSION_ID = 100L;
    private static final Long COURSE_ID = 10L;

    @Mock
    AttendanceRepository attendanceRepository;
    @Mock
    SessionService sessionService;
    @Mock
    EnrollmentService enrollmentService;
    @InjectMocks
    AttendanceService service;

    Course course;
    CourseSession session;
    Trainee taro;
    Trainee hanako;

    @BeforeEach
    void setUp() {
        course = new Course("S2", "AI 辅助编码", CourseCategory.COMMON, true, 16);
        ReflectionTestUtils.setField(course, "id", COURSE_ID);
        session = new CourseSession(course, new Instructor("田中 诚", "t@example.com", null),
                LocalDate.of(2026, 11, 10), LocalTime.of(19, 30), LocalTime.of(21, 0), SessionMode.ONLINE, "Zoom");
        session.setId(SESSION_ID);
        taro = trainee(1L, "山田 太郎");
        hanako = trainee(2L, "佐藤 花子");
    }

    private Trainee trainee(Long id, String name) {
        Trainee t = new Trainee(name, id + "@example.com", "A 社", TraineeStatus.ACTIVE, LocalDate.of(2026, 10, 1));
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    private void givenEnrolled(Trainee... trainees) {
        when(sessionService.get(SESSION_ID)).thenReturn(session);
        List<Enrollment> enrollments = java.util.Arrays.stream(trainees)
                .map(t -> new Enrollment(t, course, LocalDate.of(2026, 10, 5))).toList();
        when(enrollmentService.findByCourse(COURSE_ID)).thenReturn(enrollments);
    }

    private Map<Long, AttendanceStatus> statuses(Object... pairs) {
        Map<Long, AttendanceStatus> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((Long) pairs[i], (AttendanceStatus) pairs[i + 1]);
        }
        return m;
    }

    @Test
    void 没有记录时新增() {
        givenEnrolled(taro, hanako);
        when(attendanceRepository.findBySessionId(SESSION_ID)).thenReturn(List.of());

        service.save(SESSION_ID, statuses(1L, AttendanceStatus.PRESENT, 2L, AttendanceStatus.LATE));

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(a -> a.getTrainee().getId(), Attendance::getStatus)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, AttendanceStatus.PRESENT),
                        org.assertj.core.groups.Tuple.tuple(2L, AttendanceStatus.LATE));
    }

    @Test
    void 已有记录时更新而不是新增() {
        givenEnrolled(taro);
        Attendance existing = new Attendance(session, taro, AttendanceStatus.ABSENT);
        when(attendanceRepository.findBySessionId(SESSION_ID)).thenReturn(List.of(existing));

        service.save(SESSION_ID, statuses(1L, AttendanceStatus.PRESENT));

        assertThat(existing.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void 没选状态且没有记录_不保存() {
        givenEnrolled(taro);
        when(attendanceRepository.findBySessionId(SESSION_ID)).thenReturn(List.of());

        service.save(SESSION_ID, statuses(1L, null));

        verify(attendanceRepository, never()).save(any());
        verify(attendanceRepository, never()).delete(any());
    }

    @Test
    void 没选状态但已有记录_删除记录恢复为未记录() {
        givenEnrolled(taro);
        Attendance existing = new Attendance(session, taro, AttendanceStatus.LATE);
        when(attendanceRepository.findBySessionId(SESSION_ID)).thenReturn(List.of(existing));

        service.save(SESSION_ID, statuses(1L, null));

        verify(attendanceRepository).delete(existing);
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void 非报名者被拒绝_且一条都不保存() {
        givenEnrolled(taro);   // hanako（id=2）没有报名

        assertThatThrownBy(() -> service.save(SESSION_ID,
                statuses(1L, AttendanceStatus.PRESENT, 2L, AttendanceStatus.PRESENT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有报名");

        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void 没有报名者的课程_提交空内容不报错也不保存() {
        givenEnrolled();
        when(attendanceRepository.findBySessionId(SESSION_ID)).thenReturn(List.of());

        service.save(SESSION_ID, Map.of());

        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void 课次不存在_抛出NotFound() {
        when(sessionService.get(999L)).thenThrow(new NotFoundException("课次", 999L));

        assertThatThrownBy(() -> service.save(999L, Map.of()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 各状态人数_没有人的状态为0() {
        when(attendanceRepository.findBySessionId(SESSION_ID)).thenReturn(List.of(
                new Attendance(session, taro, AttendanceStatus.LATE),
                new Attendance(session, hanako, AttendanceStatus.LATE)));

        assertThat(service.countByStatus(SESSION_ID)).containsOnly(
                Map.entry(AttendanceStatus.PRESENT, 0L),
                Map.entry(AttendanceStatus.LATE, 2L),
                Map.entry(AttendanceStatus.ABSENT, 0L));
    }

    @Test
    void 课次一览_带报名人数和已记录人数() {
        when(sessionService.findAll(null)).thenReturn(List.of(session));
        when(enrollmentService.countByCourse(COURSE_ID)).thenReturn(6L);
        when(attendanceRepository.countBySessionId(SESSION_ID)).thenReturn(4L);

        assertThat(service.overview()).singleElement().satisfies(o -> {
            assertThat(o.session()).isSameAs(session);
            assertThat(o.enrolled()).isEqualTo(6L);
            assertThat(o.recorded()).isEqualTo(4L);
        });
    }

    @Test
    void 查询已保存的状态_按学员id返回() {
        when(attendanceRepository.findBySessionId(SESSION_ID)).thenReturn(List.of(
                new Attendance(session, taro, AttendanceStatus.LATE)));

        assertThat(service.findStatuses(SESSION_ID)).containsOnly(Map.entry(1L, AttendanceStatus.LATE));
    }
}
