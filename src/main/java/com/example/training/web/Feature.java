package com.example.training.web;

import java.util.List;

/**
 * 功能一览（主菜单与主页「实装状况」共用）。
 * 学员实装了某个功能后：把 done 改为 true，并填上 path。
 */
public record Feature(String no, String name, String path, boolean done, boolean inMenu) {

    public static final List<Feature> ALL = List.of(
            new Feature("F1", "学员管理", "/trainees", true, true),
            new Feature("F2", "讲师管理", "/instructors", true, true),
            new Feature("F3", "课程管理", "/courses", true, true),
            new Feature("F4", "课次排期", "/sessions", true, true),
            new Feature("F5", "报名管理", "/enrollments", true, true),
            new Feature("F6", "出勤登记", null, false, true),
            new Feature("F7", "排课冲突检查", null, false, false),   // 课次排期画面内的校验，不单独占菜单
            new Feature("F8", "加权评分", null, false, true),
            new Feature("F9", "学员进度看板", null, false, true),
            new Feature("F10", "CSV 导入学员", null, false, true)
    );
}
