package com.bilibili.handler;

import cn.dev33.satoken.exception.NotLoginException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SaTokenExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<String> handleNotLogin(NotLoginException e) {
        // 返回 401 状态码，前端自动处理跳转
        return ResponseEntity.status(401).body("未登录或 Token 无效");
    }
}