package com.example.training.web;

import com.example.training.domain.Course;
import com.example.training.domain.Enrollment;
import com.example.training.service.BusinessException;
import com.example.training.service.CourseService;
import com.example.training.service.EvaluationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F8 加权评分：选课程 → 看评分一览 → 给报名者打分。
 * 打分（/{报名id}/edit・POST /{报名id}）管理员和讲师都可以（SecurityConfig 限制，#16）
 */
@Controller
@RequestMapping("/evaluations")
public class EvaluationController {

    /** 打分表单的输入项名：score_{维度id} */
    private static final String SCORE_PREFIX = "score_";

    private final EvaluationService service;
    private final CourseService courseService;

    public EvaluationController(EvaluationService service, CourseService courseService) {
        this.service = service;
        this.courseService = courseService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long courseId, Model model) {
        List<Course> courses = courseService.findAll();
        model.addAttribute("courses", courses);
        if (courseId == null && !courses.isEmpty()) {
            courseId = courses.get(0).getId();
        }
        if (courseId != null) {
            model.addAttribute("course", courseService.get(courseId));
            model.addAttribute("criteria", service.findCriteria(courseId));
            model.addAttribute("rows", service.results(courseId));
        }
        model.addAttribute("courseId", courseId);
        return "evaluations/list";
    }

    @GetMapping("/{enrollmentId}/edit")
    public String editForm(@PathVariable Long enrollmentId, Model model) {
        Map<Long, String> values = new LinkedHashMap<>();
        service.scoresOf(enrollmentId).forEach((k, v) -> values.put(k, String.valueOf(v)));
        return renderForm(enrollmentId, values, model);
    }

    @PostMapping("/{enrollmentId}")
    public String save(@PathVariable Long enrollmentId, @RequestParam Map<String, String> params,
                       Model model, RedirectAttributes ra) {
        Map<Long, String> scores = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            if (key.startsWith(SCORE_PREFIX)) {
                try {
                    scores.put(Long.valueOf(key.substring(SCORE_PREFIX.length())), value);
                } catch (NumberFormatException ignored) {
                    // 不是本画面产生的输入项，忽略
                }
            }
        });
        Enrollment enrollment = service.getEnrollment(enrollmentId);
        try {
            service.saveScores(enrollmentId, scores);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return renderForm(enrollmentId, scores, model);
        }
        ra.addFlashAttribute("message", "已保存评分：" + enrollment.getTrainee().getName());
        return "redirect:/evaluations?courseId=" + enrollment.getCourse().getId();
    }

    private String renderForm(Long enrollmentId, Map<Long, String> values, Model model) {
        Enrollment enrollment = service.getEnrollment(enrollmentId);
        model.addAttribute("enrollment", enrollment);
        model.addAttribute("criteria", service.findCriteria(enrollment.getCourse().getId()));
        model.addAttribute("values", values);
        return "evaluations/form";
    }
}
