package com.bilibili.mapper;

import com.bilibili.pojo.entity.WorkflowExecutionNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowExecutionNodeRepository extends JpaRepository<WorkflowExecutionNode, Long> {
    List<WorkflowExecutionNode> findByExecutionIdOrderByNodeIdAsc(Long executionId);
}
