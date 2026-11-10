package com.example.training.web;

import com.example.training.domain.Trainee;
import com.example.training.domain.TraineeStatus;
import com.example.training.service.BusinessException;
import com.example.training.service.TraineeService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;

/** F1 学员管理（本项目的范本：其他管理画面都按这个写法） */
@Controller
@RequestMapping("/trainees")
public class TraineeController {

    private final TraineeService service;
    private final Clock clock;

    public TraineeController(TraineeService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @ModelAttribute("statuses")
    public TraineeStatus[] statuses() {
        return TraineeStatus.values();
    }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) TraineeStatus status,
                       Model model) {
        model.addAttribute("trainees", service.search(keyword, status));
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        return "trainees/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        Trainee t = new Trainee();
        t.setJoinedOn(LocalDate.now(clock));
        model.addAttribute("trainee", t);
        return "trainees/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("trainee") Trainee trainee, BindingResult result,
                         Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "trainees/form";
        }
        try {
            service.create(trainee);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "trainees/form";
        }
        ra.addFlashAttribute("message", "已登记学员：" + trainee.getName());
        return "redirect:/trainees";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("trainee", service.get(id));
        return "trainees/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("trainee") Trainee trainee,
                         BindingResult result, Model model, RedirectAttributes ra) {
        trainee.setId(id);
        if (result.hasErrors()) {
            return "trainees/form";
        }
        try {
            service.update(id, trainee);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "trainees/form";
        }
        ra.addFlashAttribute("message", "已更新学员：" + trainee.getName());
        return "redirect:/trainees";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.delete(id);
            ra.addFlashAttribute("message", "已删除学员");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/trainees";
    }
}
