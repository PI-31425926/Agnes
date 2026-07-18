package com.bilibili.service.workflow;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry that maps nodeType strings to their executor implementations.
 * Auto-populated from all NodeExecutor Spring beans.
 */
@Component
public class ExecutorRegistry {

    private final Map<String, NodeExecutor> executors = new LinkedHashMap<>();

    @Autowired
    public ExecutorRegistry(List<NodeExecutor> executorBeans) {
        for (NodeExecutor executor : executorBeans) {
            executors.put(executor.nodeType(), executor);
        }
    }

    /**
     * Get executor for a node type. Throws if not found.
     */
    public NodeExecutor get(String nodeType) {
        NodeExecutor executor = executors.get(nodeType);
        if (executor == null) {
            throw new IllegalArgumentException("Unknown node type: " + nodeType
                    + ". Available types: " + executors.keySet());
        }
        return executor;
    }

    /**
     * Get all registered node types.
     */
    public Set<String> nodeTypes() {
        return Collections.unmodifiableSet(executors.keySet());
    }
}
