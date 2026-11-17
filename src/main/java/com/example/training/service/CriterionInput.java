package com.example.training.service;

/**
 * 评价维度设置画面的 1 行。
 *
 * @param id     已有维度的 id。新增的行为 null
 * @param name   维度名称
 * @param weight 权重
 */
public record CriterionInput(Long id, String name, Integer weight) {
}
