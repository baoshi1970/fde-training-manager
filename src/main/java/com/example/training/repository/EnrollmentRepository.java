package com.example.training.repository;

import com.example.training.domain.Enrollment;
import com.example.training.domain.TraineeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findByCourseIdOrderByIdAsc(Long courseId);

    List<Enrollment> findByTraineeIdOrderByIdAsc(Long traineeId);

    boolean existsByTraineeIdAndCourseId(Long traineeId, Long courseId);

    long countByCourseId(Long courseId);

    boolean existsByCourseId(Long courseId);

    boolean existsByTraineeId(Long traineeId);

    /** F9 学员进度看板：只取指定状态（在籍）学员的报名 */
    List<Enrollment> findByTraineeStatus(TraineeStatus status);
}
