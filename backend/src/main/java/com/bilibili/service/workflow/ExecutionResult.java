package com.bilibili.service.workflow;

import java.util.Map;

/**
 * Result returned by a single node execution.
 */
public class ExecutionResult {

    private final String status;   // SUCCESS / FAILED
    private final Map<String, Object> output;
    private final String error;
    private final boolean async;   // true for long-running nodes (e.g. video)

    public ExecutionResult(String status, Map<String, Object> output, String error, boolean async) {
        this.status = status;
        this.output = output != null ? output : Map.of();
        this.error = error;
        this.async = async;
    }

    public static ExecutionResult success(Map<String, Object> output) {
        return new ExecutionResult("SUCCESS", output, null, false);
    }

    public static ExecutionResult failed(String error) {
        return new ExecutionResult("FAILED", Map.of(), error, false);
    }

    public static ExecutionResult pending(Map<String, Object> output) {
        return new ExecutionResult("RUNNING", output, null, true);
    }

    public String getStatus() { return status; }
    public Map<String, Object> getOutput() { return output; }
    public String getError() { return error; }
    public boolean isAsync() { return async; }
}
