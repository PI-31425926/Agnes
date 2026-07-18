package com.bilibili.service.workflow;

import cn.dev33.satoken.stp.StpUtil;
import com.bilibili.mapper.UserRepository;
import com.bilibili.mapper.WorkflowExecutionNodeRepository;
import com.bilibili.mapper.WorkflowExecutionRepository;
import com.bilibili.mapper.WorkflowRepository;
import com.bilibili.pojo.entity.User;
import com.bilibili.pojo.entity.WorkflowExecution;
import com.bilibili.pojo.entity.WorkflowExecutionNode;
import com.bilibili.pojo.entity.Workflow;
import com.bilibili.utils.AesUtil;
import com.bilibili.utils.VideoPollingScheduler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Core workflow execution engine.
 * Parses DAG from JSON, topologically sorts nodes, resolves variables,
 * executes synchronously (blocking) or asynchronously (video polling),
 * pushes WebSocket progress events, and persists execution state.
 */
@Service
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired private WorkflowRepository workflowRepo;
    @Autowired private WorkflowExecutionRepository executionRepo;
    @Autowired private WorkflowExecutionNodeRepository nodeRepo;
    @Autowired private ExecutorRegistry registry;
    @Autowired private AesUtil aesUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private VideoPollingScheduler videoPollingScheduler;

    // Thread pool for parallel node execution
    private final ExecutorService executorService = Executors.newFixedThreadPool(8);

    /**
     * Execute a workflow by ID.
     * Creates execution records, runs nodes in topological order, returns execution ID.
     */
    @Transactional
    public Long executeWorkflow(Long workflowId) {
        Workflow workflow = workflowRepo.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));

        // Create execution record
        WorkflowExecution execution = new WorkflowExecution(workflowId, "RUNNING");
        execution = executionRepo.save(execution);
        Long executionId = execution.getId();

        ExecutionContext ctx = new ExecutionContext();
        Map<String, WorkflowExecutionNode> nodeRecords = new ConcurrentHashMap<>();

        // Parse workflow JSON
        WorkflowDefinition def = parseDefinition(workflow.getDefinition());

        // Topological sort (detects cycles)
        List<String> executionOrder = topologicalSort(def.nodes, def.edges);

        // Create node records in DB
        for (String nodeId : executionOrder) {
            JsonNode nodeDef = def.nodes.get(nodeId);
            String nodeType = nodeDef.get("type").asText();
            WorkflowExecutionNode record = new WorkflowExecutionNode(executionId, nodeId, nodeType);
            record.setStatus("PENDING");
            record = nodeRepo.save(record);
            nodeRecords.put(nodeId, record);
        }

        // Build adjacency: nodeId -> set of upstream nodeIds
        Map<String, Set<String>> dependents = buildDependents(def.nodes, def.edges);

        // Execute nodes level by level (respecting dependencies)
        Set<String> completed = ConcurrentHashMap.newKeySet();
        Set<String> failed = ConcurrentHashMap.newKeySet();

        // Track async video tasks: nodeId -> (apiKey, config)
        Map<String, VideoTask> asyncTasks = new ConcurrentHashMap<>();

        try {
            executeLevel(executionId, executionOrder, dependents, completed, failed,
                    asyncTasks, ctx, nodeRecords, def.nodes, def.edgeMap);
        } catch (Exception e) {
            log.error("Workflow execution failed: executionId={}", executionId, e);
            failExecution(executionId, nodeRecords, "Workflow execution error: " + e.getMessage());
            throw e;
        }

        // Check if all completed or any failed
        if (!failed.isEmpty()) {
            failExecution(executionId, nodeRecords, "One or more nodes failed");
        } else {
            execution.setStatus("SUCCESS");
            execution.setCompletedAt(LocalDateTime.now());
            executionRepo.save(execution);
        }

        return executionId;
    }

    /**
     * Execute a single node within a workflow context.
     * Creates a minimal execution, runs just the specified node, returns output.
     */
    public Map<String, Object> executeSingleNode(Workflow workflow, String targetNodeId) {
        log.info("executeSingleNode: workflowId={}, nodeId={}", workflow.getId(), targetNodeId);

        WorkflowExecution execution = new WorkflowExecution(workflow.getId(), "RUNNING");
        execution = executionRepo.save(execution);
        Long executionId = execution.getId();
        log.info("executeSingleNode: created executionId={}", executionId);

        ExecutionContext ctx = new ExecutionContext();
        Map<String, WorkflowExecutionNode> nodeRecords = new ConcurrentHashMap<>();

        // Load previous successful node outputs into ctx for auto-binding (skip self)
        loadPreviousOutputsIntoContext(workflow.getId(), targetNodeId, ctx);

        WorkflowDefinition def = parseDefinition(workflow.getDefinition());
        log.info("executeSingleNode: parsed {} nodes", def.nodes.size());

        JsonNode nodeDef = def.nodes.get(targetNodeId);
        if (nodeDef == null) {
            throw new IllegalArgumentException("Node not found: " + targetNodeId);
        }

        String nodeType = nodeDef.get("type").asText();
        log.info("executeSingleNode: node type={}, data={}", nodeType, nodeDef.get("data"));

        WorkflowExecutionNode record = new WorkflowExecutionNode(executionId, targetNodeId, nodeType);
        record.setStatus("RUNNING");
        record.setStartedAt(LocalDateTime.now());
        record = nodeRepo.saveAndFlush(record);
        nodeRecords.put(targetNodeId, record);
        log.info("executeSingleNode: created nodeRecord id={}, nodeId={}", record.getId(), targetNodeId);

        try {
            // text_input: just return its prompt as output
            if ("text_input".equals(nodeType)) {
                log.info("executeSingleNode: text_input node, reading prompt from data");
                Map<String, Object> nodeOutput = new LinkedHashMap<>();
                JsonNode data = nodeDef.get("data");
                if (data != null && data.has("prompt")) {
                    String prompt = data.get("prompt").asText();
                    nodeOutput.put("prompt", prompt);
                    log.info("executeSingleNode: text_input prompt='{}'", prompt);
                } else {
                    log.warn("executeSingleNode: text_input has no prompt field in data");
                }
                record.setStatus("SUCCESS");
                record.setOutputJson(toJson(nodeOutput));
                record.setCompletedAt(LocalDateTime.now());
                nodeRepo.saveAndFlush(record);
                log.info("executeSingleNode: text_input saved, output={}", nodeOutput);
                ctx.putAll(targetNodeId, nodeOutput);
                return Map.of("executionId", executionId, "output", nodeOutput);
            }

            // text_chat, text_refine, etc.: resolve upstream context, then execute
            Map<String, Object> config = nodeConfigToMap(nodeDef);
            config = resolveAllReferences(ctx, config, targetNodeId, nodeType, def.edgeMap, def.nodes);
            String apiKey = getUserApiKey();
            config.put("api_key", apiKey);
            log.info("executeSingleNode: node {} config after resolve={}", targetNodeId, config);

            NodeExecutor executor = registry.get(nodeType);
            log.info("executeSingleNode: executing node {} with executor {}", targetNodeId, nodeType);
            ExecutionResult result = executor.execute(ctx, config);
            log.info("executeSingleNode: node {} initial result status={}, output={}", targetNodeId, result.getStatus(), result.getOutput());

            // For async nodes (video), poll until completion
            if (result.isAsync() && "RUNNING".equals(result.getStatus())) {
                String videoId = (String) result.getOutput().get("video_id");
                config.put("video_id", videoId);
                log.info("executeSingleNode: node {} is async (video_id={}), polling for completion...", targetNodeId, videoId);
                result = pollAsyncNodeToCompletion(executor, targetNodeId, apiKey, config);
                log.info("executeSingleNode: node {} polling completed, final status={}, output={}", targetNodeId, result.getStatus(), result.getOutput());
            }

            record.setOutputJson(toJson(result.getOutput()));
            record.setStatus(result.getStatus());
            record.setError(result.getError());
            record.setCompletedAt(LocalDateTime.now());
            nodeRepo.saveAndFlush(record);
            log.info("executeSingleNode: saved nodeRecord id={}, status={}", record.getId(), result.getStatus());

            execution.setStatus("SUCCESS".equals(result.getStatus()) ? "SUCCESS" : "FAILED");
            execution.setCompletedAt(LocalDateTime.now());
            executionRepo.save(execution);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("executionId", executionId);
            output.put("status", result.getStatus());
            output.put("output", result.getOutput());
            output.put("error", result.getError());
            return output;

        } catch (Exception e) {
            record.setStatus("FAILED");
            record.setError(e.getMessage());
            record.setCompletedAt(LocalDateTime.now());
            nodeRepo.saveAndFlush(record);

            execution.setStatus("FAILED");
            execution.setCompletedAt(LocalDateTime.now());
            executionRepo.save(execution);

            throw new RuntimeException("Node execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse workflow JSON into structured definition.
     */
    WorkflowDefinition parseDefinition(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            ArrayNode nodesArr = (ArrayNode) root.get("nodes");
            JsonNode edgesArr = root.get("edges");

            // Build node map: nodeId -> node JSON
            Map<String, JsonNode> nodes = new LinkedHashMap<>();
            for (JsonNode node : nodesArr) {
                String id = node.get("id").asText();
                String type = node.has("type") ? node.get("type").asText() : "unknown";
                nodes.put(id, node);
                log.info("parseDefinition: node {} has type='{}'", id, type);
            }

            // Build edge list and edge map (from -> list of to)
            List<Map<String, String>> edges = new ArrayList<>();
            Map<String, List<String>> edgeMap = new LinkedHashMap<>();
            if (edgesArr != null) {
                for (JsonNode edge : edgesArr) {
                    String source = edge.get("source").asText();
                    String target = edge.get("target").asText();
                    edges.add(Map.of("source", source, "target", target));
                    edgeMap.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
                }
            }

            return new WorkflowDefinition(nodes, edges, edgeMap);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid workflow JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Kahn's algorithm for topological sorting with cycle detection.
     */
    List<String> topologicalSort(Map<String, JsonNode> nodes, List<Map<String, String>> edges) {
        // Compute in-degree for each node
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adj = new LinkedHashMap<>(); // source -> targets

        for (String nodeId : nodes.keySet()) {
            inDegree.put(nodeId, 0);
            adj.put(nodeId, new ArrayList<>());
        }

        for (Map<String, String> edge : edges) {
            String from = edge.get("source");
            String to = edge.get("target");
            adj.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
            inDegree.merge(to, 1, Integer::sum);
        }

        // Start with nodes that have no incoming edges
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            order.add(current);
            for (String neighbor : adj.get(current)) {
                inDegree.merge(neighbor, -1, Integer::sum);
                if (inDegree.get(neighbor) == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        if (order.size() != nodes.size()) {
            throw new IllegalStateException("Workflow contains circular dependencies. "
                    + "Found " + (nodes.size() - order.size()) + " nodes in cycles.");
        }

        return order;
    }

    /**
     * Execute nodes level by level, respecting dependencies.
     * Nodes whose all predecessors are completed can run in parallel.
     */
    private void executeLevel(Long executionId, List<String> order,
                              Map<String, Set<String>> dependents,
                              Set<String> completed, Set<String> failed,
                              Map<String, VideoTask> asyncTasks,
                              ExecutionContext ctx,
                              Map<String, WorkflowExecutionNode> nodeRecords,
                              Map<String, JsonNode> nodes,
                              Map<String, List<String>> edgeMap) {

        Set<String> remaining = new LinkedHashSet<>(order);

        while (!remaining.isEmpty()) {
            // Find nodes whose all dependencies are satisfied
            List<String> readyNodes = new ArrayList<>();
            for (String nodeId : remaining) {
                Set<String> deps = dependents.get(nodeId);
                if (deps == null || deps.isEmpty() || completed.containsAll(deps)) {
                    readyNodes.add(nodeId);
                }
            }

            if (readyNodes.isEmpty()) {
                // Deadlock: remaining nodes have unsatisfied deps (shouldn't happen after topo sort)
                throw new IllegalStateException("Cannot make progress: remaining nodes have unsatisfied dependencies");
            }

            // Execute ready nodes in parallel (synchronous ones)
            List<Future<?>> futures = new ArrayList<>();
            List<String> asyncNodeIds = new ArrayList<>();

            for (String nodeId : readyNodes) {
                WorkflowExecutionNode record = nodeRecords.get(nodeId);
                JsonNode nodeDef = nodes.get(nodeId);
                Map<String, Object> config = nodeConfigToMap(nodeDef);

                // Resolve variable references in config (auto-binds from upstream)
                String nodeType = record.getNodeType();
                config = resolveAllReferences(ctx, config, nodeId, nodeType, edgeMap, nodes);

                // Get API key from user context
                String apiKey = getUserApiKey();

                // Store input
                record.setInputJson(toJson(config));
                record.setStatus("RUNNING");
                record.setStartedAt(LocalDateTime.now());
                nodeRepo.save(record);

                NodeExecutor executor;
                try {
                    executor = registry.get(record.getNodeType());
                } catch (IllegalArgumentException e) {
                    // text_input is a pure UI node — no executor needed.
                    // Store its prompt as output for downstream auto-binding.
                    log.info("Skipping UI-only node: {} (type={})", nodeId, record.getNodeType());
                    record.setStatus("SUCCESS");
                    // Extract prompt as the node's output
                    Map<String, Object> nodeOutput = new LinkedHashMap<>();
                    if (config.get("prompt") != null) {
                        nodeOutput.put("prompt", config.get("prompt"));
                    }
                    record.setOutputJson(toJson(nodeOutput));
                    record.setCompletedAt(LocalDateTime.now());
                    // Flush and clear persistence context to avoid optimistic lock
                    nodeRepo.saveAndFlush(record);
                    // Store in context for downstream auto-binding
                    ctx.putAll(nodeId, nodeOutput);
                    completed.add(nodeId);
                    continue;
                }

                if (executor.isAsync()) {
                    // Async node: submit task, poll in background
                    asyncNodeIds.add(nodeId);
                    asyncTasks.put(nodeId, new VideoTask(nodeId, apiKey, config, record));
                } else {
                    // Sync node: execute now
                    final WorkflowExecutionNode execRecord = record;
                    final Map<String, Object> execConfig = config;
                    final NodeExecutor execExecutor = executor;
                    Future<?> future = executorService.submit(() -> {
                        try {
                            ExecutionResult result = execExecutor.execute(ctx, execConfig);
                            completeNode(execRecord, result, ctx, nodeId);
                            if (result.getStatus().equals("FAILED")) {
                                failed.add(nodeId);
                            } else {
                                completed.add(nodeId);
                            }
                        } catch (Exception e) {
                            execRecord.setStatus("FAILED");
                            execRecord.setError(e.getMessage());
                            execRecord.setCompletedAt(LocalDateTime.now());
                            try { nodeRepo.saveAndFlush(execRecord); } catch (Exception ignored) { nodeRepo.save(execRecord); }
                            failed.add(nodeId);
                            log.error("Node {} failed: {}", nodeId, e.getMessage());
                        }
                    });
                    futures.add(future);
                }
            }

            // Wait for all sync nodes to complete
            for (Future<?> f : futures) {
                try {
                    f.get(120, TimeUnit.SECONDS);
                } catch (TimeoutException te) {
                    f.cancel(true);
                    log.error("Sync node execution timed out after 120s");
                } catch (Exception e) {
                    log.warn("Node execution error: {}", e.getMessage());
                }
            }

            // Poll async nodes for completion
            pollAsyncNodes(asyncTasks, asyncNodeIds, executionId, nodeRecords, completed, failed);

            // Remove processed nodes
            remaining.removeAll(readyNodes);
        }
    }

    /**
     * Poll async nodes (video) until they complete or timeout.
     */
    private void pollAsyncNodes(Map<String, VideoTask> asyncTasks, List<String> nodeIds,
                                 Long executionId,
                                 Map<String, WorkflowExecutionNode> nodeRecords,
                                 Set<String> completed, Set<String> failed) {
        int maxAttempts = 300; // 20s * 300 = 60 minutes max
        int attempt = 0;

        while (attempt < maxAttempts && !nodeIds.isEmpty()) {
            attempt++;
            try { Thread.sleep(20000); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }

            List<String> stillRunning = new ArrayList<>();
            for (String nodeId : nodeIds) {
                VideoTask task = asyncTasks.get(nodeId);
                if (task == null) continue;

                try {
                    NodeExecutor executor = registry.get(task.nodeRecord.getNodeType());
                    if (executor instanceof TextToVideoExecutor t2v) {
                        ExecutionResult result = t2v.poll(task.videoId, task.apiKey);
                        applyPollResult(task.nodeRecord, result, nodeId);
                    } else if (executor instanceof ImageToVideoExecutor i2v) {
                        ExecutionResult result = i2v.poll(task.videoId, task.apiKey);
                        applyPollResult(task.nodeRecord, result, nodeId);
                    } else if (executor instanceof KeyframeAnimationExecutor kf) {
                        ExecutionResult result = kf.poll(task.videoId, task.apiKey);
                        applyPollResult(task.nodeRecord, result, nodeId);
                    }

                    if (task.nodeRecord.getStatus().equals("RUNNING")) {
                        stillRunning.add(nodeId);
                    }
                } catch (Exception e) {
                    task.nodeRecord.setStatus("FAILED");
                    task.nodeRecord.setError("Polling error: " + e.getMessage());
                    task.nodeRecord.setCompletedAt(LocalDateTime.now());
                    nodeRepo.save(task.nodeRecord);
                    failed.add(nodeId);
                }
            }
            nodeIds.clear();
            nodeIds.addAll(stillRunning);
        }

        // Timeout: mark remaining as failed
        for (String nodeId : nodeIds) {
            VideoTask task = asyncTasks.get(nodeId);
            if (task != null) {
                task.nodeRecord.setStatus("FAILED");
                task.nodeRecord.setError("Video generation timed out (10 minutes)");
                task.nodeRecord.setCompletedAt(LocalDateTime.now());
                nodeRepo.save(task.nodeRecord);
                failed.add(nodeId);
            }
        }
    }

    private void applyPollResult(WorkflowExecutionNode record, ExecutionResult result, String nodeId) {
        record.setOutputJson(toJson(result.getOutput()));
        if ("SUCCESS".equals(result.getStatus())) {
            record.setStatus("SUCCESS");
            record.setCompletedAt(LocalDateTime.now());
        } else if ("FAILED".equals(result.getStatus())) {
            record.setStatus("FAILED");
            record.setError(result.getError());
            record.setCompletedAt(LocalDateTime.now());
        }
        // RUNNING: keep as-is
        nodeRepo.save(record);
    }

    private void completeNode(WorkflowExecutionNode record, ExecutionResult result,
                               ExecutionContext ctx, String nodeId) {
        record.setOutputJson(toJson(result.getOutput()));
        record.setStatus(result.getStatus());
        record.setError(result.getError());
        record.setCompletedAt(LocalDateTime.now());
        try {
            nodeRepo.saveAndFlush(record);
        } catch (Exception e) {
            nodeRepo.save(record);
        }

        // Store outputs in context for downstream nodes
        if ("SUCCESS".equals(result.getStatus())) {
            ctx.putAll(nodeId, result.getOutput());
        }
    }

    /**
     * Convert node JSON to a flat config map (extract data.fields).
     */
    Map<String, Object> nodeConfigToMap(JsonNode nodeDef) {
        // The node's actual type is stored in nodeDef.type (restored from rawType on save)
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("node_type", nodeDef.get("type").asText());
        JsonNode data = nodeDef.get("data");
        if (data != null && data.isObject()) {
            data.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if ("rawType".equals(key)) {
                    // Skip rawType — it's a frontend-only field
                    return;
                }
                if (!"label".equals(key) && !"position".equals(key)) {
                    config.put(key, toJsonSafe(entry.getValue()));
                }
            });
        }
        config.put("_node_id", nodeDef.get("id").asText());
        return config;
    }

    private Object toJsonSafe(JsonNode node) {
        if (node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(n -> list.add(toJsonSafe(n)));
            return list;
        }
        return node.toString();
    }

    /**
     * Resolve variable references in config. Also auto-bind from upstream nodes
     * when a config field is empty but upstream has a matching output field.
     */
    Map<String, Object> resolveAllReferences(ExecutionContext ctx, Map<String, Object> config, String nodeId, String nodeType,
                                              Map<String, List<String>> edgeMap, Map<String, JsonNode> nodes) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            // If it's a string with ${...} references, resolve them
            if (val instanceof String str && str.contains("${")) {
                resolved.put(key, resolveReferences(ctx, str));
            }
            // If it's a plain string that's empty/blank and upstream has a matching field, auto-bind
            else if (val instanceof String str && (str == null || str.isBlank()) && !"prompt".equals(key)) {
                // Skip empty non-prompt fields
                resolved.put(key, val);
            }
            else if (val instanceof String str && !str.contains("${")) {
                // Plain string without references — keep as-is
                resolved.put(key, str);
            }
            else {
                resolved.put(key, val);
            }
        }

        // Detect downstream node type for text_refine system prompt injection
        String downstreamType = getDirectDownstreamNodeType(nodeId, edgeMap, nodes);
        if ("text_refine".equals(nodeType) && downstreamType != null) {
            String sysPrompt = resolved.get("system_prompt") != null ? resolved.get("system_prompt").toString() : "";
            String optimizedSysPrompt = optimizeSystemPrompt(sysPrompt, downstreamType);
            if (optimizedSysPrompt != null && !optimizedSysPrompt.isBlank()) {
                resolved.put("system_prompt", optimizedSysPrompt);
                log.info("resolveAllReferences: injected optimized system_prompt for text_refine -> downstreamType={}", downstreamType);
            }
        }

        // Auto-bind prompt: only if prompt is empty/blank AND no explicit prompt was set
        String currentPrompt = resolved.get("prompt") != null ? resolved.get("prompt").toString() : "";
        boolean isTextInput = "text_input".equals(nodeType);
        boolean hasExplicitPrompt = resolved.containsKey("prompt") && currentPrompt != null && !currentPrompt.isBlank();
        log.info("resolveAllReferences: nodeId={}, nodeType={}, currentPrompt='{}', isTextInput={}, hasExplicitPrompt={}", nodeId, nodeType, currentPrompt, isTextInput, hasExplicitPrompt);
        if (!hasExplicitPrompt && !isTextInput) {
            // Look through completed nodes for a text output to bind
            Map<String, Map<String, Object>> outputsMap = ctx.getNodeOutputsMap();
            log.info("resolveAllReferences: ctx has {} upstream nodes: {}", outputsMap.size(), outputsMap);
            for (Map.Entry<String, Map<String, Object>> nodeEntry : outputsMap.entrySet()) {
                String upstreamId = nodeEntry.getKey();
                Map<String, Object> outputs = nodeEntry.getValue();
                // Try to find a text-like output field
                for (String fieldName : new String[]{"refined_prompt", "response", "description", "prompt"}) {
                    Object val = outputs.get(fieldName);
                    if (val != null && val.toString().trim().length() > 0) {
                        resolved.put("prompt", "${" + upstreamId + "." + fieldName + "}");
                        break;
                    }
                }
            }
        }

        // Auto-bind image_url: independent of prompt — image_to_image needs upstream image
        if (!resolved.containsKey("image_url")) {
            Map<String, Map<String, Object>> outputsMap = ctx.getNodeOutputsMap();
            for (Map.Entry<String, Map<String, Object>> nodeEntry : outputsMap.entrySet()) {
                String upstreamId = nodeEntry.getKey();
                Map<String, Object> outputs = nodeEntry.getValue();
                for (String fieldName : new String[]{"image_url", "url"}) {
                    Object val = outputs.get(fieldName);
                    if (val != null && val.toString().trim().length() > 0) {
                        resolved.put("image_url", "${" + upstreamId + "." + fieldName + "}");
                        log.info("resolveAllReferences: auto-bound image_url from {}.{}, value='{}'", upstreamId, fieldName, val);
                        break;
                    }
                }
                if (resolved.containsKey("image_url")) break;
            }
        }

        return resolved;
    }

    private Object resolveValue(ExecutionContext ctx, Object value) {
        if (value instanceof String str) {
            return resolveReferences(ctx, str);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(v -> resolveValue(ctx, v)).collect(Collectors.toList());
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), resolveValue(ctx, v)));
            return result;
        }
        return value;
    }

    private String resolveReferences(ExecutionContext ctx, String text) {
        if (text == null) return null;
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int start = text.indexOf("${", i);
            if (start < 0) {
                result.append(text.substring(i));
                break;
            }
            result.append(text.substring(i, start));
            int end = text.indexOf("}", start + 2);
            if (end < 0) {
                result.append("${");
                i = start + 2;
                continue;
            }
            String ref = text.substring(start + 2, end);
            Object resolved = ctx.get(ref);
            result.append(resolved != null ? resolved.toString() : "");
            i = end + 1;
        }
        return result.toString();
    }

    /**
     * Build a map of nodeId -> set of upstream nodeIds (dependents).
     * Used to track which nodes must complete before a node can run.
     */
    private Map<String, Set<String>> buildDependents(Map<String, JsonNode> nodes, List<Map<String, String>> edges) {
        Map<String, Set<String>> dependents = new HashMap<>();
        for (String nodeId : nodes.keySet()) {
            dependents.put(nodeId, new HashSet<>());
        }
        for (Map<String, String> edge : edges) {
            String from = edge.get("source");
            String to = edge.get("target");
            dependents.computeIfAbsent(to, k -> new HashSet<>()).add(from);
        }
        return dependents;
    }

    private void failExecution(Long executionId, Map<String, WorkflowExecutionNode> nodeRecords, String reason) {
        // Mark all RUNNING nodes as FAILED or SKIPPED
        for (WorkflowExecutionNode record : nodeRecords.values()) {
            if ("RUNNING".equals(record.getStatus()) || "PENDING".equals(record.getStatus())) {
                record.setStatus("SKIPPED");
                record.setError(reason);
                record.setCompletedAt(LocalDateTime.now());
                nodeRepo.save(record);
            }
        }

        // Update execution status
        WorkflowExecution execution = executionRepo.findById(executionId).orElse(null);
        if (execution != null) {
            execution.setStatus("FAILED");
            execution.setCompletedAt(LocalDateTime.now());
            executionRepo.save(execution);
        }
    }

    private String getUserApiKey() {
        String phone = StpUtil.getLoginIdAsString();
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + phone));
        try {
            return aesUtil.decryptLegacy(user.getApiKey());
        } catch (Exception e) {
            throw new RuntimeException("无法解密API密钥");
        }
    }

    /**
     * Poll an async node executor until it completes or times out.
     */
    private ExecutionResult pollAsyncNodeToCompletion(NodeExecutor executor, String nodeId, String apiKey, Map<String, Object> config) {
        int maxAttempts = 180; // 20s * 180 = 60 minutes max
        for (int i = 0; i < maxAttempts; i++) {
            try { Thread.sleep(20000); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            ExecutionResult result;
            if (executor instanceof TextToVideoExecutor t2v) {
                result = t2v.poll((String) config.get("video_id"), apiKey);
            } else if (executor instanceof ImageToVideoExecutor i2v) {
                result = i2v.poll((String) config.get("video_id"), apiKey);
            } else if (executor instanceof KeyframeAnimationExecutor kf) {
                result = kf.poll((String) config.get("video_id"), apiKey);
            } else {
                return ExecutionResult.failed("Unsupported async executor type: " + executor.nodeType());
            }
            log.info("pollAsyncNodeToCompletion: {} attempt={}, status={}", nodeId, i, result.getStatus());
            if (!"RUNNING".equals(result.getStatus())) {
                return result; // SUCCESS or FAILED
            }
        }
        return ExecutionResult.failed("Video generation timed out (60 minutes)");
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    /**
     * Get the type of the direct downstream node that is connected via an edge from this node.
     * Only returns the first connected downstream node's type (for single-output inference).
     */
    private String getDirectDownstreamNodeType(String nodeId, Map<String, List<String>> edgeMap, Map<String, JsonNode> nodes) {
        List<String> downstream = edgeMap.getOrDefault(nodeId, List.of());
        for (String downId : downstream) {
            JsonNode downDef = nodes.get(downId);
            if (downDef != null && downDef.has("type")) {
                return downDef.get("type").asText();
            }
        }
        return null;
    }

    /**
     * Optimize system prompt for text_refine based on downstream node type.
     */
    private String optimizeSystemPrompt(String userPrompt, String downstreamType) {
        if (userPrompt != null && !userPrompt.isBlank()) return userPrompt;

        switch (downstreamType) {
            case "text_to_image":
                return "你是一个专业的图像提示词优化师。用户会给你一段描述，你需要将其扩展为适合文生图的详细提示词。" +
                        "遵循结构：[主体] + [场景/环境] + [风格] + [光照] + [构图] + [质量要求]。" +
                        "输出简洁、高信息密度的中文提示词，不要有多余解释。";
            case "text_to_video":
                return "你是一个专业的视频提示词优化师。用户会给你一段描述，你需要将其扩展为适合文生视频的提示词。" +
                        "遵循结构：[主体] + [动作] + [场景] + [镜头运动] + [光线] + [风格]。" +
                        "输出简洁、生动的中文提示词，不要有多余解释。";
            case "image_to_image":
                return "你是一个专业的图像编辑提示词优化师。用户会给你修改要求，你需要将其扩展为适合图生图的详细指令。" +
                        "遵循结构：[改变要求] + [新风格/场景] + [需要添加或移除的元素] + [需要保留的元素]。" +
                        "输出简洁的中文提示词，不要有多余解释。";
            case "keyframe_animation":
                return "你是一个专业的关键帧动画提示词优化师。用户会给你动画描述，你需要将其扩展为适合关键帧动画的提示词。" +
                        "清晰描述关键帧之间的过渡关系，保持角色身份一致，镜头角度稳定，动作自然流畅。" +
                        "输出简洁的中文提示词，不要有多余解释。";
            default:
                return "你是一个专业的提示词优化师。将用户的简短描述扩展为详细、生动、富有画面感的提示词。" +
                        "只输出优化后的提示词，不要有多余解释。";
        }
    }

    /**
     * Load successful node outputs from previous executions of this workflow into the context.
     * This enables single-node execution to auto-bind from upstream nodes.
     */
    /**
     * Load successful node outputs from all executions of this workflow into the context.
     * Only loads nodes that are NOT the target node being executed (so the target can use upstream results).
     * Later executions overwrite earlier ones, so the latest value for each node is used.
     */
    private void loadPreviousOutputsIntoContext(Long workflowId, String targetNodeId, ExecutionContext ctx) {
        List<WorkflowExecution> executions = executionRepo.findByWorkflowIdOrderByCreatedAtAsc(workflowId);
        for (WorkflowExecution exec : executions) {
            List<WorkflowExecutionNode> nodes = nodeRepo.findByExecutionIdOrderByNodeIdAsc(exec.getId());
            for (WorkflowExecutionNode node : nodes) {
                if (targetNodeId.equals(node.getNodeId())) continue; // skip self
                if (!"SUCCESS".equals(node.getStatus())) continue;
                try {
                    Map<String, Object> output = mapper.readValue(node.getOutputJson(), Map.class);
                    ctx.putAll(node.getNodeId(), output);
                } catch (Exception e) {
                    // Ignore parse errors
                }
            }
        }
    }

    // --- Inner classes ---

    static class WorkflowDefinition {
        final Map<String, JsonNode> nodes;
        final List<Map<String, String>> edges;
        final Map<String, List<String>> edgeMap;
        WorkflowDefinition(Map<String, JsonNode> nodes, List<Map<String, String>> edges, Map<String, List<String>> edgeMap) {
            this.nodes = nodes;
            this.edges = edges;
            this.edgeMap = edgeMap;
        }
    }

    static class VideoTask {
        final String nodeId;
        final String apiKey;
        final Map<String, Object> config;
        final WorkflowExecutionNode nodeRecord;
        String videoId;
        VideoTask(String nodeId, String apiKey, Map<String, Object> config, WorkflowExecutionNode nodeRecord) {
            this.nodeId = nodeId;
            this.apiKey = apiKey;
            this.config = config;
            this.nodeRecord = nodeRecord;
        }
    }
}
