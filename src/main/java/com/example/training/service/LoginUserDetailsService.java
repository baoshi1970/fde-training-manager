package com.example.training.service;

import com.example.training.domain.AppUser;
import com.example.training.repository.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** 从 app_users 表读取登录用户 */
@Service
public class LoginUserDetailsService implements UserDetailsService {

    private final AppUserRepository repository;

    public LoginUserDetailsService(AppUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .build();
    }

    /** 画面右上角显示的名字 */
    public String displayNameOf(String username) {
        return repository.findByUsername(username).map(AppUser::getDisplayName).orElse(username);
    }
}
