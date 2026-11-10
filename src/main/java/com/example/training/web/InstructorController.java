package com.example.training.web;

import com.example.training.domain.Instructor;
import com.example.training.service.BusinessException;
import com.example.training.service.InstructorService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** F2 讲师管理 */
@Controller
@RequestMapping("/instructors")
public class InstructorController {

    private final InstructorService service;

    public InstructorController(InstructorService service) {
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("instructors", service.findAll());
        return "instructors/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("instructor", new Instructor());
        return "instructors/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("instructor") Instructor instructor, BindingResult result,
                         Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "instructors/form";
        }
        try {
            service.create(instructor);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "instructors/form";
        }
        ra.addFlashAttribute("message", "已登记讲师：" + instructor.getName());
        return "redirect:/instructors";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("instructor", service.get(id));
        return "instructors/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("instructor") Instructor instructor,
                         BindingResult result, Model model, RedirectAttributes ra) {
        instructor.setId(id);
        if (result.hasErrors()) {
            return "instructors/form";
        }
        try {
            service.update(id, instructor);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "instructors/form";
        }
        ra.addFlashAttribute("message", "已更新讲师：" + instructor.getName());
        return "redirect:/instructors";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.delete(id);
            ra.addFlashAttribute("message", "已删除讲师");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/instructors";
    }
}
