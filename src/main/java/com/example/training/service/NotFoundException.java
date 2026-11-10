package com.example.training.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 指定 ID 的数据不存在 → 404 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, Long id) {
        super(what + "不存在（id=" + id + "）");
    }
}
