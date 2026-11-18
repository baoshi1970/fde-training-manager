package com.example.training.service;

import com.example.training.domain.Course;
import com.example.training.domain.CourseCategory;
import com.example.training.repository.CourseRepository;
import com.example.training.repository.CourseSessionRepository;
import com.example.training.repository.EnrollmentRepository;
import com.example.training.repository.EvaluationCriterionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
    @Mock
    EvaluationCriterionRepository criterionRepository;
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

    // ---------- S24 评价维度（F8）：没有得分的维度跟着课程一起删除（PR #20 讲师评审，方案②） ----------

    @Test
    void S24_1_删除_评价维度先于课程删除() {
        Course c = course();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c));
        when(sessionRepository.existsByCourseId(1L)).thenReturn(false);
        when(enrollmentRepository.existsByCourseId(1L)).thenReturn(false);

        service.delete(1L);

        // 评价维度有外键指向课程，所以必须先删维度
        InOrder order = inOrder(criterionRepository, courseRepository);
        order.verify(criterionRepository).deleteByCourseId(1L);
        order.verify(courseRepository).delete(c);
    }

    @Test
    void S24_2_删除_有课次时评价维度也不删() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course()));
        when(sessionRepository.existsByCourseId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BusinessException.class);
        verify(criterionRepository, never()).deleteByCourseId(anyLong());
    }

    @Test
    void S24_3_删除_有报名时评价维度也不删() {
        // 得分挂在报名上：有报名 ＝ 可能有得分，这时维度一个都不能删
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course()));
        when(sessionRepository.existsByCourseId(1L)).thenReturn(false);
        when(enrollmentRepository.existsByCourseId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BusinessException.class);
        verify(criterionRepository, never()).deleteByCourseId(anyLong());
    }

    @Test
    void 删除_不存在的ID报NotFound() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(NotFoundException.class);
        verify(sessionRepository, never()).existsByCourseId(anyLong());
        verify(courseRepository, never()).delete(any());
    }
}
