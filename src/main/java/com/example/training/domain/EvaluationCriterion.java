package com.example.training.domain;

import jakarta.persistence.*;

/** 评价维度（F8）：课程 × 名称 ＋ 权重。同一课程的权重合计为 100 */
@Entity
@Table(name = "evaluation_criteria")
public class EvaluationCriterion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 50)
    private String name;

    /** 权重（1〜100 的整数） */
    @Column(nullable = false)
    private int weight;

    /** 显示顺序（0 开始） */
    @Column(nullable = false)
    private int sortOrder;

    protected EvaluationCriterion() {
    }

    public EvaluationCriterion(Course course, String name, int weight, int sortOrder) {
        this.course = course;
        this.name = name;
        this.weight = weight;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public Course getCourse() { return course; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
