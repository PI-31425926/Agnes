package com.bilibili.service;

import com.bilibili.common.context.RequestContext;
import com.bilibili.mapper.OperationLogRepository;
import com.bilibili.pojo.entity.OperationLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LogService {

    @Autowired
    private OperationLogRepository logRepository;

    /**
     * 异步记录操作日志，避免阻塞主流程
     */
    @Async("taskExecutor")
    public void log(String operationType, String description, String params, String resultStatus, String resultDetail) {
        String username = getCurrentUsername(); // 自动从 RequestContext 获取
        System.out.println(username);
        OperationLog log = new OperationLog();
        log.setUsername(username);
        log.setOperationType(operationType);
        log.setDescription(description);
        log.setParams(params);
        log.setResultStatus(resultStatus);
        log.setResultDetail(resultDetail);
        log.setCreateTime(LocalDateTime.now());
        logRepository.save(log);
    }

    // 新增：显式传入 username 的重载
    @Async
    public void log(String operationType, String description, String params,
                    String resultStatus, String resultDetail, String username) {
        OperationLog log = new OperationLog();
        log.setUsername(username != null ? username : "anonymous");
        log.setOperationType(operationType);
        log.setDescription(description);
        log.setParams(params);
        log.setResultStatus(resultStatus);
        log.setResultDetail(resultDetail);
        log.setCreateTime(LocalDateTime.now());
        logRepository.save(log);
    }

    /**
     * 记录操作日志（带 IP）
     */
    @Async
    public void logWithIp(String operationType, String description, String params,
                          String resultStatus, String resultDetail, String ipAddress) {
        OperationLog log = new OperationLog();
        log.setUsername(getCurrentUsername());
        log.setOperationType(operationType);
        log.setDescription(description);
        log.setParams(params);
        log.setResultStatus(resultStatus);
        log.setResultDetail(resultDetail);
        log.setCreateTime(LocalDateTime.now());
        log.setIpAddress(ipAddress);
        logRepository.save(log);
    }

    // 新方法：允许显式指定用户名和IP（登录专用）
    @Async
    public void logLogin(String operationType, String description, String resultStatus,
                         String username, String ipAddress) {
        saveLog(operationType, description, null, resultStatus, null, username, ipAddress);
    }

    private void saveLog(String operationType, String description, String params,
                         String resultStatus, String resultDetail, String username, String ipAddress) {
        if (username == null) {
            username = getCurrentUsername();   // 从登录上下文获取
        }
        OperationLog log = new OperationLog();
        log.setUsername(username);
        log.setOperationType(operationType);
        log.setDescription(description);
        log.setParams(params);
        log.setResultStatus(resultStatus);
        log.setResultDetail(resultDetail);
        log.setCreateTime(LocalDateTime.now());
        log.setIpAddress(ipAddress);
        logRepository.save(log);
    }



    private String getCurrentUsername() {
        // 优先从 ThreadLocal 获取
        String user = RequestContext.getCurrentUser();
        if (user != null) return user;
        return "anonymous";
    }
}
