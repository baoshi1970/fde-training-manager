package com.example.training.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 「今天」统一从这个 Clock 取（不要直接写 LocalDate.now()）。
 * 测试时可以换成固定日期的 Clock。
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Tokyo"));
    }
}
