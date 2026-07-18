package com.bilibili.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.bilibili.pojo.dto.ApiResponse;
import com.bilibili.pojo.entity.Workflow;
import com.bilibili.pojo.entity.WorkflowExecution;
import com.bilibili.pojo.entity.WorkflowExecutionNode;
import com.bilibili.service.workflow.WorkflowEngine;
import com.bilibili.mapper.WorkflowRepository;
import com.bilibili.mapper.WorkflowExecutionRepository;
import com.bilibili.mapper.WorkflowExecutionNodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    @Autowired private WorkflowRepository workflowRepo;
    @Autowired private WorkflowExecutionRepository executionRepo;
    @Autowired private WorkflowExecutionNodeRepository nodeRepo;
    @Autowired private WorkflowEngine engine;

    @PostMapping
    public ApiResponse<Long> createWorkflow(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String definition = body.get("definition") != null ? toString(body.get("definition")) : "{}";
        if (name == null || name.isBlank()) {
            return ApiResponse.error(400, "Workflow name is required");
        }

        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
        Workflow workflow = new Workflow(userId, name, definition);
        workflow = workflowRepo.save(workflow);
        return ApiResponse.success(workflow.getId());
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listWorkflows() {
        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
        List<Workflow> workflows = workflowRepo.findByUserIdOrderByCreatedAtDesc(userId);
        return ApiResponse.success(workflows.stream().map(w -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", w.getId());
            map.put("name", w.getName());
            map.put("createdAt", w.getCreatedAt());
            map.put("updatedAt", w.getUpdatedAt());
            return map;
        }).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getWorkflow(@PathVariable Long id) {
        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
        Workflow workflow = workflowRepo.findById(id)
                .filter(w -> w.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", workflow.getId());
        result.put("name", workflow.getName());
        result.put("definition", workflow.getDefinition());
        result.put("createdAt", workflow.getCreatedAt());
        result.put("updatedAt", workflow.getUpdatedAt());

        return ApiResponse.success(result);
    }

    @GetMapping("/executions/latest/{id}")
    public ApiResponse<Map<String, Object>> getLatestExecutionResults(@PathVariable Long id) {
        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
        Workflow workflow = workflowRepo.findById(id)
                .filter(w -> w.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));

        // Get the latest successful execution
        List<WorkflowExecution> executions = executionRepo.findByWorkflowIdOrderByCreatedAtDesc(workflow.getId());
        Map<String, Object> nodeResults = new LinkedHashMap<>();

        for (WorkflowExecution exec : executions) {
            if (!"SUCCESS".equals(exec.getStatus())) continue;
            List<WorkflowExecutionNode> nodes = nodeRepo.findByExecutionIdOrderByNodeIdAsc(exec.getId());
            for (WorkflowExecutionNode node : nodes) {
                if (!"SUCCESS".equals(node.getStatus())) continue;
                Map<String, Object> nr = new LinkedHashMap<>();
                nr.put("_executionStatus", node.getStatus());
                try {
                    Map<String, Object> output = new com.fasterxml.jackson.databind.ObjectMapper().readValue(node.getOutputJson(), Map.class);
                    nr.put("_output", output);
                } catch (Exception e) {
                    nr.put("_output", Map.of());
                }
                nodeResults.put(node.getNodeId(), nr);
            }
            break; // only use the latest successful execution
        }

        return ApiResponse.success(nodeResults);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> updateWorkflow(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
        Workflow workflow = workflowRepo.findById(id)
                .filter(w -> w.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));

        String name = (String) body.get("name");
        String definition = body.get("definition") != null ? toString(body.get("definition")) : workflow.getDefinition();
        if (name != null && !name.isBlank()) workflow.setName(name);
        if (definition != null) workflow.setDefinition(definition);

        workflowRepo.save(workflow);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteWorkflow(@PathVariable Long id) {
        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
        Workflow workflow = workflowRepo.findById(id)
                .filter(w -> w.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));

        // Delete all associated executions
        List<WorkflowExecution> executions = executionRepo.findByWorkflowIdOrderByCreatedAtDesc(id);
        for (WorkflowExecution exec : executions) {
            Long execId = exec.getId();
            nodeRepo.findByExecutionIdOrderByNodeIdAsc(execId).forEach(nodeRepo::delete);
            executionRepo.deleteById(execId);
        }

        // Delete the workflow itself
        workflowRepo.delete(workflow);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/execute")
    public ApiResponse<Map<String, Object>> executeWorkflow(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
        Workflow workflow = workflowRepo.findById(id)
                .filter(w -> w.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));

        // Single node execution mode
        String nodeId = body != null ? (String) body.get("nodeId") : null;
        if (nodeId != null && !nodeId.isBlank()) {
            try {
                Map<String, Object> result = engine.executeSingleNode(workflow, nodeId);
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("executionId", result.get("executionId"));
                resp.put("output", result.get("output"));
                return ApiResponse.success(resp);
            } catch (Exception e) {
                return ApiResponse.error(500, "Node execution failed: " + e.getMessage());
            }
        }

        // Full workflow execution mode
        try {
            Long executionId = engine.executeWorkflow(id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("executionId", executionId);
            result.put("status", "RUNNING");
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(500, "Execution failed: " + e.getMessage());
        }
    }

    @GetMapping("/executions/{executionId}")
    public ApiResponse<Map<String, Object>> getExecution(@PathVariable Long executionId) {
        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());

        WorkflowExecution execution = executionRepo.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found"));

        // Verify ownership via workflow
        Workflow workflow = workflowRepo.findById(execution.getWorkflowId())
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));
        if (!workflow.getUserId().equals(userId)) {
            return ApiResponse.error(403, "Access denied");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", execution.getId());
        result.put("workflowId", execution.getWorkflowId());
        result.put("status", execution.getStatus());
        result.put("result", execution.getResult());
        result.put("createdAt", execution.getCreatedAt());
        result.put("completedAt", execution.getCompletedAt());

        List<WorkflowExecutionNode> nodes = nodeRepo.findByExecutionIdOrderByNodeIdAsc(executionId);
        List<Map<String, Object>> nodeResults = nodes.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodeId", n.getNodeId());
            m.put("nodeType", n.getNodeType());
            m.put("status", n.getStatus());
            m.put("inputJson", n.getInputJson());
            m.put("outputJson", n.getOutputJson());
            m.put("error", n.getError());
            m.put("startedAt", n.getStartedAt());
            m.put("completedAt", n.getCompletedAt());
            return m;
        }).collect(Collectors.toList());
        result.put("nodes", nodeResults);

        return ApiResponse.success(result);
    }

    @PostMapping("/{id}/stop")
    public ApiResponse<Void> stopWorkflow(@PathVariable Long id) {
        // TODO: Implement actual stop logic with running execution tracking
        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
        Workflow workflow = workflowRepo.findById(id)
                .filter(w -> w.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));
        return ApiResponse.success(null);
    }

    private String toString(Object obj) {
        if (obj instanceof String s) return s;
        if (obj instanceof Map || obj instanceof List) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
            } catch (Exception e) {
                return obj.toString();
            }
        }
        return String.valueOf(obj);
    }
}
