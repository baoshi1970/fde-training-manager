package com.example.training.service;

import com.example.training.domain.Enrollment;

import java.util.Map;

/**
 * 评分一览的 1 行（1 个报名者）。
 *
 * @param enrollment 报名
 * @param scores     维度 id → 得分（没打分的维度不在 Map 里）
 * @param result     总分・评级
 */
public record EvaluationRow(Enrollment enrollment, Map<Long, Integer> scores, ScoreResult result) {
}
