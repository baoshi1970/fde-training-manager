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
import java.util.Optional;

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
}
