package com.example.training.service;

import com.example.training.domain.Course;
import com.example.training.domain.CourseCategory;
import com.example.training.domain.CourseSession;
import com.example.training.domain.Instructor;
import com.example.training.domain.SessionMode;
import com.example.training.repository.AttendanceRepository;
import com.example.training.repository.CourseSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;

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
    void 删除课次时_连同出勤一起删() {
        CourseSession s = session(LocalTime.of(19, 30), LocalTime.of(21, 0));
        when(sessionRepository.findById(5L)).thenReturn(java.util.Optional.of(s));

        service.delete(5L);

        var order = inOrder(attendanceRepository, sessionRepository);
        order.verify(attendanceRepository).deleteBySessionId(5L);
        order.verify(sessionRepository).delete(s);
    }

    @Test
    void 删除不存在的课次_抛出NotFound_且不删出勤() {
        when(sessionRepository.findById(5L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.delete(5L)).isInstanceOf(NotFoundException.class);
        verify(attendanceRepository, never()).deleteBySessionId(any());
    }

    @Test
    void 本周课次数_按周一到周日统计() {
        // 2026-11-12 是周四 → 本周 = 11/9（周一）〜11/15（周日）
        service.countThisWeek(LocalDate.of(2026, 11, 12));
        verify(sessionRepository).countByDateBetween(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15));
    }
}
