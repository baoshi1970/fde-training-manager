package com.example.training.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 讲师 */
@Entity
@Table(name = "instructors")
public class Instructor {

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

    /** 擅长领域（例：Claude Code、RAG） */
    @Size(max = 100)
    @Column(length = 100)
    private String specialty;

    public Instructor() {
    }

    public Instructor(String name, String email, String specialty) {
        this.name = name;
        this.email = email;
        this.specialty = specialty;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
}
