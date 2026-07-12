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
     * 截断过长的字符串，防止 MySQL TEXT 字段溢出（UTF-8 下 TEXT 最大 65535 字节）
     */
    private String truncate(String str, int maxBytes) {
        if (str == null) return null;
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return str;
        // 截断到 maxBytes，避免截断多字节字符中间
        int cut = Math.min(maxBytes, bytes.length);
        while (cut > 0 && (bytes[cut] & 0xC0) == 0x80) {
            cut--;  // 回退到多字节字符边界
        }
        return new String(bytes, 0, cut) + "...";
    }

    /**
     * 异步记录操作日志，避免阻塞主流程
     */
    @Async("taskExecutor")
    public void log(String operationType, String description, String params, String resultStatus, String resultDetail) {
        String username = getCurrentUsername();
        System.out.println(username);
        OperationLog log = new OperationLog();
        log.setUsername(username);
        log.setOperationType(operationType);
        log.setDescription(description);
        log.setParams(params);
        log.setResultStatus(resultStatus);
        log.setResultDetail(truncate(resultDetail, 65000));
        log.setCreateTime(LocalDateTime.now());
        logRepository.save(log);
    }

    @Async
    public void log(String operationType, String description, String params,
                    String resultStatus, String resultDetail, String username) {
        OperationLog log = new OperationLog();
        log.setUsername(username != null ? username : "anonymous");
        log.setOperationType(operationType);
        log.setDescription(description);
        log.setParams(params);
        log.setResultStatus(resultStatus);
        log.setResultDetail(truncate(resultDetail, 65000));
        log.setCreateTime(LocalDateTime.now());
        logRepository.save(log);
    }

    @Async
    public void logWithIp(String operationType, String description, String params,
                          String resultStatus, String resultDetail, String ipAddress) {
        OperationLog log = new OperationLog();
        log.setUsername(getCurrentUsername());
        log.setOperationType(operationType);
        log.setDescription(description);
        log.setParams(params);
        log.setResultStatus(resultStatus);
        log.setResultDetail(truncate(resultDetail, 65000));
        log.setCreateTime(LocalDateTime.now());
        log.setIpAddress(ipAddress);
        logRepository.save(log);
    }

    @Async
    public void logLogin(String operationType, String description, String resultStatus,
                         String username, String ipAddress) {
        saveLog(operationType, description, null, resultStatus, null, username, ipAddress);
    }

    private void saveLog(String operationType, String description, String params,
                         String resultStatus, String resultDetail, String username, String ipAddress) {
        if (username == null) {
            username = getCurrentUsername();
        }
        OperationLog log = new OperationLog();
        log.setUsername(username);
        log.setOperationType(operationType);
        log.setDescription(description);
        log.setParams(params);
        log.setResultStatus(resultStatus);
        log.setResultDetail(truncate(resultDetail, 65000));
        log.setCreateTime(LocalDateTime.now());
        log.setIpAddress(ipAddress);
        logRepository.save(log);
    }

    private String getCurrentUsername() {
        String user = RequestContext.getCurrentUser();
        if (user != null) return user;
        return "anonymous";
    }
}
