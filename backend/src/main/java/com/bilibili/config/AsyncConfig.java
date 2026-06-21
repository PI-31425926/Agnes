package com.bilibili.config;

import com.bilibili.common.context.RequestContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean("taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("Async-");

        // 关键：任务装饰器，复制 ThreadLocal
        executor.setTaskDecorator(runnable -> {
            String currentUser = RequestContext.getCurrentUser();
            return () -> {
                try {
                    RequestContext.setCurrentUser(currentUser);
                    runnable.run();
                } finally {
                    RequestContext.clear();
                }
            };
        });

        executor.initialize();
        return executor;
    }
}
