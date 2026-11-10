package com.example.training.service;

import com.example.training.domain.Trainee;
import com.example.training.domain.TraineeStatus;
import com.example.training.repository.EnrollmentRepository;
import com.example.training.repository.TraineeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Service 单元测试的范本：Repository 用 Mock 代替，不连数据库 */
@ExtendWith(MockitoExtension.class)
class TraineeServiceTest {

    @Mock
    TraineeRepository traineeRepository;
    @Mock
    EnrollmentRepository enrollmentRepository;
    @InjectMocks
    TraineeService service;

    private Trainee trainee(String name, String email) {
        return new Trainee(name, email, "A 社", TraineeStatus.ACTIVE, LocalDate.of(2026, 10, 1));
    }

    @Test
    void 无条件检索返回全部() {
        List<Trainee> all = List.of(trainee("山田 太郎", "a@example.com"));
        when(traineeRepository.findAllByOrderByIdAsc()).thenReturn(all);

        assertThat(service.search("  ", null)).isEqualTo(all);
    }

    @Test
    void 只按状态检索() {
        service.search(null, TraineeStatus.ON_LEAVE);
        verify(traineeRepository).findByStatusOrderByIdAsc(TraineeStatus.ON_LEAVE);
    }

    @Test
    void 新增_邮箱重复则报业务错误() {
        when(traineeRepository.existsByEmail("a@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(trainee("山田 太郎", "a@example.com")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("a@example.com");
        verify(traineeRepository, never()).save(any());
    }

    @Test
    void 新增_正常保存() {
        Trainee t = trainee("山田 太郎", "a@example.com");
        when(traineeRepository.existsByEmail("a@example.com")).thenReturn(false);
        when(traineeRepository.save(t)).thenReturn(t);

        assertThat(service.create(t)).isSameAs(t);
    }

    @Test
    void 删除_有报名记录则不能删() {
        Trainee t = trainee("山田 太郎", "a@example.com");
        when(traineeRepository.findById(1L)).thenReturn(Optional.of(t));
        when(enrollmentRepository.existsByTraineeId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BusinessException.class);
        verify(traineeRepository, never()).delete(any());
    }

    @Test
    void 不存在的ID报NotFound() {
        when(traineeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(NotFoundException.class);
    }
}
