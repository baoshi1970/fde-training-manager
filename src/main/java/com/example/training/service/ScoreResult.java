package com.example.training.service;

import com.example.training.domain.Grade;

import java.math.BigDecimal;

/**
 * 加权评分的计算结果。
 *
 * @param complete 所有维度都打完分才为 true
 * @param total    总分（scale = 1，例：79.0）。未完成时为 null
 * @param grade    评级。未完成时为 null
 */
public record ScoreResult(boolean complete, BigDecimal total, Grade grade) {

    public static ScoreResult incomplete() {
        return new ScoreResult(false, null, null);
    }
}
