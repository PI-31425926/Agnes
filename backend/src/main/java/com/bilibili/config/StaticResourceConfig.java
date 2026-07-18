package com.bilibili.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // SPA fallback: all non-/api/** paths serve index.html
        registry.addViewController("{spring:[^\\.]*}")
                .setViewName("forward:/index.html");
    }
}
