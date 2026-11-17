package com.example.training.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 学员进度看板的计算（issue #9）。
 * <p>
 * 只收数字、不碰数据库：「课次日期 ≤ 今天」「按不同课程数报名」等条件在查询里处理，这里只做计算。
 * 不依赖 F6・F8 的实体，评级用字符串 "A"〜"D" 接收。
 */
public final class ProgressCalculator {

    private static final String GRADE_D = "D";

    private ProgressCalculator() {
    }

    /**
     * 一门课程的评分结果
     *
     * @param total 总分（小数 1 位）。未完成评分时为 null
     * @param grade 评级 "A"〜"D"。未完成评分时为 null
     */
    public record CourseScore(BigDecimal total, String grade) {

        boolean complete() {
            return total != null;
        }
    }

    /**
     * 一名学员的看板数据
     *
     * @param attendanceRate 出勤率（整数 %，向下取整）。没有出勤记录时为 null（画面显示「—」）
     * @param lateCount      迟到次数
     * @param averageScore   已完成评分的课程总分的平均（小数 1 位）。没有时为 null（画面显示「—」）
     * @param attention      要关注：出勤率 < 80% 或有 D 评级
     */
    public record Progress(Integer attendanceRate, int lateCount, BigDecimal averageScore, boolean attention) {
    }

    /**
     * @param present  出席数
     * @param late     迟到数
     * @param recorded 已登记出勤的记录数（出席＋迟到＋缺席）
     * @param scores   该学员各课程的评分结果（含未完成）
     */
    public static Progress calculate(int present, int late, int recorded, List<CourseScore> scores) {
        // 查询结果不可能出现这些值，出现就是程序错误，所以不用 BusinessException
        if (present < 0 || late < 0 || recorded < 0) {
            throw new IllegalArgumentException("出勤数不能是负数");
        }
        if (present + late > recorded) {
            throw new IllegalArgumentException("出席＋迟到不能多于出勤记录数");
        }
        Objects.requireNonNull(scores, "scores");

        Integer attendanceRate = attendanceRate(present, late, recorded);
        boolean attention = lowAttendance(present, late, recorded) || hasGradeD(scores);
        return new Progress(attendanceRate, late, averageScore(scores), attention);
    }

    /** 向下取整：四舍五入会让 79.5% 显示成「80%」却被标红，和判断矛盾 */
    private static Integer attendanceRate(int present, int late, int recorded) {
        if (recorded == 0) {
            return null;
        }
        return (present + late) * 100 / recorded;
    }

    /** 出勤率 < 80%。用整数比较避免小数误差，正好 80% 不算；没有记录不算 */
    private static boolean lowAttendance(int present, int late, int recorded) {
        return recorded > 0 && (present + late) * 5 < recorded * 4;
    }

    /** 只看已完成评分的课程。评级由 F8 决定，这里不重复判断分数线 */
    private static boolean hasGradeD(List<CourseScore> scores) {
        return scores.stream()
                .filter(CourseScore::complete)
                .anyMatch(s -> GRADE_D.equals(s.grade()));
    }

    /** 未完成的课程不算进分母。简单平均（不按学时加权），四舍五入到小数 1 位 */
    private static BigDecimal averageScore(List<CourseScore> scores) {
        List<BigDecimal> totals = scores.stream()
                .filter(CourseScore::complete)
                .map(CourseScore::total)
                .toList();
        if (totals.isEmpty()) {
            return null;
        }
        BigDecimal sum = totals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(totals.size()), 1, RoundingMode.HALF_UP);
    }
}
