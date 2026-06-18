package com.bilibili.pojo.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "operation_logs")
@Data
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;          // 操作者用户名（手机号）

    @Column(nullable = false)
    private String operationType;     // 操作类型：CHAT, IMAGE_GENERATION, IMAGE_TO_IMAGE, VIDEO_GENERATION

    @Column(length = 1000)
    private String description;       // 操作描述，如“对话输入前20字...”、“生成图片提示词...”

    @Column(length = 500)
    private String params;            // 可选，记录请求参数摘要

    private String resultStatus;      // SUCCESS / FAILED

    @Column(length = 1000)
    private String resultDetail;      // 结果摘要，如“图片URL”或错误信息

    @Column(nullable = false)
    private LocalDateTime createTime; // 操作时间

    private String ipAddress;         // 可选，操作IP
}
