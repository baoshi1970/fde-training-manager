package com.example.training.service;

import com.example.training.domain.Course;
import com.example.training.domain.CourseCategory;
import com.example.training.repository.CourseRepository;
import com.example.training.repository.CourseSessionRepository;
import com.example.training.repository.EnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** 课程删除的业务规则：有课次或报名时不能删除（issue #5） */
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    CourseRepository courseRepository;
    @Mock
    CourseSessionRepository sessionRepository;
    @Mock
    EnrollmentRepository enrollmentRepository;
    @InjectMocks
    CourseService service;

    private Course course() {
        return new Course("S2", "AI 辅助编码", CourseCategory.COMMON, true, 16);
    }

    @Test
    void 删除_有课次则不能删() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course()));
        when(sessionRepository.existsByCourseId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("课次");
        verify(courseRepository, never()).delete(any());
    }

    @Test
    void 删除_有报名则不能删() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course()));
        when(sessionRepository.existsByCourseId(1L)).thenReturn(false);
        when(enrollmentRepository.existsByCourseId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报名");
        verify(courseRepository, never()).delete(any());
    }

    @Test
    void 删除_课次和报名都有时先提示课次() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course()));
        when(sessionRepository.existsByCourseId(1L)).thenReturn(true);
        // 先查课次就会抛出，报名的检查可能不会被调用，所以用 lenient
        lenient().when(enrollmentRepository.existsByCourseId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("课次");
        verify(courseRepository, never()).delete(any());
    }

    @Test
    void 删除_没有课次也没有报名则正常删除() {
        Course c = course();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c));
        when(sessionRepository.existsByCourseId(1L)).thenReturn(false);
        when(enrollmentRepository.existsByCourseId(1L)).thenReturn(false);

        service.delete(1L);

        verify(courseRepository).delete(c);
    }

    @Test
    void 删除_不存在的ID报NotFound() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(NotFoundException.class);
        verify(sessionRepository, never()).existsByCourseId(anyLong());
        verify(courseRepository, never()).delete(any());
    }
}
