package com.example.training.web;

import com.example.training.config.DataInitializer;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 管理员专用操作（SecurityConfig 里限定 ADMIN 角色） */
@Controller
public class AdminController {

    private final DataInitializer dataInitializer;

    public AdminController(DataInitializer dataInitializer) {
        this.dataInitializer = dataInitializer;
    }

    /** 把业务数据恢复成种子数据（演示环境被改乱时用） */
    @PostMapping("/admin/reset")
    public String reset(RedirectAttributes ra) {
        dataInitializer.resetToSeed();
        ra.addFlashAttribute("message", "已重置为初始数据");
        return "redirect:/";
    }
}
