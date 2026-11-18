package com.example.training.web;

import com.example.training.service.ProgressService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.LocalDate;

/** F9 学员进度看板（登录用户都能看） */
@Controller
@RequestMapping("/progress")
public class ProgressController {

    private final ProgressService service;
    private final Clock clock;

    public ProgressController(ProgressService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "false") boolean attention, Model model) {
        model.addAttribute("rows", service.board(attention));
        model.addAttribute("attention", attention);
        // 说明里写出出勤的统计截止日（和 Service 用同一个 Clock）
        model.addAttribute("today", LocalDate.now(clock));
        return "progress/list";
    }
}
