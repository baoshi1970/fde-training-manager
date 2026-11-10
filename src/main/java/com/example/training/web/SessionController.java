package com.example.training.web;

import com.example.training.domain.Course;
import com.example.training.domain.CourseSession;
import com.example.training.domain.Instructor;
import com.example.training.domain.SessionMode;
import com.example.training.service.BusinessException;
import com.example.training.service.CourseService;
import com.example.training.service.InstructorService;
import com.example.training.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** F4 课次排期 */
@Controller
@RequestMapping("/sessions")
public class SessionController {

    private final SessionService service;
    private final CourseService courseService;
    private final InstructorService instructorService;
    private final Clock clock;

    public SessionController(SessionService service, CourseService courseService,
                             InstructorService instructorService, Clock clock) {
        this.service = service;
        this.courseService = courseService;
        this.instructorService = instructorService;
        this.clock = clock;
    }

    @ModelAttribute("courses")
    public List<Course> courses() {
        return courseService.findAll();
    }

    @ModelAttribute("instructors")
    public List<Instructor> instructors() {
        return instructorService.findAll();
    }

    @ModelAttribute("modes")
    public SessionMode[] modes() {
        return SessionMode.values();
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long courseId, Model model) {
        model.addAttribute("sessions", service.findAll(courseId));
        model.addAttribute("courseId", courseId);
        return "sessions/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        CourseSession s = new CourseSession();
        s.setDate(LocalDate.now(clock));
        s.setStartTime(LocalTime.of(19, 30));
        s.setEndTime(LocalTime.of(21, 0));
        s.setMode(SessionMode.ONLINE);
        model.addAttribute("courseSession", s);
        return "sessions/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("courseSession") CourseSession session, BindingResult result,
                         Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "sessions/form";
        }
        try {
            service.create(session);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "sessions/form";
        }
        ra.addFlashAttribute("message", "已登记课次");
        return "redirect:/sessions";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("courseSession", service.get(id));
        return "sessions/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("courseSession") CourseSession session,
                         BindingResult result, Model model, RedirectAttributes ra) {
        session.setId(id);
        if (result.hasErrors()) {
            return "sessions/form";
        }
        try {
            service.update(id, session);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "sessions/form";
        }
        ra.addFlashAttribute("message", "已更新课次");
        return "redirect:/sessions";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        service.delete(id);
        ra.addFlashAttribute("message", "已删除课次");
        return "redirect:/sessions";
    }
}
