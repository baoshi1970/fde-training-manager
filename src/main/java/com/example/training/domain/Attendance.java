package com.example.training.domain;

import jakarta.persistence.*;

/**
 * 出勤：课次 × 学员 → 状态。
 * 同一课次・同一学员只有 1 条（Service 先查后更新，数据库再用唯一约束兜底）
 */
@Entity
@Table(name = "attendances",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "trainee_id"}))
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "session_id", nullable = false)
    private CourseSession session;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "trainee_id", nullable = false)
    private Trainee trainee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    protected Attendance() {
    }

    public Attendance(CourseSession session, Trainee trainee, AttendanceStatus status) {
        this.session = session;
        this.trainee = trainee;
        this.status = status;
    }

    public Long getId() { return id; }
    public CourseSession getSession() { return session; }
    public Trainee getTrainee() { return trainee; }
    public AttendanceStatus getStatus() { return status; }
    public void setStatus(AttendanceStatus status) { this.status = status; }
}
