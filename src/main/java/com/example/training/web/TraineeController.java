package com.example.training.web;

import com.example.training.domain.Trainee;
import com.example.training.domain.TraineeStatus;
import com.example.training.service.BusinessException;
import com.example.training.service.CsvImportException;
import com.example.training.service.TraineeService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.InputStream;
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

    // ================= F10 CSV 导入 =================

    @GetMapping("/import")
    public String importForm() {
        return "trainees/import";
    }

    /**
     * 上传 CSV 并导入。有错误时回到上传画面，一条也不登记。
     * 读取上传文件失败（IOException）不是业务错误，交给共通错误页。
     */
    @PostMapping("/import")
    public String importCsv(@RequestParam(name = "file", required = false) MultipartFile file,
                            Model model, RedirectAttributes ra) throws IOException {
        // 没选文件时，浏览器发送的是文件名为空的 file。0 字节但选了文件的，交给 Service 报「文件是空的」
        if (file == null || !StringUtils.hasText(file.getOriginalFilename())) {
            model.addAttribute("error", "请选择 CSV 文件。");
            return "trainees/import";
        }
        int count;
        try (InputStream in = file.getInputStream()) {
            count = service.importCsv(in);
        } catch (CsvImportException e) {
            // 必须写在 BusinessException 前面：CsvImportException 是它的子类
            model.addAttribute("error", e.getMessage());
            model.addAttribute("errors", e.getErrors());
            return "trainees/import";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "trainees/import";
        }
        ra.addFlashAttribute("message", "已从 CSV 登记 " + count + " 名学员");
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
