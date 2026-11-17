package com.example.training.service;

import com.example.training.domain.Course;
import com.example.training.domain.CriterionScore;
import com.example.training.domain.Enrollment;
import com.example.training.domain.EvaluationCriterion;
import com.example.training.repository.CriterionScoreRepository;
import com.example.training.repository.EnrollmentRepository;
import com.example.training.repository.EvaluationCriterionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** F8 加权评分：评价维度的设置、打分、结果一览 */
@Service
@Transactional(readOnly = true)
public class EvaluationService {

    private static final int TOTAL_WEIGHT = 100;
    private static final int MIN_SCORE = 1;
    private static final int MAX_SCORE = 5;
    /** 维度名称的最大字数（EvaluationCriterion.name 的 length = 50） */
    private static final int MAX_NAME_LENGTH = 50;

    private final EvaluationCriterionRepository criterionRepository;
    private final CriterionScoreRepository scoreRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseService courseService;

    public EvaluationService(EvaluationCriterionRepository criterionRepository,
                             CriterionScoreRepository scoreRepository,
                             EnrollmentRepository enrollmentRepository,
                             CourseService courseService) {
        this.criterionRepository = criterionRepository;
        this.scoreRepository = scoreRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseService = courseService;
    }

    // ---------- 评价维度 ----------

    public List<EvaluationCriterion> findCriteria(Long courseId) {
        return criterionRepository.findByCourseIdOrderBySortOrderAscIdAsc(courseId);
    }

    /** 该课程是否已有得分（有则只能修改维度名称） */
    public boolean hasScores(Long courseId) {
        return scoreRepository.existsByCriterionCourseId(courseId);
    }

