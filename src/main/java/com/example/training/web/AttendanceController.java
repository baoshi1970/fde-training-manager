package com.example.training.web;

import com.example.training.domain.AttendanceStatus;
import com.example.training.service.AttendanceService;
import com.example.training.service.BusinessException;
import com.example.training.service.SessionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

/** F6 出勤登记：课次一览 → 选课次 → 给该课程的报名者逐人选状态 → 保存 */
@Controller
@RequestMapping("/attendance")
public class AttendanceController {

    private static final String PARAM_PREFIX = "status_";

    private final AttendanceService service;
    private final SessionService sessionService;

    public AttendanceController(AttendanceService service, SessionService sessionService) {
        this.service = service;
        this.sessionService = sessionService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("overviews", service.overview());
        return "attendance/list";
    }

    @GetMapping("/{sessionId}")
    public String form(@PathVariable Long sessionId, Model model) {
        model.addAttribute("courseSession", sessionService.get(sessionId));
        model.addAttribute("enrollments", service.findEnrollments(sessionId));
        model.addAttribute("statuses", service.findStatuses(sessionId));
        model.addAttribute("counts", service.countByStatus(sessionId));
        model.addAttribute("options", AttendanceStatus.values());
        return "attendance/form";
    }

    /** 表单字段名：status_{学员id}，值：PRESENT/LATE/ABSENT，空＝未记录 */
    @PostMapping("/{sessionId}")
    public String save(@PathVariable Long sessionId, @RequestParam Map<String, String> params,
                       RedirectAttributes ra) {
        try {
            service.save(sessionId, parse(params));
            ra.addFlashAttribute("message", "已保存出勤");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/attendance/" + sessionId;
    }

    private Map<Long, AttendanceStatus> parse(Map<String, String> params) {
        Map<Long, AttendanceStatus> result = new HashMap<>();
        params.forEach((key, value) -> {
            if (!key.startsWith(PARAM_PREFIX)) {
                return;
            }
            try {
                Long traineeId = Long.valueOf(key.substring(PARAM_PREFIX.length()));
                result.put(traineeId, value == null || value.isBlank() ? null : AttendanceStatus.valueOf(value));
            } catch (IllegalArgumentException e) {
                throw new BusinessException("提交的出勤数据不正确，请刷新页面后重试");
            }
        });
        return result;
    }
}
