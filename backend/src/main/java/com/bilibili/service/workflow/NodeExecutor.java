package com.bilibili.service.workflow;

import java.util.Map;

/**
 * Contract for all AI capability executors in a workflow.
 * Each node type (text_refine, text_to_image, etc.) implements this interface.
 */
public interface NodeExecutor {

    /**
     * @return unique identifier for this node type (e.g. "llm_refine", "text_to_image")
     */
    String nodeType();

    /**
     * Execute this node with the given context and configuration.
     *
     * @param ctx     execution context containing upstream node outputs
     * @param config  node configuration (model, prompt, parameters, etc.)
     * @return result with output data, status, and async flag
     */
    ExecutionResult execute(ExecutionContext ctx, Map<String, Object> config);

    /**
     * @return true if this node is long-running and returns immediately with RUNNING status
     */
    boolean isAsync();
}
