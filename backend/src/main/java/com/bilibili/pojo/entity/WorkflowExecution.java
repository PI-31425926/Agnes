package com.bilibili.pojo.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_executions")
@Data
@NoArgsConstructor
public class WorkflowExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long workflowId;

    @Column(nullable = false, length = 20)
    private String status;   // PENDING / RUNNING / SUCCESS / FAILED / STOPPED

    @Column(columnDefinition = "TEXT")
    private String result;   // JSON: final output summary

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime completedAt;

    public WorkflowExecution(Long workflowId, String status) {
        this.workflowId = workflowId;
        this.status = status;
    }
}
