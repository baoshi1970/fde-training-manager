package com.example.training.repository;

import com.example.training.domain.CriterionScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CriterionScoreRepository extends JpaRepository<CriterionScore, Long> {

    /** 该课程是否已有任何得分（有则不能增删维度・改权重） */
    boolean existsByCriterionCourseId(Long courseId);

    List<CriterionScore> findByEnrollmentCourseId(Long courseId);

    List<CriterionScore> findByEnrollmentId(Long enrollmentId);

    Optional<CriterionScore> findByEnrollmentIdAndCriterionId(Long enrollmentId, Long criterionId);

    void deleteByEnrollmentId(Long enrollmentId);

    /**
     * F9 学员进度看板：全部维度得分，只取数字。
     * 用 @Query 的理由：取实体的话，CriterionScore 的报名・维度关联都是 EAGER，
     * 会连带查报名・学员・课程・维度，查询次数可能随数据量增加（#9 计划第 3 节）。
     */
    @Query("select s.enrollment.id as enrollmentId, s.criterion.id as criterionId, s.score as score"
            + " from CriterionScore s")
    List<ScoreValue> findAllScoreValues();

    /** 维度得分的一行：报名 id・维度 id・得分 */
    interface ScoreValue {
        Long getEnrollmentId();

        Long getCriterionId();

        int getScore();
    }
}
