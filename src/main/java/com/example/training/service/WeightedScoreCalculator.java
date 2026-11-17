package com.example.training.service;

import com.example.training.domain.Grade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 加权评分的计算（F8）。不依赖 DB 的纯计算。
 * <ul>
 *   <li>总分 ＝ Σ(权重 × 得分) ÷ 5，满分 100，四舍五入到小数 1 位</li>
 *   <li>评级：≥ 85 → A，≥ 70 → B，≥ 60 → C，其余 → D</li>
 * </ul>
 * 小数的处理：加权和用 int 计算（没有误差）；只在「÷ 5」这一步换成 BigDecimal，
 * 明确指定 scale 和 HALF_UP。不用 double（例：299 * 0.2 = 59.800000000000004）。
 */
public final class WeightedScoreCalculator {

    private static final BigDecimal DIVISOR = BigDecimal.valueOf(5);
    private static final BigDecimal A_LINE = new BigDecimal("85");
    private static final BigDecimal B_LINE = new BigDecimal("70");
    private static final BigDecimal C_LINE = new BigDecimal("60");

    /** 一个维度：权重 ＋ 得分（未打分为 null） */
    public record Item(int weight, Integer score) {
    }

    private WeightedScoreCalculator() {
    }

    public static ScoreResult calculate(List<Item> items) {
        // 没有维度，或有维度没打分 → 未完成（不显示半截的数字）
        if (items.isEmpty() || items.stream().anyMatch(i -> i.score() == null)) {
            return ScoreResult.incomplete();
        }
        int weightedSum = items.stream().mapToInt(i -> i.weight() * i.score()).sum();
        // 权重・得分都是整数时结果只会是 .0/.2/.4/.6/.8，实际不会发生舍入；
        // 仍按规格写明「四舍五入到 1 位」，也避免除不尽时 BigDecimal 抛异常
        BigDecimal total = BigDecimal.valueOf(weightedSum).divide(DIVISOR, 1, RoundingMode.HALF_UP);
        return new ScoreResult(true, total, gradeOf(total));
    }

    private static Grade gradeOf(BigDecimal total) {
        if (total.compareTo(A_LINE) >= 0) {
            return Grade.A;
        }
        if (total.compareTo(B_LINE) >= 0) {
            return Grade.B;
        }
        if (total.compareTo(C_LINE) >= 0) {
            return Grade.C;
        }
        return Grade.D;
    }
}
