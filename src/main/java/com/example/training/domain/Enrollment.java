package com.example.training.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

/** 报名：学员 × 课程 */
@Entity
@Table(name = "enrollments")
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "trainee_id", nullable = false)
    private Trainee trainee;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false)
    private LocalDate enrolledOn;

    protected Enrollment() {
    }

    public Enrollment(Trainee trainee, Course course, LocalDate enrolledOn) {
        this.trainee = trainee;
        this.course = course;
        this.enrolledOn = enrolledOn;
    }

    public Long getId() { return id; }
    public Trainee getTrainee() { return trainee; }
    public Course getCourse() { return course; }
    public LocalDate getEnrolledOn() { return enrolledOn; }
}
