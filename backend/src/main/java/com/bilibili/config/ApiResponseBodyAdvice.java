package com.bilibili.config;

import com.bilibili.pojo.dto.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        // 如果已经是 ApiResponse，直接返回
        if (body instanceof ApiResponse) {
            return body;
        }
        // 如果是 ResponseEntity，直接返回（异常处理器等已自行控制 HTTP 状态码）
        if (body instanceof org.springframework.http.ResponseEntity) {
            return body;
        }
        // 如果是 null，返回 ApiResponse.success(null)
        if (body == null) {
            return ApiResponse.success(null);
        }
        // 否则自动包装
        return ApiResponse.success(body);
    }
}
