package com.bilibili.pojo.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_execution_nodes")
@Data
@NoArgsConstructor
public class WorkflowExecutionNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long executionId;

    @Column(nullable = false, length = 64)
    private String nodeId;

    @Column(nullable = false, length = 64)
    private String nodeType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String inputJson;

    @Column(columnDefinition = "TEXT")
    private String outputJson;

    @Column(length = 1000)
    private String error;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    public WorkflowExecutionNode(Long executionId, String nodeId, String nodeType) {
        this.executionId = executionId;
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.completedAt = LocalDateTime.now();
    }
}
