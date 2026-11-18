package com.example.training.domain;

import jakarta.persistence.*;

/** 维度得分（F8）：报名 × 评价维度 → 1〜5 分。挂在报名上，所以只有报名者能被评分 */
@Entity
@Table(name = "criterion_scores",
        uniqueConstraints = @UniqueConstraint(columnNames = {"enrollment_id", "criterion_id"}))
public class CriterionScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "criterion_id", nullable = false)
    private EvaluationCriterion criterion;

    /** 得分（1〜5 的整数） */
    @Column(nullable = false)
    private int score;

    protected CriterionScore() {
    }

    public CriterionScore(Enrollment enrollment, EvaluationCriterion criterion, int score) {
        this.enrollment = enrollment;
        this.criterion = criterion;
        this.score = score;
    }

    public Long getId() { return id; }
    public Enrollment getEnrollment() { return enrollment; }
    public EvaluationCriterion getCriterion() { return criterion; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
}
