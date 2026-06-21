package com.bilibili.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.bilibili.common.context.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 登录校验 + 设置 RequestContext
        registry.addInterceptor(new SaInterceptor(handle -> {
                    StpUtil.checkLogin();
                    // 设置用户上下文
                    RequestContext.setCurrentUser(StpUtil.getLoginIdAsString());
                }))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/guest/**");

        // 2. 请求结束后清理 ThreadLocal
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                        Object handler, Exception ex) {
                RequestContext.clear();
            }
        }).addPathPatterns("/api/**");
    }
}