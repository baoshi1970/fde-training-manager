package com.example.training.web;

import com.example.training.domain.Course;
import com.example.training.domain.CourseCategory;
import com.example.training.service.BusinessException;
import com.example.training.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** F3 课程管理 */
@Controller
@RequestMapping("/courses")
public class CourseController {

    private final CourseService service;

    public CourseController(CourseService service) {
        this.service = service;
    }

    @ModelAttribute("categories")
    public CourseCategory[] categories() {
        return CourseCategory.values();
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("courses", service.findAll());
        return "courses/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("course", new Course());
        return "courses/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("course") Course course, BindingResult result,
                         Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "courses/form";
        }
        try {
            service.create(course);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "courses/form";
        }
        ra.addFlashAttribute("message", "已登记课程：" + course.getDisplayName());
        return "redirect:/courses";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("course", service.get(id));
        return "courses/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("course") Course course,
                         BindingResult result, Model model, RedirectAttributes ra) {
        course.setId(id);
        if (result.hasErrors()) {
            return "courses/form";
        }
        try {
            service.update(id, course);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "courses/form";
        }
        ra.addFlashAttribute("message", "已更新课程：" + course.getDisplayName());
        return "redirect:/courses";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.delete(id);
            ra.addFlashAttribute("message", "已删除课程");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/courses";
    }
}
