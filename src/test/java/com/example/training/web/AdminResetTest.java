package com.example.training.web;

import com.example.training.domain.Trainee;
import com.example.training.domain.TraineeStatus;
import com.example.training.repository.TraineeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminResetTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TraineeRepository traineeRepository;

    @Test
    @WithMockUser(username = "teacher", roles = "INSTRUCTOR")
    void 非管理员不能重置() throws Exception {
        mvc.perform(post("/admin/reset").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 管理员重置后恢复成种子数据() throws Exception {
        long seeded = traineeRepository.count();
        traineeRepository.save(new Trainee("测试 学员", "test.reset@example.com", null,
                TraineeStatus.ACTIVE, LocalDate.of(2026, 11, 1)));
        assertThat(traineeRepository.count()).isEqualTo(seeded + 1);

        mvc.perform(post("/admin/reset").with(csrf()))
                .andExpect(redirectedUrl("/"));

        assertThat(traineeRepository.count()).isEqualTo(seeded);
        assertThat(traineeRepository.existsByEmail("test.reset@example.com")).isFalse();
    }
}
