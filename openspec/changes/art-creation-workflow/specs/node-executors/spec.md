## ADDED Requirements

### Requirement: NodeExecutor Interface
All AI capability executors SHALL implement a common interface:

```
interface NodeExecutor {
    String nodeType();                          // returns the node type identifier
    ExecutionResult execute(ExecutionContext ctx, NodeConfig config);  // executes the node
    boolean isAsync();                          // true if the node is long-running (video)
}
```

#### Scenario: Executor registers itself
- **WHEN** the application starts
- **THEN** each executor is registered as a Spring Bean and discoverable by nodeType()

#### Scenario: Unknown node type produces error
- **WHEN** the execution engine encounters a node type with no registered executor
- **THEN** the system throws an exception and marks the node as FAILED with "Unknown node type"

### Requirement: ExecutionContext
The system SHALL provide a thread-safe execution context that stores variables during workflow execution.

#### Scenario: Put and get context variables
- **WHEN** a node completes and writes its output to the context
- **THEN** downstream nodes can read those variables via context.get(nodeId.field)

#### Scenario: Context is isolated per execution
- **WHEN** two workflows execute simultaneously
- **THEN** each execution has its own isolated context; variables do not leak between executions

### Requirement: LLM Refine Executor
The LLM Refine executor SHALL call the chat API with a system prompt designed to improve/enhance user input.

#### Scenario: Refine a text prompt
- **WHEN** a LLM_REFINE node receives input prompt "cat sitting on roof"
- **THEN** the executor calls the chat API with a refinement system prompt and returns enriched text

#### Scenario: Use configurable system prompt
- **WHEN** the node config contains a custom system_prompt field
- **THEN** the executor uses that as the system prompt instead of the default

### Requirement: Image Executor
Image executors (TEXT_TO_IMAGE, IMAGE_TO_IMAGE) SHALL call the existing image generation API.

#### Scenario: Generate image from text
- **WHEN** a TEXT_TO_IMAGE node receives a prompt
- **THEN** the executor calls the image API and returns the image URL

#### Scenario: Modify image with prompt
- **WHEN** an IMAGE_TO_IMAGE node receives an image URL and a prompt
- **THEN** the executor calls the image-to-image API and returns the modified image URL

### Requirement: Image Understand Executor
The IMAGE_UNDERSTAND executor SHALL call the chat API with vision capability.

#### Scenario: Describe an image
- **WHEN** an IMAGE_UNDERSTAND node receives an image URL
- **THEN** the executor calls the chat API with the image and a description prompt, returning text

### Requirement: Video Executors
Video executors (TEXT_TO_VIDEO, IMAGE_TO_VIDEO, KEYFRAME_ANIMATION) SHALL call the existing video API and manage async polling.

#### Scenario: Create video task
- **WHEN** a TEXT_TO_VIDEO node receives a prompt
- **THEN** the executor creates a video task via the API, stores the videoId in context, and returns PENDING status

#### Scenario: Poll video progress
- **WHEN** a video task is in PENDING status
- **THEN** the system polls the video status API every 20 seconds and updates the execution record

#### Scenario: Return video URL on success
- **WHEN** the video task completes with SUCCESS status
- **THEN** the executor updates the node result with the video download URL and marks the node as COMPLETED

#### Scenario: Timeout on video generation
- **WHEN** a video task exceeds the maximum polling time (10 minutes)
- **THEN** the executor marks the node as FAILED with "Video generation timed out"
