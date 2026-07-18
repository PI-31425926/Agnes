package com.bilibili.mapper;

import com.bilibili.pojo.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRepository extends JpaRepository<Workflow, Long> {
    List<Workflow> findByUserIdOrderByCreatedAtDesc(Long userId);
}
