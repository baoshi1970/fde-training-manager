package com.example.training.service;

import com.example.training.service.ProgressCalculator.CourseScore;
import com.example.training.service.ProgressCalculator.Progress;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 学员进度看板的计算规则（issue #9） */
class ProgressCalculatorTest {

    private static final List<CourseScore> 没有评分 = List.of();

    private static CourseScore 已完成(String total, String grade) {
        return new CourseScore(new BigDecimal(total), grade);
    }

    private static CourseScore 未完成() {
        return new CourseScore(null, null);
    }

    // ---- 出勤率 ----

    @Test
    void 出勤_没有记录则出勤率为空且不要关注() {
        Progress p = ProgressCalculator.calculate(0, 0, 0, 没有评分);

        assertThat(p.attendanceRate()).isNull();
        assertThat(p.attention()).isFalse();
    }

    @Test
    void 出勤_正好80百分比不要关注() {
        Progress p = ProgressCalculator.calculate(8, 0, 10, 没有评分);

        assertThat(p.attendanceRate()).isEqualTo(80);
        assertThat(p.attention()).isFalse();
    }

    @Test
    void 出勤_出席加迟到合算正好80百分比不要关注() {
        Progress p = ProgressCalculator.calculate(6, 2, 10, 没有评分);

        assertThat(p.attendanceRate()).isEqualTo(80);
        assertThat(p.attention()).isFalse();
    }

    @Test
    void 出勤_低于80百分比要关注() {
        Progress p = ProgressCalculator.calculate(7, 0, 10, 没有评分);

        assertThat(p.attendanceRate()).isEqualTo(70);
        assertThat(p.attention()).isTrue();
    }

    @Test
    void 出勤_79点5百分比向下取整显示79且要关注() {
        Progress p = ProgressCalculator.calculate(159, 0, 200, 没有评分);

        assertThat(p.attendanceRate()).isEqualTo(79);
        assertThat(p.attention()).isTrue();
    }

    @Test
    void 出勤_全出席为100百分比() {
        Progress p = ProgressCalculator.calculate(10, 0, 10, 没有评分);

        assertThat(p.attendanceRate()).isEqualTo(100);
        assertThat(p.attention()).isFalse();
    }

    @Test
    void 出勤_全缺席为0百分比且要关注() {
        Progress p = ProgressCalculator.calculate(0, 0, 5, 没有评分);

        assertThat(p.attendanceRate()).isZero();
        assertThat(p.attention()).isTrue();
    }

    @Test
    void 出勤_迟到次数原样返回() {
        Progress p = ProgressCalculator.calculate(5, 3, 10, 没有评分);

        assertThat(p.lateCount()).isEqualTo(3);
        assertThat(p.attendanceRate()).isEqualTo(80);
    }

    @Test
    void 出勤_负数则报错() {
        assertThatThrownBy(() -> ProgressCalculator.calculate(-1, 0, 10, 没有评分))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 出勤_出席加迟到多于记录数则报错() {
        assertThatThrownBy(() -> ProgressCalculator.calculate(8, 3, 10, 没有评分))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- 平均分・评级 ----

    @Test
    void 评分_没有评分则平均分为空() {
        Progress p = ProgressCalculator.calculate(10, 0, 10, 没有评分);

        assertThat(p.averageScore()).isNull();
    }

    @Test
    void 评分_只有未完成评分的课程则平均分为空且不要关注() {
        Progress p = ProgressCalculator.calculate(10, 0, 10, List.of(未完成(), 未完成()));

        assertThat(p.averageScore()).isNull();
        assertThat(p.attention()).isFalse();
    }

    @Test
    void 评分_1门课程则为该课程总分() {
        Progress p = ProgressCalculator.calculate(10, 0, 10, List.of(已完成("79.0", "B")));

        assertThat(p.averageScore()).isEqualByComparingTo("79.0");
    }

    @Test
    void 评分_未完成的课程不算进平均() {
        Progress p = ProgressCalculator.calculate(10, 0, 10,
                List.of(已完成("79.0", "B"), 未完成(), 已完成("85.0", "A")));

        assertThat(p.averageScore()).isEqualByComparingTo("82.0");
    }

    @Test
    void 评分_平均分四舍五入到小数1位() {
        Progress p = ProgressCalculator.calculate(10, 0, 10,
                List.of(已完成("79.0", "B"), 已完成("80.1", "B")));

        // (79.0 + 80.1) ÷ 2 = 79.55 → 79.6
        assertThat(p.averageScore()).isEqualTo(new BigDecimal("79.6"));
    }

    // ---- 要关注 ----

    @Test
    void 关注_只有D评级也要关注() {
        Progress p = ProgressCalculator.calculate(10, 0, 10, List.of(已完成("50.0", "D")));

        assertThat(p.attendanceRate()).isEqualTo(100);
        assertThat(p.attention()).isTrue();
    }

    @Test
    void 关注_没有出勤记录但有D评级要关注() {
        Progress p = ProgressCalculator.calculate(0, 0, 0, List.of(已完成("50.0", "D")));

        assertThat(p.attendanceRate()).isNull();
        assertThat(p.attention()).isTrue();
    }

    @Test
    void 关注_出勤率低且有D评级要关注() {
        Progress p = ProgressCalculator.calculate(5, 0, 10, List.of(已完成("50.0", "D")));

        assertThat(p.attention()).isTrue();
    }

    @Test
    void 关注_出勤80百分比且没有D评级不要关注() {
        Progress p = ProgressCalculator.calculate(8, 0, 10,
                List.of(已完成("60.0", "C"), 已完成("85.0", "A")));

        assertThat(p.attention()).isFalse();
    }
}
