package com.example.training.service;

import com.example.training.domain.AttendanceStatus;
import com.example.training.domain.Course;
import com.example.training.domain.CourseCategory;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** #9 学员进度看板：把查询结果组装成看板的每一行 */
@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    @Mock
    TraineeRepository traineeRepository;
    @Mock
    EnrollmentRepository enrollmentRepository;
    @Mock
    EvaluationCriterionRepository criterionRepository;
    @Mock
    CriterionScoreRepository scoreRepository;
    @Mock
    AttendanceRepository attendanceRepository;

    ProgressService service;

    Trainee 甲;
    Trainee 乙;
    Course s2;
    Course c1;

    // 各查询的返回值。测试里往这些 list 里加数据，再调用 stubAll()
    final List<Trainee> trainees = new ArrayList<>();
    final List<Enrollment> enrollments = new ArrayList<>();
    final List<EvaluationCriterion> criteria = new ArrayList<>();
    final List<ScoreValue> scores = new ArrayList<>();
    final List<StatusCount> counts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE);
        service = new ProgressService(traineeRepository, enrollmentRepository, criterionRepository,
                scoreRepository, attendanceRepository, clock);
        甲 = trainee(1L, "学员 甲");
        乙 = trainee(2L, "学员 乙");
        s2 = course(10L, "S2");
        c1 = course(20L, "C1");
    }

    private void stubAll() {
        when(traineeRepository.findByStatusOrderByIdAsc(TraineeStatus.ACTIVE)).thenReturn(trainees);
        when(enrollmentRepository.findByTraineeStatus(TraineeStatus.ACTIVE)).thenReturn(enrollments);
        when(criterionRepository.findAll()).thenReturn(criteria);
        when(scoreRepository.findAllScoreValues()).thenReturn(scores);
        // 只对「今天」返回数据：Service 传错日期的话，出勤汇总就是空的，测试会失败
        when(attendanceRepository.countByTraineeAndStatusUpTo(TODAY)).thenReturn(counts);
    }

    // ---- 造数据的小工具 ----

    private static Trainee trainee(Long id, String name) {
        Trainee t = new Trainee(name, "t" + id + "@example.com", null, TraineeStatus.ACTIVE, TODAY.minusMonths(1));
        t.setId(id);
        return t;
    }

    private static Course course(Long id, String code) {
        Course c = new Course(code, "课程 " + code, CourseCategory.COMMON, true, 16);
        c.setId(id);
        return c;
    }

    private Enrollment enroll(Long id, Trainee t, Course c) {
        Enrollment e = new Enrollment(t, c, TODAY.minusDays(30));
        ReflectionTestUtils.setField(e, "id", id);
        enrollments.add(e);
        return e;
    }

    private EvaluationCriterion criterion(Long id, Course c, int weight) {
        EvaluationCriterion ec = new EvaluationCriterion(c, "维度" + id, weight, criteria.size());
        ReflectionTestUtils.setField(ec, "id", id);
        criteria.add(ec);
        return ec;
    }

    private void score(Enrollment e, EvaluationCriterion c, int score) {
        scores.add(new ScoreValue() {
            public Long getEnrollmentId() { return e.getId(); }
            public Long getCriterionId() { return c.getId(); }
            public int getScore() { return score; }
        });
    }

    private void attendance(Trainee t, AttendanceStatus status, long total) {
        counts.add(new StatusCount() {
            public Long getTraineeId() { return t.getId(); }
            public AttendanceStatus getStatus() { return status; }
            public long getTotal() { return total; }
        });
    }

    /** 课程 S2 的评价表（#8 的例子）：权重 25・15・15・20・15・10 */
    private List<EvaluationCriterion> s2评价表() {
        return List.of(criterion(101L, s2, 25), criterion(102L, s2, 15), criterion(103L, s2, 15),
                criterion(104L, s2, 20), criterion(105L, s2, 15), criterion(106L, s2, 10));
    }

    private ProgressRow rowOf(List<ProgressRow> rows, Trainee t) {
        return rows.stream().filter(r -> r.trainee() == t).findFirst().orElseThrow();
    }

    // ---- 测试 ----

    @Test
    void 没有在籍学员则看板为空() {
        stubAll();

        assertThat(service.board(false)).isEmpty();
    }

    @Test
    void 没有任何数据的学员_报名0_出勤率和平均分为空_不要关注() {
        trainees.add(甲);
        stubAll();

        ProgressRow row = rowOf(service.board(false), 甲);

        assertThat(row.courseCount()).isZero();
        assertThat(row.progress().attendanceRate()).isNull();
        assertThat(row.progress().lateCount()).isZero();
        assertThat(row.progress().averageScore()).isNull();
        assertThat(row.progress().attention()).isFalse();
    }

    @Test
    void 报名课程数_同一课程重复报名只算1门() {
        trainees.add(甲);
        enroll(1L, 甲, s2);
        enroll(2L, 甲, s2);   // #12 修好之前可能留下的重复报名
        enroll(3L, 甲, c1);
        stubAll();

        assertThat(rowOf(service.board(false), 甲).courseCount()).isEqualTo(2);
    }

    @Test
    void 出勤_出席迟到缺席的件数交给计算() {
        trainees.add(甲);
        attendance(甲, AttendanceStatus.PRESENT, 6);
        attendance(甲, AttendanceStatus.LATE, 2);
        attendance(甲, AttendanceStatus.ABSENT, 2);
        stubAll();

        ProgressRow row = rowOf(service.board(false), 甲);

        // (6 + 2) ÷ 10 = 80%，正好 80% 不要关注
        assertThat(row.progress().attendanceRate()).isEqualTo(80);
        assertThat(row.progress().lateCount()).isEqualTo(2);
        assertThat(row.progress().attention()).isFalse();
    }

    @Test
    void 评分_全部维度打完则用F8的计算算出总分() {
        trainees.add(甲);
        Enrollment e = enroll(1L, 甲, s2);
        List<EvaluationCriterion> cs = s2评价表();
        int[] points = {4, 3, 5, 4, 3, 5};
        for (int i = 0; i < cs.size(); i++) {
            score(e, cs.get(i), points[i]);
        }
        stubAll();

        // #8 的例子：395 ÷ 5 = 79.0（B）
        assertThat(rowOf(service.board(false), 甲).progress().averageScore()).isEqualByComparingTo("79.0");
    }

    @Test
    void 评分_有维度没打分的课程不算进平均() {
        trainees.add(甲);
        Enrollment eS2 = enroll(1L, 甲, s2);
        List<EvaluationCriterion> cs = s2评价表();
        for (int i = 0; i < cs.size() - 1; i++) {   // 最后一个维度没打分 → 未完成
            score(eS2, cs.get(i), 5);
        }
        Enrollment eC1 = enroll(2L, 甲, c1);
        score(eC1, criterion(201L, c1, 100), 4);    // C1：100 × 4 ÷ 5 = 80.0
        stubAll();

        assertThat(rowOf(service.board(false), 甲).progress().averageScore()).isEqualByComparingTo("80.0");
    }

    @Test
    void 评分_课程没有评价维度则不算() {
        trainees.add(甲);
        enroll(1L, 甲, s2);
        stubAll();

        assertThat(rowOf(service.board(false), 甲).progress().averageScore()).isNull();
    }

    @Test
    void 评分_有D评级则要关注() {
        trainees.add(甲);
        Enrollment e = enroll(1L, 甲, c1);
        score(e, criterion(201L, c1, 100), 2);      // 100 × 2 ÷ 5 = 40.0 → D
        stubAll();

        ProgressRow row = rowOf(service.board(false), 甲);

        assertThat(row.progress().averageScore()).isEqualByComparingTo("40.0");
        assertThat(row.progress().attendanceRate()).isNull();
        assertThat(row.progress().attention()).isTrue();
    }

    @Test
    void 多个学员的数据不会混在一起() {
        trainees.add(甲);
        trainees.add(乙);
        Enrollment e = enroll(1L, 甲, c1);
        score(e, criterion(201L, c1, 100), 5);
        attendance(甲, AttendanceStatus.ABSENT, 3);
        stubAll();

        List<ProgressRow> rows = service.board(false);

        assertThat(rowOf(rows, 甲).courseCount()).isEqualTo(1);
        assertThat(rowOf(rows, 甲).progress().attendanceRate()).isZero();
        ProgressRow row乙 = rowOf(rows, 乙);
        assertThat(row乙.courseCount()).isZero();
        assertThat(row乙.progress().attendanceRate()).isNull();
        assertThat(row乙.progress().averageScore()).isNull();
        assertThat(row乙.progress().attention()).isFalse();
    }

    @Test
    void 只看要关注_只返回要关注的学员() {
        trainees.add(甲);
        trainees.add(乙);
        attendance(甲, AttendanceStatus.ABSENT, 1);   // 0% → 要关注
        attendance(乙, AttendanceStatus.PRESENT, 1);  // 100%
        stubAll();

        assertThat(service.board(true)).extracting(ProgressRow::trainee).containsExactly(甲);
    }

    @Test
    void 不筛选时按学员顺序返回全部() {
        trainees.add(甲);
        trainees.add(乙);
        attendance(甲, AttendanceStatus.ABSENT, 1);
        stubAll();

        assertThat(service.board(false)).extracting(ProgressRow::trainee).containsExactly(甲, 乙);
    }
}
