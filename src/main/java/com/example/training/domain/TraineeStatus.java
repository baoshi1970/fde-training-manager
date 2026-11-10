package com.example.training.domain;

/** 学员状态 */
public enum TraineeStatus {
    ACTIVE("在籍"),
    ON_LEAVE("休学"),
    GRADUATED("结业");

    private final String label;

    TraineeStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
}
