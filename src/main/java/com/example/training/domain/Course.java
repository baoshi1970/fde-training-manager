package com.example.training.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

/** 课程（例：S2 AI 辅助编码） */
@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 课程编号（例：S2）。唯一 */
    @NotBlank(message = "请输入课程编号")
    @Pattern(regexp = "[A-Z][0-9]{1,2}", message = "课程编号格式：大写字母＋1〜2 位数字（例：S2、C10）")
    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @NotBlank(message = "请输入课程名称")
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @NotNull(message = "请选择类别")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseCategory category;

    /** 是否必修 */
    @Column(nullable = false)
    private boolean required;

    /** 标准学时（小时） */
    @NotNull(message = "请输入学时")
    @Min(value = 1, message = "学时至少 1 小时")
    @Max(value = 200, message = "学时最多 200 小时")
    @Column(nullable = false)
    private Integer hours;

    public Course() {
    }

    public Course(String code, String name, CourseCategory category, boolean required, Integer hours) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.required = required;
        this.hours = hours;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public CourseCategory getCategory() { return category; }
    public void setCategory(CourseCategory category) { this.category = category; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public Integer getHours() { return hours; }
    public void setHours(Integer hours) { this.hours = hours; }

    /** 画面显示用：「S2 AI 辅助编码」 */
    public String getDisplayName() { return code + " " + name; }
}
