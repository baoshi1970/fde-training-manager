package com.example.training.service;

/**
 * 业务规则错误（重复、关联数据存在等）。
 * Controller 捕获后把 message 显示在画面上，不当作系统错误。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
