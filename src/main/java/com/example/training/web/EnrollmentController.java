package com.example.training.web;

import com.example.training.domain.Course;
import com.example.training.domain.TraineeStatus;
import com.example.training.service.BusinessException;
import com.example.training.service.CourseService;
import com.example.training.service.EnrollmentService;
import com.example.training.service.TraineeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** F5 报名管理：选课程 → 看报名者 → 添加／取消 */
@Controller
@RequestMapping("/enrollments")
public class EnrollmentController {

    private final EnrollmentService service;
    private final CourseService courseService;
    private final TraineeService traineeService;
    private final Clock clock;

    public EnrollmentController(EnrollmentService service, CourseService courseService,
                                TraineeService traineeService, Clock clock) {
        this.service = service;
        this.courseService = courseService;
        this.traineeService = traineeService;
        this.clock = clock;
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
            model.addAttribute("enrollments", service.findByCourse(courseId));
            model.addAttribute("activeTrainees", traineeService.search(null, TraineeStatus.ACTIVE));
        }
        model.addAttribute("courseId", courseId);
        return "enrollments/list";
    }

    @PostMapping
    public String enroll(@RequestParam Long courseId, @RequestParam Long traineeId, RedirectAttributes ra) {
        try {
            service.enroll(traineeId, courseId, LocalDate.now(clock));
            ra.addFlashAttribute("message", "已报名");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/enrollments?courseId=" + courseId;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam Long courseId, RedirectAttributes ra) {
        service.cancel(id);
        ra.addFlashAttribute("message", "已取消报名");
        return "redirect:/enrollments?courseId=" + courseId;
    }
}
