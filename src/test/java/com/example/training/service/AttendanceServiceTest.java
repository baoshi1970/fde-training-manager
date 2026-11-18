package com.example.training.service;

import com.example.training.domain.*;
import com.example.training.repository.AttendanceRepository;
import com.example.training.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    AttendanceRepository attendanceRepository;
    @Mock
    EnrollmentRepository enrollmentRepository;
    @Mock
    SessionService sessionService;
    @Mock
    TraineeService traineeService;
    @InjectMocks
    AttendanceService service;

    static final Long SESSION_ID = 1L;
    static final Long COURSE_ID = 10L;

    CourseSession session;

    @BeforeEach
    void setUp() {
        Course course = new Course("S2", "AI 辅助编码", CourseCategory.COMMON, true, 16);
        course.setId(COURSE_ID);
        session = new CourseSession(course, new Instructor("田中 诚", "t@example.com", null),
                LocalDate.of(2026, 11, 16), LocalTime.of(19, 30), LocalTime.of(21, 0), SessionMode.ONLINE, "Zoom");
        session.setId(SESSION_ID);
    }

    private Trainee trainee(Long id, String name) {
        Trainee t = new Trainee(name, "t" + id + "@example.com", "A 社", TraineeStatus.ACTIVE, LocalDate.of(2026, 10, 1));
        t.setId(id);
        return t;
    }

    /** 课次存在，且该学员是报名者 */
    private Trainee enrolled(Long id, String name) {
        Trainee t = trainee(id, name);
        when(traineeService.get(id)).thenReturn(t);
        when(enrollmentRepository.existsByTraineeIdAndCourseId(id, COURSE_ID)).thenReturn(true);
        return t;
    }

    // ---- 报名者一览 ----

    @Test
    void 报名者一览_只取该课次所属课程的报名者() {
        when(sessionService.get(SESSION_ID)).thenReturn(session);
        List<Enrollment> list = List.of(new Enrollment(trainee(100L, "山田 太郎"), session.getCourse(), LocalDate.of(2026, 11, 1)));
        when(enrollmentRepository.findByCourseIdOrderByIdAsc(COURSE_ID)).thenReturn(list);

        assertThat(service.enrollees(SESSION_ID)).isEqualTo(list);
    }

    @Test
    void 已保存状态_按学员ID返回() {
        Trainee t = trainee(100L, "山田 太郎");
        when(attendanceRepository.findBySessionId(SESSION_ID))
                .thenReturn(List.of(new Attendance(session, t, AttendanceStatus.LATE)));

        assertThat(service.statusesOf(SESSION_ID)).containsExactly(Map.entry(100L, AttendanceStatus.LATE));
    }

    // ---- 保存 ----

    @Test
    void 新增_没有旧记录则新建一条() {
        when(sessionService.get(SESSION_ID)).thenReturn(session);
        Trainee t = enrolled(100L, "山田 太郎");
        when(attendanceRepository.findBySessionIdAndTraineeId(SESSION_ID, 100L)).thenReturn(Optional.empty());

        service.save(SESSION_ID, Map.of(100L, AttendanceStatus.PRESENT));

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).save(captor.capture());
        assertThat(captor.getValue().getSession()).isSameAs(session);
        assertThat(captor.getValue().getTrainee()).isSameAs(t);
        assertThat(captor.getValue().getStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void 更新_有旧记录则只改状态不新增() {
        when(sessionService.get(SESSION_ID)).thenReturn(session);
        Trainee t = enrolled(100L, "山田 太郎");
        Attendance existing = new Attendance(session, t, AttendanceStatus.PRESENT);
        when(attendanceRepository.findBySessionIdAndTraineeId(SESSION_ID, 100L)).thenReturn(Optional.of(existing));

        service.save(SESSION_ID, Map.of(100L, AttendanceStatus.LATE));

        assertThat(existing.getStatus()).isEqualTo(AttendanceStatus.LATE);
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void 更新_状态相同再保存也不新增() {
        when(sessionService.get(SESSION_ID)).thenReturn(session);
        Trainee t = enrolled(100L, "山田 太郎");
        Attendance existing = new Attendance(session, t, AttendanceStatus.ABSENT);
        when(attendanceRepository.findBySessionIdAndTraineeId(SESSION_ID, 100L)).thenReturn(Optional.of(existing));

        service.save(SESSION_ID, Map.of(100L, AttendanceStatus.ABSENT));

        assertThat(existing.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void 非报名者被拒绝() {
        when(sessionService.get(SESSION_ID)).thenReturn(session);
        when(traineeService.get(200L)).thenReturn(trainee(200L, "鈴木 一郎"));
        when(enrollmentRepository.existsByTraineeIdAndCourseId(200L, COURSE_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.save(SESSION_ID, Map.of(200L, AttendanceStatus.PRESENT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("鈴木 一郎")
                .hasMessageContaining("没有报名");
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void 混有非报名者时整批都不保存() {
        when(sessionService.get(SESSION_ID)).thenReturn(session);
        enrolled(100L, "山田 太郎");
        when(traineeService.get(200L)).thenReturn(trainee(200L, "鈴木 一郎"));
        when(enrollmentRepository.existsByTraineeIdAndCourseId(200L, COURSE_ID)).thenReturn(false);
        Map<Long, AttendanceStatus> input = new LinkedHashMap<>();
        input.put(100L, AttendanceStatus.PRESENT);   // 报名者在前
        input.put(200L, AttendanceStatus.PRESENT);

        assertThatThrownBy(() -> service.save(SESSION_ID, input)).isInstanceOf(BusinessException.class);
        verify(attendanceRepository, never()).save(any());
        verify(attendanceRepository, never()).delete(any());
    }

    @Test
    void 未记录_没有旧记录则不保存() {
        when(sessionService.get(SESSION_ID)).thenReturn(session);
        enrolled(100L, "山田 太郎");
        when(attendanceRepository.findBySessionIdAndTraineeId(SESSION_ID, 100L)).thenReturn(Optional.empty());
        Map<Long, AttendanceStatus> input = new HashMap<>();
        input.put(100L, null);

        service.save(SESSION_ID, input);

        verify(attendanceRepository, never()).save(any());
        verify(attendanceRepository, never()).delete(any());
    }

    @Test
    void 未记录_有旧记录则删除() {
        when(sessionService.get(SESSION_ID)).thenReturn(session);
        Trainee t = enrolled(100L, "山田 太郎");
        Attendance existing = new Attendance(session, t, AttendanceStatus.PRESENT);
        when(attendanceRepository.findBySessionIdAndTraineeId(SESSION_ID, 100L)).thenReturn(Optional.of(existing));
        Map<Long, AttendanceStatus> input = new HashMap<>();
        input.put(100L, null);

        service.save(SESSION_ID, input);

        verify(attendanceRepository).delete(existing);
        verify(attendanceRepository, never()).save(any());
    }

    // ---- 已取消报名但有出勤记录的学员（#17 review：取消报名后出勤看不到 → 课次删不掉）----

    /** 学员有该课次的出勤记录，但已经取消了报名 */
    private Attendance cancelledWithRecord(Long id, String name, AttendanceStatus status) {
        Trainee t = trainee(id, name);
        when(traineeService.get(id)).thenReturn(t);
        when(enrollmentRepository.existsByTraineeIdAndCourseId(id, COURSE_ID)).thenReturn(false);
        Attendance a = new Attendance(session, t, status);
        when(attendanceRepository.findBySessionIdAndTraineeId(SESSION_ID, id)).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void 已取消报名者一览_只返回有出勤记录但已不是报名者的学员() {
        when(sessionService.get(SESSION_ID)).thenReturn(session);
        Attendance enrolledOne = new Attendance(session, trainee(100L, "山田 太郎"), AttendanceStatus.PRESENT);
        Attendance cancelledOne = new Attendance(session, trainee(300L, "王 磊"), AttendanceStatus.LATE);
        when(attendanceRepository.findBySessionId(SESSION_ID)).thenReturn(List.of(enrolledOne, cancelledOne));
        when(enrollmentRepository.existsByTraineeIdAndCourseId(100L, COURSE_ID)).thenReturn(true);
        when(enrollmentRepository.existsByTraineeIdAndCourseId(300L, COURSE_ID)).thenReturn(false);

        assertThat(service.cancelledWithAttendance(SESSION_ID)).containsExactly(cancelledOne);
    }

    @Test
    void 已取消报名者_改为未记录则删除记录() {
        when(sessionService.get(SESSION_ID)).thenReturn(session);
        Attendance existing = cancelledWithRecord(300L, "王 磊", AttendanceStatus.PRESENT);
        Map<Long, AttendanceStatus> input = new HashMap<>();
        input.put(300L, null);

        service.save(SESSION_ID, input);

        verify(attendanceRepository).delete(existing);
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void 已取消报名者_状态不变再保存则什么都不做() {
        // 表单会把当前状态原样提交回来，不能因此报错
        when(sessionService.get(SESSION_ID)).thenReturn(session);
        Attendance existing = cancelledWithRecord(300L, "王 磊", AttendanceStatus.PRESENT);

        service.save(SESSION_ID, Map.of(300L, AttendanceStatus.PRESENT));

        assertThat(existing.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        verify(attendanceRepository, never()).save(any());
        verify(attendanceRepository, never()).delete(any());
    }

    @Test
    void 已取消报名者_改为其他状态被拒绝且整批不保存() {
        when(sessionService.get(SESSION_ID)).thenReturn(session);
        enrolled(100L, "山田 太郎");
        Attendance existing = cancelledWithRecord(300L, "王 磊", AttendanceStatus.PRESENT);
        Map<Long, AttendanceStatus> input = new LinkedHashMap<>();
        input.put(100L, AttendanceStatus.PRESENT);
        input.put(300L, AttendanceStatus.ABSENT);

        assertThatThrownBy(() -> service.save(SESSION_ID, input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("王 磊")
                .hasMessageContaining("已取消报名")
                .hasMessageContaining("未记录");
        assertThat(existing.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        verify(attendanceRepository, never()).save(any());
        verify(attendanceRepository, never()).delete(any());
    }

    @Test
    void 空Map或null则什么都不做() {
        when(sessionService.get(SESSION_ID)).thenReturn(session);

        service.save(SESSION_ID, Map.of());
        service.save(SESSION_ID, null);

        verifyNoInteractions(attendanceRepository, traineeService, enrollmentRepository);
    }

    @Test
    void 课次不存在报NotFound() {
        when(sessionService.get(99L)).thenThrow(new NotFoundException("课次", 99L));

        assertThatThrownBy(() -> service.save(99L, Map.of(100L, AttendanceStatus.PRESENT)))
                .isInstanceOf(NotFoundException.class);
        verify(attendanceRepository, never()).save(any());
        verify(attendanceRepository, never()).findBySessionIdAndTraineeId(anyLong(), anyLong());
    }
}