    /**
     * 保存课程的评价维度（整张表一起保存）。
     * <ul>
     *   <li>至少 1 个维度；名称不能为空、不超过 50 个字符、不能重复；权重是 1 以上的整数，合计 100</li>
     *   <li>已有得分时：不能增加・删除维度，不能修改权重，只能修改名称</li>
     *   <li>画面上去掉的已有维度会被删除（只在没有得分时可能发生）</li>
     * </ul>
     */
    @Transactional
    public void saveCriteria(Long courseId, List<CriterionInput> inputs) {
        Course course = courseService.get(courseId);
        if (inputs.isEmpty()) {
            throw new BusinessException("至少需要设置 1 个评价维度");
        }
        for (CriterionInput in : inputs) {
            if (in.name() == null || in.name().isBlank()) {
                throw new BusinessException("维度名称不能为空");
            }
            // 按 char 数（String.length()）计算：H2 按 char 数、Postgres 按字符数计算，
            // 按 char 数检查两种 DB 都不会超长（扩展汉字算 2 个，比 Postgres 严一些；#23）
            int length = nameOf(in).length();
            if (length > MAX_NAME_LENGTH) {
                throw new BusinessException("维度名称不能超过 " + MAX_NAME_LENGTH + " 个字符（当前：" + length
                        + " 字，扩展汉字按 2 个字符计算）");
            }
            if (in.weight() == null || in.weight() < 1) {
                throw new BusinessException("权重必须是 1 以上的整数（维度：" + nameOf(in) + "）");
            }
        }
        Set<String> names = new HashSet<>();
        for (CriterionInput in : inputs) {
            if (!names.add(nameOf(in))) {
                throw new BusinessException("维度名称重复：" + nameOf(in) + "。同一课程内的维度名称不能相同");
            }
        }

        Map<Long, EvaluationCriterion> existing = findCriteria(courseId).stream()
                .collect(Collectors.toMap(EvaluationCriterion::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Set<Long> inputIds = new HashSet<>();
        for (CriterionInput in : inputs) {
            if (in.id() == null) {
                continue;                          // 新增行（可以有多行）
            }
            if (!existing.containsKey(in.id())) {
                throw new NotFoundException("评价维度", in.id());
            }
            // 同一维度出现 2 次时，后一行会覆盖前一行，合计 100 的检查就失去意义（#22）
            if (!inputIds.add(in.id())) {
                throw new BusinessException("评价维度重复提交（id：" + in.id() + "）。请重新打开画面后再保存");
            }
        }

        if (hasScores(courseId) && changesStructureOrWeight(existing, inputs)) {
            throw new BusinessException("该课程已有得分，不能增加・删除维度或修改权重（只能修改维度名称）。");
        }

        int sum = inputs.stream().mapToInt(CriterionInput::weight).sum();
        if (sum != TOTAL_WEIGHT) {
            throw new BusinessException("权重合计必须为 100（当前：" + sum + "）");
        }

        Set<Long> keptIds = new HashSet<>();
        for (int i = 0; i < inputs.size(); i++) {
            CriterionInput in = inputs.get(i);
            String name = nameOf(in);
            if (in.id() == null) {
                criterionRepository.save(new EvaluationCriterion(course, name, in.weight(), i));
            } else {
                EvaluationCriterion c = existing.get(in.id());
                c.setName(name);
                c.setWeight(in.weight());
                c.setSortOrder(i);
                keptIds.add(in.id());
            }
        }
        List<EvaluationCriterion> removed = existing.values().stream()
                .filter(c -> !keptIds.contains(c.getId()))
                .toList();
        if (!removed.isEmpty()) {
            criterionRepository.deleteAll(removed);
        }
    }

    /**
     * 检查・重复判定・保存都用这个值。
     * strip() 也会去掉全角空格（U+3000）；trim() 只去掉半角空白，「三段法　」会被当成另一个名称（#23）
     */
    private static String nameOf(CriterionInput in) {
        return in.name().strip();
    }

    /** 有没有增删维度或修改权重（只改名称・顺序则为 false） */
    private boolean changesStructureOrWeight(Map<Long, EvaluationCriterion> existing, List<CriterionInput> inputs) {
        Set<Long> inputIds = new HashSet<>();
        for (CriterionInput in : inputs) {
            if (in.id() == null) {
                return true;                       // 增加
            }
            if (existing.get(in.id()).getWeight() != in.weight()) {
                return true;                       // 改权重
            }
            inputIds.add(in.id());
        }
        return !inputIds.equals(existing.keySet()); // 删除
    }

    // ---------- 打分 ----------

    public Enrollment getEnrollment(Long enrollmentId) {
        return enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException("报名", enrollmentId));
    }

    /** 该报名的得分：维度 id → 得分 */
    public Map<Long, Integer> scoresOf(Long enrollmentId) {
        return scoreRepository.findByEnrollmentId(enrollmentId).stream()
                .collect(Collectors.toMap(s -> s.getCriterion().getId(), CriterionScore::getScore));
    }

    /**
     * 给 1 个报名者打分（可以一次打多个维度）。
     * 得分为空 ＝ 未打分（已有得分会被清除）。有 1 个得分不对，全部都不保存。
     *
     * @param scores 维度 id → 画面输入的得分（字符串）
     */
    @Transactional
    public void saveScores(Long enrollmentId, Map<Long, String> scores) {
        Enrollment enrollment = getEnrollment(enrollmentId);

        // 先全部检查，再保存
        Map<EvaluationCriterion, Integer> parsed = new LinkedHashMap<>();
        for (Map.Entry<Long, String> entry : scores.entrySet()) {
            EvaluationCriterion criterion = criterionRepository.findById(entry.getKey())
                    .orElseThrow(() -> new NotFoundException("评价维度", entry.getKey()));
            if (!criterion.getCourse().getId().equals(enrollment.getCourse().getId())) {
                throw new BusinessException("该学员没有报名「" + criterion.getCourse().getDisplayName()
                        + "」，不能按这门课程的维度打分");
            }
            parsed.put(criterion, parseScore(entry.getValue(), criterion));
        }

        for (Map.Entry<EvaluationCriterion, Integer> entry : parsed.entrySet()) {
            EvaluationCriterion criterion = entry.getKey();
            Integer score = entry.getValue();
            Optional<CriterionScore> current =
                    scoreRepository.findByEnrollmentIdAndCriterionId(enrollmentId, criterion.getId());
            if (score == null) {
                current.ifPresent(scoreRepository::delete);
            } else if (current.isPresent()) {
                current.get().setScore(score);
            } else {
                scoreRepository.save(new CriterionScore(enrollment, criterion, score));
            }
        }
    }

    /** 空 → null（未打分）。不是 1〜5 的整数 → 业务错误 */
    private Integer parseScore(String raw, EvaluationCriterion criterion) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String message = "得分必须是 1〜5 的整数（维度：" + criterion.getName() + "，输入：" + raw.trim() + "）";
        int score;
        try {
            score = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(message);
        }
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new BusinessException(message);
        }
        return score;
    }

    // ---------- 结果一览 ----------

    /** 该课程每个报名者的得分和总分・评级（按报名顺序） */
    public List<EvaluationRow> results(Long courseId) {
        List<EvaluationCriterion> criteria = findCriteria(courseId);
        Map<Long, Map<Long, Integer>> scoresByEnrollment = new HashMap<>();
        for (CriterionScore s : scoreRepository.findByEnrollmentCourseId(courseId)) {
            scoresByEnrollment.computeIfAbsent(s.getEnrollment().getId(), k -> new HashMap<>())
                    .put(s.getCriterion().getId(), s.getScore());
        }

        List<EvaluationRow> rows = new ArrayList<>();
        for (Enrollment e : enrollmentRepository.findByCourseIdOrderByIdAsc(courseId)) {
            Map<Long, Integer> scores = scoresByEnrollment.getOrDefault(e.getId(), Map.of());
            List<WeightedScoreCalculator.Item> items = criteria.stream()
                    .map(c -> new WeightedScoreCalculator.Item(c.getWeight(), scores.get(c.getId())))
                    .toList();
            rows.add(new EvaluationRow(e, scores, WeightedScoreCalculator.calculate(items)));
        }
        return rows;
    }
}
