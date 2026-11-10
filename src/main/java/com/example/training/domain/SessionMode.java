package com.example.training.domain;

/** 授课形式 */
public enum SessionMode {
    ONLINE("线上"),
    ONSITE("线下");

    private final String label;

    SessionMode(String label) { this.label = label; }

    public String getLabel() { return label; }
}
