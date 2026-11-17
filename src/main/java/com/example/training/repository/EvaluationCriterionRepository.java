package com.example.training.repository;

import com.example.training.domain.EvaluationCriterion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationCriterionRepository extends JpaRepository<EvaluationCriterion, Long> {

    List<EvaluationCriterion> findByCourseIdOrderBySortOrderAscIdAsc(Long courseId);

    /** 删除课程时，先删该课程的评价维度（PR #20 讲师评审） */
    void deleteByCourseId(Long courseId);
}
