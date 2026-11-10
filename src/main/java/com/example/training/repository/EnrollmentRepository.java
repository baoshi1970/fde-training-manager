package com.example.training.repository;

import com.example.training.domain.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findByCourseIdOrderByIdAsc(Long courseId);

    List<Enrollment> findByTraineeIdOrderByIdAsc(Long traineeId);

    boolean existsByTraineeIdAndCourseId(Long traineeId, Long courseId);

    long countByCourseId(Long courseId);

    boolean existsByCourseId(Long courseId);

    boolean existsByTraineeId(Long traineeId);
}
