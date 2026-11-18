package com.example.training.service;

import com.example.training.domain.Grade;
import com.example.training.service.WeightedScoreCalculator.Item;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** F8 加权评分：计算类的单元测试（Issue #8 测试式样 C1〜C13） */
class WeightedScoreCalculatorTest {

    private static Item item(int weight, Integer score) {
        return new Item(weight, score);
    }

    private static void assertResult(ScoreResult r, String total, Grade grade) {
        assertThat(r.complete()).isTrue();
        assertThat(r.total().toPlainString()).isEqualTo(total);
        assertThat(r.grade()).isEqualTo(grade);
    }

    private static void assertIncomplete(ScoreResult r) {
        assertThat(r.complete()).isFalse();
        assertThat(r.total()).isNull();
        assertThat(r.grade()).isNull();
    }

    @Test
    void C1_Issue例子算出79点0_B() {
        ScoreResult r = WeightedScoreCalculator.calculate(List.of(
                item(25, 4), item(15, 3), item(15, 5), item(20, 4), item(15, 3), item(10, 5)));
        assertResult(r, "79.0", Grade.B);
    }

    @Test
    void C2_边界_85点0是A() {
        assertResult(WeightedScoreCalculator.calculate(List.of(item(25, 5), item(75, 4))), "85.0", Grade.A);
    }

    @Test
    void C3_边界_84点8是B() {
        assertResult(WeightedScoreCalculator.calculate(List.of(item(24, 5), item(76, 4))), "84.8", Grade.B);
    }

    @Test
    void C4_边界_70点0是B() {
        assertResult(WeightedScoreCalculator.calculate(List.of(item(50, 3), item(50, 4))), "70.0", Grade.B);
    }

    @Test
    void C5_边界_69点8是C() {
        assertResult(WeightedScoreCalculator.calculate(List.of(item(49, 4), item(51, 3))), "69.8", Grade.C);
    }

    @Test
    void C6_边界_60点0是C() {
        assertResult(WeightedScoreCalculator.calculate(List.of(item(50, 3), item(50, 3))), "60.0", Grade.C);
    }

    @Test
    void C7_边界_59点8是D() {
        assertResult(WeightedScoreCalculator.calculate(List.of(item(1, 2), item(99, 3))), "59.8", Grade.D);
    }

    @Test
    void C8_全部5分是满分100点0() {
        assertResult(WeightedScoreCalculator.calculate(List.of(item(100, 5))), "100.0", Grade.A);
    }

    @Test
    void C9_全部1分是最低分20点0() {
        assertResult(WeightedScoreCalculator.calculate(List.of(item(100, 1))), "20.0", Grade.D);
    }

    @Test
    void C10_有1个维度没打分则未完成() {
        assertIncomplete(WeightedScoreCalculator.calculate(List.of(item(50, 3), item(50, null))));
    }

    @Test
    void C11_全部没打分则未完成() {
        assertIncomplete(WeightedScoreCalculator.calculate(List.of(item(50, null), item(50, null))));
    }

    @Test
    void C12_没有维度则未完成() {
        assertIncomplete(WeightedScoreCalculator.calculate(List.of()));
    }

    @Test
    void C13_总分的scale固定为1() {
        ScoreResult r = WeightedScoreCalculator.calculate(List.of(item(100, 4)));
        assertThat(r.total().scale()).isEqualTo(1);
        assertThat(r.total().toPlainString()).isEqualTo("80.0");
    }
}
