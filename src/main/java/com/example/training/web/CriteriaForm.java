package com.example.training.web;

import java.util.ArrayList;
import java.util.List;

/** 评价维度设置画面的表单（多行） */
public class CriteriaForm {

    private List<Row> rows = new ArrayList<>();

    public List<Row> getRows() { return rows; }
    public void setRows(List<Row> rows) { this.rows = rows; }

    /** 1 行 ＝ 1 个维度。名称和权重都为空的行视为「没有这一行」（已有维度 → 删除） */
    public static class Row {
        private Long id;
        private String name;
        private Integer weight;

        public Row() {
        }

        public Row(Long id, String name, Integer weight) {
            this.id = id;
            this.name = name;
            this.weight = weight;
        }

        public boolean isBlank() {
            return (name == null || name.isBlank()) && weight == null;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getWeight() { return weight; }
        public void setWeight(Integer weight) { this.weight = weight; }
    }
}
