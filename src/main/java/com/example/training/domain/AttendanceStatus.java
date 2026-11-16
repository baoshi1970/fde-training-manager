package com.example.training.domain;

/** 出勤状态（没有记录 = 未记录，所以这里没有「未记录」） */
public enum AttendanceStatus {
    PRESENT("出席"),
    LATE("迟到"),
    ABSENT("缺席");

    private final String label;

    AttendanceStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
}
