package com.example.training.service;

import com.example.training.domain.AttendanceStatus;
import com.example.training.domain.Enrollment;
import com.example.training.domain.EvaluationCriterion;
import com.example.training.domain.Trainee;
import com.example.training.domain.TraineeStatus;
import com.example.training.repository.AttendanceRepository;
import com.example.training.repository.AttendanceRepository.StatusCount;
import com.example.training.repository.CriterionScoreRepository;
import com.example.training.repository.CriterionScoreRepository.ScoreValue;
import com.example.training.repository.EnrollmentRepository;
import com.example.training.repository.EvaluationCriterionRepository;
import com.example.training.repository.TraineeRepository;
import com.example.training.service.ProgressCalculator.Progress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * F9 学员进度看板。
 * <p>
 * 查询次数固定（不随学员人数增加，#9 计划第 3 节）：先用 5 次查询把数据全部取出来，再在内存里按学员组装。
 * 计算交给 {@link WeightedScoreCalculator}（F8 的总分・评级）和 {@link ProgressCalculator}（出勤率・平均分・要关注）。
 */
@Service
@Transactional(readOnly = true)
public class ProgressService {

    private final TraineeRepository traineeRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EvaluationCriterionRepository criterionRepository;
    private final CriterionScoreRepository scoreRepository;
    private final AttendanceRepository attendanceRepository;
    private final Clock clock;

    public ProgressService(TraineeRepository traineeRepository, EnrollmentRepository enrollmentRepository,
                           EvaluationCriterionRepository criterionRepository,
                           CriterionScoreRepository scoreRepository,
                           AttendanceRepository attendanceRepository, Clock clock) {
        this.traineeRepository = traineeRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.criterionRepository = criterionRepository;
        this.scoreRepository = scoreRepository;
        this.attendanceRepository = attendanceRepository;
        this.clock = clock;
    }

    /**
     * @param attentionOnly true 时只返回「要关注」的学员
     * @return 在籍学员一人一行，按学员 id 顺序
     */
    public List<ProgressRow> board(boolean attentionOnly) {
        LocalDate today = LocalDate.now(clock);

        // ① 〜 ⑤：5 次查询
        List<Trainee> trainees = traineeRepository.findByStatusOrderByIdAsc(TraineeStatus.ACTIVE);
        Map<Long, List<Enrollment>> enrollmentsByTrainee = enrollmentRepository
                .findByTraineeStatus(TraineeStatus.ACTIVE).stream()
                .collect(Collectors.groupingBy(e -> e.getTrainee().getId()));
        Map<Long, List<EvaluationCriterion>> criteriaByCourse = criterionRepository.findAll().stream()
                .collect(Collectors.groupingBy(c -> c.getCourse().getId()));
        Map<Long, Map<Long, Integer>> scoresByEnrollment = scoresByEnrollment(scoreRepository.findAllScoreValues());
        Map<Long, Map<AttendanceStatus, Integer>> attendanceByTrainee =
                attendanceByTrainee(attendanceRepository.countByTraineeAndStatusUpTo(today));

        return trainees.stream()
                .map(t -> row(t,
                        enrollmentsByTrainee.getOrDefault(t.getId(), List.of()),
                        criteriaByCourse, scoresByEnrollment,
                        attendanceByTrainee.getOrDefault(t.getId(), Map.of())))
                .filter(r -> !attentionOnly || r.progress().attention())
                .toList();
    }

    private ProgressRow row(Trainee trainee, List<Enrollment> enrollments,
                            Map<Long, List<EvaluationCriterion>> criteriaByCourse,
                            Map<Long, Map<Long, Integer>> scoresByEnrollment,
                            Map<AttendanceStatus, Integer> attendance) {
        // 按不同课程数数（Spike §2-6：#12 修好之前可能有重复报名）
        int courseCount = (int) enrollments.stream().map(e -> e.getCourse().getId()).distinct().count();

        List<ScoreResult> scores = enrollments.stream()
                .map(e -> scoreOf(e, criteriaByCourse, scoresByEnrollment))
                .toList();

        int present = attendance.getOrDefault(AttendanceStatus.PRESENT, 0);
        int late = attendance.getOrDefault(AttendanceStatus.LATE, 0);
        int absent = attendance.getOrDefault(AttendanceStatus.ABSENT, 0);
        Progress progress = ProgressCalculator.calculate(present, late, present + late + absent, scores);

        return new ProgressRow(trainee, courseCount, progress);
    }

    /** 一个报名的总分・评级：和 F8 的评分一览（EvaluationService.results）同样用 WeightedScoreCalculator 计算 */
    private static ScoreResult scoreOf(Enrollment enrollment,
                                       Map<Long, List<EvaluationCriterion>> criteriaByCourse,
                                       Map<Long, Map<Long, Integer>> scoresByEnrollment) {
        List<EvaluationCriterion> criteria = criteriaByCourse.getOrDefault(enrollment.getCourse().getId(), List.of());
        Map<Long, Integer> scores = scoresByEnrollment.getOrDefault(enrollment.getId(), Map.of());
        List<WeightedScoreCalculator.Item> items = criteria.stream()
                .map(c -> new WeightedScoreCalculator.Item(c.getWeight(), scores.get(c.getId())))
                .toList();
        // 没有维度、或有维度没打分 → 未完成（ProgressCalculator 不算进平均）
        return WeightedScoreCalculator.calculate(items);
    }

    /** 报名 id → (维度 id → 得分) */
    private static Map<Long, Map<Long, Integer>> scoresByEnrollment(List<ScoreValue> values) {
        Map<Long, Map<Long, Integer>> map = new HashMap<>();
        for (ScoreValue v : values) {
            map.computeIfAbsent(v.getEnrollmentId(), k -> new HashMap<>()).put(v.getCriterionId(), v.getScore());
        }
        return map;
    }

    /** 学员 id → (状态 → 件数) */
    private static Map<Long, Map<AttendanceStatus, Integer>> attendanceByTrainee(List<StatusCount> counts) {
        Map<Long, Map<AttendanceStatus, Integer>> map = new HashMap<>();
        for (StatusCount c : counts) {
            map.computeIfAbsent(c.getTraineeId(), k -> new EnumMap<>(AttendanceStatus.class))
                    .put(c.getStatus(), Math.toIntExact(c.getTotal()));
        }
        return map;
    }
}
