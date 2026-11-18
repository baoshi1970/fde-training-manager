package com.example.training.web;

import com.example.training.domain.AttendanceStatus;

import java.util.HashMap;
import java.util.Map;

/** 出勤登记表单：学员 ID → 状态（选「未记录」时为 null） */
public class AttendanceForm {

    private Map<Long, AttendanceStatus> statuses = new HashMap<>();

    public Map<Long, AttendanceStatus> getStatuses() { return statuses; }
    public void setStatuses(Map<Long, AttendanceStatus> statuses) { this.statuses = statuses; }
}
