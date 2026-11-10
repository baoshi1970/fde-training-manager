package com.example.training.web;

import com.example.training.service.LoginUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/** 所有画面共用的模型：菜单、当前路径、登录者名 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final LoginUserDetailsService userService;

    public GlobalModelAdvice(LoginUserDetailsService userService) {
        this.userService = userService;
    }

    @ModelAttribute("features")
    public List<Feature> features() {
        return Feature.ALL;
    }

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ModelAttribute("loginName")
    public String loginName(Authentication auth) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return userService.displayNameOf(auth.getName());
    }
}
