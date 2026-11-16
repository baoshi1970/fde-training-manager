package com.example.training.repository;

import com.example.training.domain.EvaluationCriterion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationCriterionRepository extends JpaRepository<EvaluationCriterion, Long> {

    List<EvaluationCriterion> findByCourseIdOrderBySortOrderAscIdAsc(Long courseId);
}
