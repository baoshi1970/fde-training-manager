package com.example.training.domain;

/** 课程类别 */
public enum CourseCategory {
    COMMON("公共基础"),
    PRODUCT("产品");

    private final String label;

    CourseCategory(String label) { this.label = label; }

    public String getLabel() { return label; }
}
