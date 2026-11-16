package com.example.training.domain;

/** 出勤状态（「未记录」不是状态：没有记录就是未记录） */
public enum AttendanceStatus {
    PRESENT("出席"),
    LATE("迟到"),
    ABSENT("缺席");

    private final String label;

    AttendanceStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
}
