package com.example.training.web;

import com.example.training.service.ProgressService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** F9 学员进度看板（登录用户都能看） */
@Controller
@RequestMapping("/progress")
public class ProgressController {

    private final ProgressService service;

    public ProgressController(ProgressService service) {
        this.service = service;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "false") boolean attention, Model model) {
        model.addAttribute("rows", service.board(attention));
        model.addAttribute("attention", attention);
        return "progress/list";
    }
}
