package com.example.training.repository;

import com.example.training.domain.CriterionScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CriterionScoreRepository extends JpaRepository<CriterionScore, Long> {

    /** 该课程是否已有任何得分（有则不能增删维度・改权重） */
    boolean existsByCriterionCourseId(Long courseId);

    List<CriterionScore> findByEnrollmentCourseId(Long courseId);

    List<CriterionScore> findByEnrollmentId(Long enrollmentId);

    Optional<CriterionScore> findByEnrollmentIdAndCriterionId(Long enrollmentId, Long criterionId);

    void deleteByEnrollmentId(Long enrollmentId);
}
