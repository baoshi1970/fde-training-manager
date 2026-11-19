package com.example.training.service;

import com.example.training.domain.Attendance;
import com.example.training.domain.AttendanceStatus;
import com.example.training.domain.Course;
import com.example.training.domain.CourseCategory;
import com.example.training.domain.CourseSession;
import com.example.training.domain.CriterionScore;
import com.example.training.domain.Enrollment;
import com.example.training.domain.EvaluationCriterion;
import com.example.training.domain.Instructor;
import com.example.training.domain.SessionMode;
import com.example.training.domain.Trainee;
import com.example.training.domain.TraineeStatus;
import com.example.training.repository.AttendanceRepository;
import com.example.training.repository.AttendanceRepository.StatusCount;
import com.example.training.repository.CourseRepository;
import com.example.training.repository.CourseSessionRepository;
import com.example.training.repository.CriterionScoreRepository;
import com.example.training.repository.CriterionScoreRepository.ScoreValue;
import com.example.training.repository.EnrollmentRepository;
import com.example.training.repository.EvaluationCriterionRepository;
import com.example.training.repository.InstructorRepository;
import com.example.training.repository.TraineeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * #9 学员进度看板：看板用的汇总查询，在真实的数据库（H2）上确认。
 * 「今天」是 TODAY；课次日期 ≤ 今天的出勤才计入（讲师在 #9 的回答 1）。
 */
@DataJpaTest
class ProgressQueryTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    @Autowired
    AttendanceRepository attendanceRepository;
    @Autowired
    CriterionScoreRepository scoreRepository;
    @Autowired
    EnrollmentRepository enrollmentRepository;
    @Autowired
    EvaluationCriterionRepository criterionRepository;
    @Autowired
    TraineeRepository traineeRepository;
    @Autowired
    CourseRepository courseRepository;
    @Autowired
    CourseSessionRepository sessionRepository;
    @Autowired
    InstructorRepository instructorRepository;

    Course course;
    Instructor instructor;
    Trainee 甲;
    Trainee 乙;

    @BeforeEach
    void setUp() {
        course = courseRepository.save(new Course("S2", "AI 辅助编码", CourseCategory.COMMON, true, 16));
        instructor = instructorRepository.save(new Instructor("讲师 甲", "teacher@example.com", null));
        甲 = trainee("学员 甲", "a@example.com", TraineeStatus.ACTIVE);
        乙 = trainee("学员 乙", "b@example.com", TraineeStatus.ACTIVE);
    }

    private Trainee trainee(String name, String email, TraineeStatus status) {
        return traineeRepository.save(new Trainee(name, email, null, status, TODAY.minusMonths(1)));
    }

    private CourseSession session(LocalDate date) {
        return sessionRepository.save(new CourseSession(course, instructor, date,
                LocalTime.of(10, 0), LocalTime.of(12, 0), SessionMode.ONLINE, "Zoom"));
    }

    private void attend(CourseSession s, Trainee t, AttendanceStatus status) {
        attendanceRepository.save(new Attendance(s, t, status));
    }

    // ---- 出勤汇总 ----

    @Test
    void 出勤汇总_没有记录则为空() {
        assertThat(attendanceRepository.countByTraineeAndStatusUpTo(TODAY)).isEmpty();
    }

    @Test
    void 出勤汇总_按学员和状态分别计数() {
        CourseSession s1 = session(TODAY.minusDays(3));
        CourseSession s2 = session(TODAY.minusDays(2));
        CourseSession s3 = session(TODAY.minusDays(1));
        attend(s1, 甲, AttendanceStatus.PRESENT);
        attend(s2, 甲, AttendanceStatus.PRESENT);
        attend(s3, 甲, AttendanceStatus.LATE);
        attend(s1, 乙, AttendanceStatus.ABSENT);

        List<StatusCount> counts = attendanceRepository.countByTraineeAndStatusUpTo(TODAY);

        assertThat(counts)
                .extracting(StatusCount::getTraineeId, StatusCount::getStatus, StatusCount::getTotal)
                .containsExactlyInAnyOrder(
                        tuple(甲.getId(), AttendanceStatus.PRESENT, 2L),
                        tuple(甲.getId(), AttendanceStatus.LATE, 1L),
                        tuple(乙.getId(), AttendanceStatus.ABSENT, 1L));
    }

    @Test
    void 出勤汇总_今天的课次计入_明天的不计入() {
        attend(session(TODAY), 甲, AttendanceStatus.PRESENT);
        attend(session(TODAY.plusDays(1)), 甲, AttendanceStatus.PRESENT);

        List<StatusCount> counts = attendanceRepository.countByTraineeAndStatusUpTo(TODAY);

        assertThat(counts)
                .extracting(StatusCount::getTraineeId, StatusCount::getStatus, StatusCount::getTotal)
                .containsExactly(tuple(甲.getId(), AttendanceStatus.PRESENT, 1L));
    }

    @Test
    void 出勤汇总_没有报名的出勤记录也计入() {
        // 取消报名后出勤照算（讲师在 #9 的回答 2）：这里根本不建报名，出勤记录仍要被数到
        attend(session(TODAY.minusDays(1)), 甲, AttendanceStatus.LATE);

        assertThat(enrollmentRepository.count()).isZero();
        assertThat(attendanceRepository.countByTraineeAndStatusUpTo(TODAY))
                .extracting(StatusCount::getTraineeId, StatusCount::getStatus, StatusCount::getTotal)
                .containsExactly(tuple(甲.getId(), AttendanceStatus.LATE, 1L));
    }

    // ---- 维度得分 ----

    @Test
    void 维度得分_没有得分则为空() {
        assertThat(scoreRepository.findAllScoreValues()).isEmpty();
    }

    @Test
    void 维度得分_只取报名id维度id和得分() {
        Enrollment e = enrollmentRepository.save(new Enrollment(甲, course, TODAY.minusDays(10)));
        EvaluationCriterion c1 = criterionRepository.save(new EvaluationCriterion(course, "三段法", 60, 0));
        EvaluationCriterion c2 = criterionRepository.save(new EvaluationCriterion(course, "协作", 40, 1));
        scoreRepository.save(new CriterionScore(e, c1, 4));
        scoreRepository.save(new CriterionScore(e, c2, 5));

        assertThat(scoreRepository.findAllScoreValues())
                .extracting(ScoreValue::getEnrollmentId, ScoreValue::getCriterionId, ScoreValue::getScore)
                .containsExactlyInAnyOrder(
                        tuple(e.getId(), c1.getId(), 4),
                        tuple(e.getId(), c2.getId(), 5));
    }

    // ---- 报名 ----

    @Test
    void 报名_只取在籍学员的报名() {
        Trainee 休学 = trainee("学员 丙", "c@example.com", TraineeStatus.ON_LEAVE);
        Trainee 结业 = trainee("学员 丁", "d@example.com", TraineeStatus.GRADUATED);
        Enrollment e甲 = enrollmentRepository.save(new Enrollment(甲, course, TODAY));
        enrollmentRepository.save(new Enrollment(休学, course, TODAY));
        enrollmentRepository.save(new Enrollment(结业, course, TODAY));

        assertThat(enrollmentRepository.findByTraineeStatus(TraineeStatus.ACTIVE))
                .extracting(Enrollment::getId)
                .containsExactly(e甲.getId());
    }
}
