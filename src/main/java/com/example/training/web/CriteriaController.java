package com.example.training.web;

import com.example.training.domain.Course;
import com.example.training.service.BusinessException;
import com.example.training.service.CourseService;
import com.example.training.service.CriterionInput;
import com.example.training.service.EvaluationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** F8 评价维度设置（只有管理员：/admin/** 由 SecurityConfig 限制） */
@Controller
@RequestMapping("/admin/courses/{courseId}/criteria")
public class CriteriaController {

    /** 没有得分时，在已有维度后面追加的空行数 */
    private static final int BLANK_ROWS = 3;

    private final EvaluationService service;
    private final CourseService courseService;

    public CriteriaController(EvaluationService service, CourseService courseService) {
        this.service = service;
        this.courseService = courseService;
    }

    @GetMapping
    public String form(@PathVariable Long courseId, Model model) {
        boolean locked = service.hasScores(courseId);
        CriteriaForm form = new CriteriaForm();
        service.findCriteria(courseId).forEach(c ->
                form.getRows().add(new CriteriaForm.Row(c.getId(), c.getName(), c.getWeight())));
        if (!locked) {
            for (int i = 0; i < BLANK_ROWS; i++) {
                form.getRows().add(new CriteriaForm.Row());
            }
        }
        model.addAttribute("form", form);
        return render(courseId, locked, model);
    }

    @PostMapping
    public String save(@PathVariable Long courseId, @ModelAttribute("form") CriteriaForm form,
                       BindingResult result, Model model, RedirectAttributes ra) {
        boolean locked = service.hasScores(courseId);
        if (result.hasErrors()) {
            model.addAttribute("error", "权重必须是整数");
            return render(courseId, locked, model);
        }
        List<CriterionInput> inputs = form.getRows().stream()
                .filter(r -> !r.isBlank())
                .map(r -> new CriterionInput(r.getId(), r.getName(), r.getWeight()))
                .toList();
        try {
            service.saveCriteria(courseId, inputs);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return render(courseId, locked, model);
        }
        ra.addFlashAttribute("message", "已保存评价维度");
        return "redirect:/admin/courses/" + courseId + "/criteria";
    }

    private String render(Long courseId, boolean locked, Model model) {
        Course course = courseService.get(courseId);
        model.addAttribute("course", course);
        model.addAttribute("locked", locked);
        return "evaluations/criteria";
    }
}
