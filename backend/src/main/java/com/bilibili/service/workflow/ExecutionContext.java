package com.bilibili.service.workflow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe variable store for workflow execution.
 * Supports dotted notation: ctx.put("n2", "refined_prompt", "value") → ctx.get("n2.refined_prompt")
 */
public class ExecutionContext {

    // nodeId -> fieldName -> value
    private final Map<String, Map<String, Object>> nodeOutputs = new ConcurrentHashMap<>();

    /**
     * Store a field value from a completed node.
     */
    public void put(String nodeId, String fieldName, Object value) {
        nodeOutputs.computeIfAbsent(nodeId, k -> new ConcurrentHashMap<>())
                   .put(fieldName, value);
    }

    /**
     * Store an entire output map from a node.
     */
    public void putAll(String nodeId, Map<String, Object> outputs) {
        nodeOutputs.computeIfAbsent(nodeId, k -> new ConcurrentHashMap<>())
                   .putAll(outputs);
    }

    /**
     * Get a value by dotted notation: "n2.refined_prompt"
     */
    public Object get(String reference) {
        int dot = reference.indexOf('.');
        if (dot < 0) {
            // Bare nodeId — return the whole map as string representation
            Map<String, Object> map = nodeOutputs.get(reference);
            return map != null ? map.toString() : null;
        }
        String nodeId = reference.substring(0, dot);
        String fieldName = reference.substring(dot + 1);
        Map<String, Object> nodeMap = nodeOutputs.get(nodeId);
        if (nodeMap == null) return null;
        return nodeMap.get(fieldName);
    }

    /**
     * Check if a reference can be resolved.
     */
    public boolean contains(String reference) {
        return get(reference) != null;
    }

    /**
     * Get all outputs for a node (for debugging / inspection).
     */
    public Map<String, Object> getNodeOutputs(String nodeId) {
        Map<String, Object> map = nodeOutputs.get(nodeId);
        return map != null ? Collections.unmodifiableMap(map) : Map.of();
    }

    /**
     * Get all outputs as a map of nodeId -> output fields (for auto-binding).
     */
    public Map<String, Map<String, Object>> getNodeOutputsMap() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : nodeOutputs.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        return result;
    }

    /**
     * Clear all state (for re-execution).
     */
    public void clear() {
        nodeOutputs.clear();
    }
}
