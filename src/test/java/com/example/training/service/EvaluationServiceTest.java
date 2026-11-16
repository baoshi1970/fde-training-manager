package com.example.training.service;

import com.example.training.domain.*;
import com.example.training.repository.CriterionScoreRepository;
import com.example.training.repository.EnrollmentRepository;
import com.example.training.repository.EvaluationCriterionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/** F8 加权评分：业务规则的单元测试（Issue #8 测试式样 S1〜S20） */
@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock
    EvaluationCriterionRepository criterionRepository;
    @Mock
    CriterionScoreRepository scoreRepository;
    @Mock
    EnrollmentRepository enrollmentRepository;
    @Mock
    CourseService courseService;
    @InjectMocks
    EvaluationService service;

    private final LocalDate today = LocalDate.of(2026, 11, 16);
    private final Course s2 = course(1L, "S2");
    private final Course s3 = course(2L, "S3");

    // ---------- 测试数据 ----------

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static Course course(Long id, String code) {
        return withId(new Course(code, "课程 " + code, CourseCategory.COMMON, true, 8), id);
    }

    private static EvaluationCriterion criterion(Long id, Course course, String name, int weight) {
        return withId(new EvaluationCriterion(course, name, weight, 0), id);
    }

    private Enrollment enrollment(Long id, Course course, String traineeName) {
        Trainee t = new Trainee(traineeName, id + "@example.com", "A 社", TraineeStatus.ACTIVE, today);
        return withId(new Enrollment(t, course, today), id);
    }

    private static CriterionInput input(Long id, String name, Integer weight) {
        return new CriterionInput(id, name, weight);
    }

    private static Map<Long, String> scores(Object... kv) {
        Map<Long, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(((Number) kv[i]).longValue(), (String) kv[i + 1]);
        }
        return m;
    }

    // ---------- 评价维度的保存 ----------

    @Test
    void S1_权重合计100可以保存() {
        when(courseService.get(1L)).thenReturn(s2);

        service.saveCriteria(1L, List.of(input(null, "三段法", 60), input(null, "协作", 40)));

        ArgumentCaptor<EvaluationCriterion> captor = ArgumentCaptor.forClass(EvaluationCriterion.class);
        verify(criterionRepository, times(2)).save(captor.capture());
        List<EvaluationCriterion> saved = captor.getAllValues();
        assertThat(saved).extracting(EvaluationCriterion::getName).containsExactly("三段法", "协作");
        assertThat(saved).extracting(EvaluationCriterion::getWeight).containsExactly(60, 40);
        assertThat(saved).extracting(EvaluationCriterion::getSortOrder).containsExactly(0, 1);
        assertThat(saved).allSatisfy(c -> assertThat(c.getCourse()).isSameAs(s2));
    }

    @Test
    void S2_权重合计99不能保存() {
        when(courseService.get(1L)).thenReturn(s2);

        assertThatThrownBy(() -> service.saveCriteria(1L, List.of(input(null, "A", 50), input(null, "B", 49))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("100")
                .hasMessageContaining("99");
        verify(criterionRepository, never()).save(any());
    }

    @Test
    void S3_权重合计101不能保存() {
        when(courseService.get(1L)).thenReturn(s2);

        assertThatThrownBy(() -> service.saveCriteria(1L, List.of(input(null, "A", 50), input(null, "B", 51))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("101");
        verify(criterionRepository, never()).save(any());
    }

    @Test
    void S4_维度为空不能保存() {
        when(courseService.get(1L)).thenReturn(s2);

        assertThatThrownBy(() -> service.saveCriteria(1L, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少");
    }

    @Test
    void S5_权重为0不能保存_即使合计100() {
        when(courseService.get(1L)).thenReturn(s2);

        assertThatThrownBy(() -> service.saveCriteria(1L, List.of(input(null, "A", 0), input(null, "B", 100))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("权重");
        verify(criterionRepository, never()).save(any());
    }

    @Test
    void S6_权重为负数不能保存() {
        when(courseService.get(1L)).thenReturn(s2);

        assertThatThrownBy(() -> service.saveCriteria(1L, List.of(input(null, "A", -10), input(null, "B", 110))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("权重");
    }

    @Test
    void S6_权重为空不能保存() {
        when(courseService.get(1L)).thenReturn(s2);

        assertThatThrownBy(() -> service.saveCriteria(1L, List.of(input(null, "A", null), input(null, "B", 100))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("权重");
    }

    @Test
    void S6_维度名称为空不能保存() {
        when(courseService.get(1L)).thenReturn(s2);

        assertThatThrownBy(() -> service.saveCriteria(1L, List.of(input(null, "  ", 50), input(null, "B", 50))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("名称");
    }

    @Test
    void S7_同一课程维度名称重复不能保存() {
        when(courseService.get(1L)).thenReturn(s2);

        // 前后空格不同也算重复
        assertThatThrownBy(() -> service.saveCriteria(1L, List.of(input(null, "三段法", 50), input(null, " 三段法 ", 50))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("三段法");
        verify(criterionRepository, never()).save(any());
    }

    @Test
    void S8_课程不存在() {
        when(courseService.get(99L)).thenThrow(new NotFoundException("课程", 99L));

        assertThatThrownBy(() -> service.saveCriteria(99L, List.of(input(null, "A", 100))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void S19_已有得分时修改权重不能保存() {
        EvaluationCriterion a = criterion(10L, s2, "A", 60);
        EvaluationCriterion b = criterion(11L, s2, "B", 40);
        when(courseService.get(1L)).thenReturn(s2);
        when(criterionRepository.findByCourseIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(a, b));
        when(scoreRepository.existsByCriterionCourseId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.saveCriteria(1L, List.of(input(10L, "A", 50), input(11L, "B", 50))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有得分");
        assertThat(a.getWeight()).isEqualTo(60);
        assertThat(b.getWeight()).isEqualTo(40);
    }

    @Test
    void S19_已有得分时增加维度不能保存() {
        EvaluationCriterion a = criterion(10L, s2, "A", 100);
        when(courseService.get(1L)).thenReturn(s2);
        when(criterionRepository.findByCourseIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(a));
        when(scoreRepository.existsByCriterionCourseId(1L)).thenReturn(true);

        // 已有得分时先检查「有没有增删维度・改权重」，所以消息是「已有得分」而不是「合计不是 100」
        assertThatThrownBy(() -> service.saveCriteria(1L, List.of(input(10L, "A", 100), input(null, "B", 10))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有得分");
        verify(criterionRepository, never()).save(any());
    }

    @Test
    void S19_已有得分时删除维度不能保存() {
        EvaluationCriterion a = criterion(10L, s2, "A", 50);
        EvaluationCriterion b = criterion(11L, s2, "B", 50);
        when(courseService.get(1L)).thenReturn(s2);
        when(criterionRepository.findByCourseIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(a, b));
        when(scoreRepository.existsByCriterionCourseId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.saveCriteria(1L, List.of(input(10L, "A", 100))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有得分");
        verify(criterionRepository, never()).deleteAll(anyCollection());
    }

    @Test
    void S19_已有得分时只改名称可以保存() {
        EvaluationCriterion a = criterion(10L, s2, "Skill 创建", 60);
        EvaluationCriterion b = criterion(11L, s2, "协作", 40);
        when(courseService.get(1L)).thenReturn(s2);
        when(criterionRepository.findByCourseIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(a, b));
        when(scoreRepository.existsByCriterionCourseId(1L)).thenReturn(true);

        service.saveCriteria(1L, List.of(input(10L, "Skill 编写", 60), input(11L, "协作", 40)));

        assertThat(a.getName()).isEqualTo("Skill 编写");
        assertThat(a.getWeight()).isEqualTo(60);
        verify(criterionRepository, never()).save(any());
    }

    @Test
    void S20_没有得分时可以修改权重和删除维度() {
        EvaluationCriterion a = criterion(10L, s2, "A", 50);
        EvaluationCriterion b = criterion(11L, s2, "B", 30);
        EvaluationCriterion c = criterion(12L, s2, "C", 20);
        when(courseService.get(1L)).thenReturn(s2);
        when(criterionRepository.findByCourseIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(a, b, c));
        when(scoreRepository.existsByCriterionCourseId(1L)).thenReturn(false);

        service.saveCriteria(1L, List.of(input(10L, "A", 70), input(11L, "B", 30)));

        assertThat(a.getWeight()).isEqualTo(70);
        verify(criterionRepository).deleteAll(List.of(c));
    }

    @Test
    void 其他课程的维度id不能混进来() {
        when(courseService.get(1L)).thenReturn(s2);
        when(criterionRepository.findByCourseIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.saveCriteria(1L, List.of(input(99L, "A", 100))))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- 打分 ----------

    /** S2 课程：维度 10(权重60)・11(权重40)，报名 100 */
    private Enrollment givenS2Enrollment() {
        Enrollment e = enrollment(100L, s2, "山田 太郎");
        lenient().when(enrollmentRepository.findById(100L)).thenReturn(Optional.of(e));
        lenient().when(criterionRepository.findById(10L)).thenReturn(Optional.of(criterion(10L, s2, "A", 60)));
        lenient().when(criterionRepository.findById(11L)).thenReturn(Optional.of(criterion(11L, s2, "B", 40)));
        return e;
    }

    private CriterionScore savedScore() {
        ArgumentCaptor<CriterionScore> captor = ArgumentCaptor.forClass(CriterionScore.class);
        verify(scoreRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void S9_得分1可以保存_下边界() {
        Enrollment e = givenS2Enrollment();

        service.saveScores(100L, scores(10L, "1"));

        CriterionScore s = savedScore();
        assertThat(s.getScore()).isEqualTo(1);
        assertThat(s.getEnrollment()).isSameAs(e);
        assertThat(s.getCriterion().getId()).isEqualTo(10L);
    }

    @Test
    void S10_得分5可以保存_上边界() {
        givenS2Enrollment();

        service.saveScores(100L, scores(10L, "5"));

        assertThat(savedScore().getScore()).isEqualTo(5);
    }

    @Test
    void S11_得分0不能保存() {
        givenS2Enrollment();

        assertThatThrownBy(() -> service.saveScores(100L, scores(10L, "0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1〜5");
        verify(scoreRepository, never()).save(any());
    }

    @Test
    void S12_得分6不能保存() {
        givenS2Enrollment();

        assertThatThrownBy(() -> service.saveScores(100L, scores(10L, "6")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1〜5");
    }

    @Test
    void S12_得分是小数不能保存() {
        givenS2Enrollment();

        assertThatThrownBy(() -> service.saveScores(100L, scores(10L, "3.5")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("整数");
    }

    @Test
    void S12_得分不是数字不能保存() {
        givenS2Enrollment();

        assertThatThrownBy(() -> service.saveScores(100L, scores(10L, "abc")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("整数");
    }

    @Test
    void S12_有1个得分不对时_其他维度也不保存() {
        givenS2Enrollment();

        assertThatThrownBy(() -> service.saveScores(100L, scores(10L, "4", 11L, "9")))
                .isInstanceOf(BusinessException.class);
        verify(scoreRepository, never()).save(any());
    }

    @Test
    void S13_得分为空表示未打分_不保存_已有得分被清除() {
        Enrollment e = givenS2Enrollment();
        CriterionScore existing = new CriterionScore(e, criterion(10L, s2, "A", 60), 3);
        when(scoreRepository.findByEnrollmentIdAndCriterionId(100L, 10L)).thenReturn(Optional.of(existing));

        service.saveScores(100L, scores(10L, " "));

        verify(scoreRepository, never()).save(any());
        verify(scoreRepository).delete(existing);
    }

    @Test
    void S14_维度属于别的课程_不能打分() {
        givenS2Enrollment();
        when(criterionRepository.findById(20L)).thenReturn(Optional.of(criterion(20L, s3, "X", 100)));

        assertThatThrownBy(() -> service.saveScores(100L, scores(20L, "3")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报名");
        verify(scoreRepository, never()).save(any());
    }

    @Test
    void S15_报名不存在() {
        when(enrollmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveScores(999L, scores(10L, "3")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void S15_维度不存在() {
        givenS2Enrollment();
        when(criterionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveScores(100L, scores(999L, "3")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void S16_再次打分覆盖原分数_不新增() {
        Enrollment e = givenS2Enrollment();
        CriterionScore existing = new CriterionScore(e, criterion(10L, s2, "A", 60), 2);
        when(scoreRepository.findByEnrollmentIdAndCriterionId(100L, 10L)).thenReturn(Optional.of(existing));

        service.saveScores(100L, scores(10L, "4"));

        assertThat(existing.getScore()).isEqualTo(4);
        verify(scoreRepository, never()).save(any());
    }

    // ---------- 一览 ----------

    @Test
    void S17_S18_只返回该课程的报名者_打完的显示总分_没打完的显示未完成() {
        EvaluationCriterion a = criterion(10L, s2, "A", 60);
        EvaluationCriterion b = criterion(11L, s2, "B", 40);
        Enrollment done = enrollment(100L, s2, "山田 太郎");
        Enrollment half = enrollment(101L, s2, "李 明");
        when(criterionRepository.findByCourseIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(a, b));
        when(enrollmentRepository.findByCourseIdOrderByIdAsc(1L)).thenReturn(List.of(done, half));
        when(scoreRepository.findByEnrollmentCourseId(1L)).thenReturn(List.of(
                new CriterionScore(done, a, 5), new CriterionScore(done, b, 4),   // (300+160)/5 = 92.0
                new CriterionScore(half, a, 3)));

        List<EvaluationRow> rows = service.results(1L);

        assertThat(rows).extracting(r -> r.enrollment().getId()).containsExactly(100L, 101L);
        assertThat(rows.get(0).scores()).containsEntry(10L, 5).containsEntry(11L, 4);
        assertThat(rows.get(0).result().complete()).isTrue();
        assertThat(rows.get(0).result().total().toPlainString()).isEqualTo("92.0");
        assertThat(rows.get(0).result().grade()).isEqualTo(Grade.A);
        assertThat(rows.get(1).scores()).containsEntry(10L, 3).doesNotContainKey(11L);
        assertThat(rows.get(1).result().complete()).isFalse();
        verify(enrollmentRepository, never()).findAll();
    }

    @Test
    void 没有设置维度的课程_所有报名者都是未完成() {
        Enrollment e = enrollment(100L, s3, "山田 太郎");
        when(criterionRepository.findByCourseIdOrderBySortOrderAscIdAsc(2L)).thenReturn(List.of());
        when(enrollmentRepository.findByCourseIdOrderByIdAsc(2L)).thenReturn(List.of(e));
        when(scoreRepository.findByEnrollmentCourseId(2L)).thenReturn(List.of());

        List<EvaluationRow> rows = service.results(2L);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).result().complete()).isFalse();
    }
}
