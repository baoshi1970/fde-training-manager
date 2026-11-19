package com.example.training.service;

import com.example.training.domain.Trainee;
import com.example.training.service.ProgressCalculator.Progress;

/**
 * 学员进度看板的一行（F9）
 *
 * @param trainee     在籍学员
 * @param courseCount 报名课程数（按不同课程数）
 * @param progress    出勤率・迟到次数・平均分・要关注
 */
public record ProgressRow(Trainee trainee, int courseCount, Progress progress) {
}
