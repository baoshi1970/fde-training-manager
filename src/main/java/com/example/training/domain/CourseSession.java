package com.example.training.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/** 课次：某门课程的一次授课（日期・时段・讲师） */
@Entity
@Table(name = "course_sessions")
public class CourseSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "请选择课程")
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @NotNull(message = "请选择讲师")
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "instructor_id", nullable = false)
    private Instructor instructor;

    @NotNull(message = "请输入日期")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Column(name = "session_date", nullable = false)
    private LocalDate date;

    @NotNull(message = "请输入开始时间")
    @DateTimeFormat(pattern = "HH:mm")
    @Column(nullable = false)
    private LocalTime startTime;

    @NotNull(message = "请输入结束时间")
    @DateTimeFormat(pattern = "HH:mm")
    @Column(nullable = false)
    private LocalTime endTime;

    @NotNull(message = "请选择授课形式")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionMode mode;

    /** 会议链接或教室 */
    @Size(max = 200)
    @Column(length = 200)
    private String location;

    public CourseSession() {
    }

    public CourseSession(Course course, Instructor instructor, LocalDate date,
                         LocalTime startTime, LocalTime endTime, SessionMode mode, String location) {
        this.course = course;
        this.instructor = instructor;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.mode = mode;
        this.location = location;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public Instructor getInstructor() { return instructor; }
    public void setInstructor(Instructor instructor) { this.instructor = instructor; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public SessionMode getMode() { return mode; }
    public void setMode(SessionMode mode) { this.mode = mode; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
