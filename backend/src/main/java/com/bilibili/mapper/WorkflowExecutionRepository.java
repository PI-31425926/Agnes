package com.bilibili.mapper;

import com.bilibili.pojo.entity.WorkflowExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Long> {
    List<WorkflowExecution> findByWorkflowIdOrderByCreatedAtDesc(Long workflowId);
    List<WorkflowExecution> findByWorkflowIdOrderByCreatedAtAsc(Long workflowId);
}
