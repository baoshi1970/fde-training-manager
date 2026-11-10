package com.example.training.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 画面级测试的范本：启动整个应用（H2＋种子数据），用 MockMvc 发请求 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginAndMenuTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 未登录访问主页_跳转到登录页() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void 正确的账号可以登录() throws Exception {
        mvc.perform(formLogin("/login").user("admin").password("admin123"))
                .andExpect(authenticated().withUsername("admin"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void 密码错误不能登录() throws Exception {
        mvc.perform(formLogin("/login").user("admin").password("wrong"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @WithMockUser(username = "admin")
    void 主页显示主菜单和实装状况() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("学员管理")))
                .andExpect(content().string(containsString("功能实装状况")))
                .andExpect(content().string(containsString("管理员")));
    }

    @Test
    @WithMockUser(username = "admin")
    void 学员一览显示种子数据() throws Exception {
        mvc.perform(get("/trainees"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("山田 太郎")));
    }

    @Test
    @WithMockUser(username = "admin")
    void 不存在的学员返回404() throws Exception {
        mvc.perform(get("/trainees/9999/edit"))
                .andExpect(status().isNotFound());
    }
}
