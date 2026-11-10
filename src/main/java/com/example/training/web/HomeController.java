package com.example.training.web;

import com.example.training.service.CourseService;
import com.example.training.service.SessionService;
import com.example.training.service.TraineeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Clock;
import java.time.LocalDate;

/** 主页：概况数字＋近期课次＋功能实装状况 */
@Controller
public class HomeController {

    private final TraineeService traineeService;
    private final CourseService courseService;
    private final SessionService sessionService;
    private final Clock clock;

    public HomeController(TraineeService traineeService, CourseService courseService,
                          SessionService sessionService, Clock clock) {
        this.traineeService = traineeService;
        this.courseService = courseService;
        this.sessionService = sessionService;
        this.clock = clock;
    }

    @GetMapping("/")
    public String home(Model model) {
        LocalDate today = LocalDate.now(clock);
        model.addAttribute("today", today);
        model.addAttribute("activeTrainees", traineeService.countActive());
        model.addAttribute("courseCount", courseService.count());
        model.addAttribute("sessionsThisWeek", sessionService.countThisWeek(today));
        model.addAttribute("upcoming", sessionService.upcoming(today, 5));
        return "home";
    }
}
