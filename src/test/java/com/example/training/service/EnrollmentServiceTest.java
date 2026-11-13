package com.example.training.service;

import com.example.training.domain.Course;
import com.example.training.domain.CourseCategory;
import com.example.training.domain.Enrollment;
import com.example.training.domain.Trainee;
import com.example.training.domain.TraineeStatus;
import com.example.training.repository.EnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    EnrollmentRepository enrollmentRepository;
    @Mock
    TraineeService traineeService;
    @Mock
    CourseService courseService;
    @InjectMocks
    EnrollmentService service;

    private final Course course = new Course("S2", "AI 辅助编码", CourseCategory.COMMON, true, 16);
    private final LocalDate today = LocalDate.of(2026, 11, 10);

    @Test
    void 在籍学员可以报名() {
        Trainee t = new Trainee("山田 太郎", "a@example.com", "A 社", TraineeStatus.ACTIVE, today);
        when(traineeService.get(1L)).thenReturn(t);
        when(courseService.get(2L)).thenReturn(course);
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Enrollment e = service.enroll(1L, 2L, today);

        assertThat(e.getTrainee()).isSameAs(t);
        assertThat(e.getCourse()).isSameAs(course);
        assertThat(e.getEnrolledOn()).isEqualTo(today);
    }

    @Test
    void 已报名的学员不能重复报名同一课程() {
        Trainee t = new Trainee("山田 太郎", "a@example.com", "A 社", TraineeStatus.ACTIVE, today);
        when(traineeService.get(1L)).thenReturn(t);
        when(courseService.get(2L)).thenReturn(course);
        when(enrollmentRepository.existsByTraineeIdAndCourseId(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.enroll(1L, 2L, today))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("山田 太郎")
                .hasMessageContaining("不能重复报名");
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void 已报名其他课程的学员可以报名本课程() {
        Trainee t = new Trainee("山田 太郎", "a@example.com", "A 社", TraineeStatus.ACTIVE, today);
        when(traineeService.get(1L)).thenReturn(t);
        when(courseService.get(2L)).thenReturn(course);
        // 只有（学员 1 × 课程 3）报过名，（学员 1 × 课程 2）没有
        lenient().when(enrollmentRepository.existsByTraineeIdAndCourseId(1L, 3L)).thenReturn(true);
        when(enrollmentRepository.existsByTraineeIdAndCourseId(1L, 2L)).thenReturn(false);
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Enrollment e = service.enroll(1L, 2L, today);

        assertThat(e.getCourse()).isSameAs(course);
        verify(enrollmentRepository).save(any());
    }

    @Test
    void 休学学员不能报名() {
        Trainee t = new Trainee("鈴木 一郎", "b@example.com", "C 社", TraineeStatus.ON_LEAVE, today);
        when(traineeService.get(1L)).thenReturn(t);
        when(courseService.get(2L)).thenReturn(course);

        assertThatThrownBy(() -> service.enroll(1L, 2L, today))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("在籍");
        verify(enrollmentRepository, never()).save(any());
    }
}
