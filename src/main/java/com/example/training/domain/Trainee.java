package com.example.training.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 学员 */
@Entity
@Table(name = "trainees")
public class Trainee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "请输入姓名")
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String name;

    @NotBlank(message = "请输入邮箱")
    @Email(message = "邮箱格式不正确")
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /** 所属公司・部门 */
    @Size(max = 100)
    @Column(length = 100)
    private String affiliation;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TraineeStatus status = TraineeStatus.ACTIVE;

    @NotNull(message = "请输入入学日期")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Column(nullable = false)
    private LocalDate joinedOn;

    public Trainee() {
    }

    public Trainee(String name, String email, String affiliation, TraineeStatus status, LocalDate joinedOn) {
        this.name = name;
        this.email = email;
        this.affiliation = affiliation;
        this.status = status;
        this.joinedOn = joinedOn;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAffiliation() { return affiliation; }
    public void setAffiliation(String affiliation) { this.affiliation = affiliation; }
    public TraineeStatus getStatus() { return status; }
    public void setStatus(TraineeStatus status) { this.status = status; }
    public LocalDate getJoinedOn() { return joinedOn; }
    public void setJoinedOn(LocalDate joinedOn) { this.joinedOn = joinedOn; }
}
