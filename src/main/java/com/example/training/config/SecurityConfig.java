package com.example.training.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * 登录与访问控制。
 * 除登录页、静态资源（以及本地开发时的 H2 控制台）外，所有页面都需要登录。
 * （H2 控制台也注册了 Servlet，所以路径一律用 antMatcher 指定）
 */
@Configuration
public class SecurityConfig {

    /** 公开部署时为 false（见 Dockerfile） */
    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (h2ConsoleEnabled) {
            http
                .authorizeHttpRequests(auth -> auth.requestMatchers(PathRequest.toH2Console()).permitAll())
                // H2 控制台用 frame，且不带 CSRF token
                .csrf(csrf -> csrf.ignoringRequestMatchers(PathRequest.toH2Console()))
                .headers(h -> h.frameOptions(f -> f.sameOrigin()));
        }
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(antMatcher("/login"), antMatcher("/css/**")).permitAll()
                .requestMatchers(antMatcher("/admin/**")).hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutRequestMatcher(antMatcher("/logout"))
                .logoutSuccessUrl("/login?logout"));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
