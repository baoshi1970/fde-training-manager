package com.example.training.web;

import com.example.training.domain.AttendanceStatus;
import com.example.training.domain.Course;
import com.example.training.service.AttendanceService;
import com.example.training.service.BusinessException;
import com.example.training.service.CourseService;
import com.example.training.service.SessionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;

/** F6 出勤登记 */
@Controller
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService service;
    private final SessionService sessionService;
    private final CourseService courseService;

    public AttendanceController(AttendanceService service, SessionService sessionService,
                                CourseService courseService) {
        this.service = service;
        this.sessionService = sessionService;
        this.courseService = courseService;
    }

    @ModelAttribute("courses")
    public List<Course> courses() {
        return courseService.findAll();
    }

    @ModelAttribute("attendanceStatuses")
    public AttendanceStatus[] attendanceStatuses() {
        return AttendanceStatus.values();
    }

    /** 出勤用的课次一览 */
    @GetMapping
    public String list(@RequestParam(required = false) Long courseId, Model model) {
        model.addAttribute("sessions", sessionService.findAll(courseId));
        model.addAttribute("courseId", courseId);
        return "attendance/list";
    }

    /** 某个课次的登记表单（显示已保存的状态） */
    @GetMapping("/sessions/{id}")
    public String form(@PathVariable Long id, Model model) {
        AttendanceForm form = new AttendanceForm();
        form.setStatuses(new HashMap<>(service.statusesOf(id)));
        fillModel(id, form, model);
        return "attendance/form";
    }

    @PostMapping("/sessions/{id}")
    public String save(@PathVariable Long id, @ModelAttribute("form") AttendanceForm form,
                       BindingResult binding, Model model, RedirectAttributes ra) {
        // 绕过画面直接提交了非法的状态值或学员 id：表单绑定失败，一条都不保存，回显已保存的状态
        if (binding.hasErrors()) {
            model.addAttribute("error", "提交的出勤状态不正确，请刷新页面后重新选择。");
            form.setStatuses(new HashMap<>(service.statusesOf(id)));
            fillModel(id, form, model);
            return "attendance/form";
        }
        try {
            service.save(id, form.getStatuses());
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            fillModel(id, form, model);
            return "attendance/form";
        }
        ra.addFlashAttribute("message", "已保存出勤");
        return "redirect:/attendance/sessions/" + id;
    }

    private void fillModel(Long id, AttendanceForm form, Model model) {
        model.addAttribute("courseSession", sessionService.get(id));
        model.addAttribute("enrollees", service.enrollees(id));
        model.addAttribute("cancelled", service.cancelledWithAttendance(id));
        model.addAttribute("form", form);
    }
}
