## ADDED Requirements

### Requirement: Workflow Definition CRUD
The system SHALL allow users to create, read, update, and delete workflow definitions.
Each workflow consists of nodes (AI capability instances) and edges (data flow connections).

#### Scenario: Create workflow
- **WHEN** user submits a workflow definition with nodes and edges via POST /api/workflows
- **THEN** the system saves the workflow to the database and returns a workflow ID

#### Scenario: List user workflows
- **WHEN** user requests GET /api/workflows
- **THEN** the system returns all workflows owned by the current user

#### Scenario: Get single workflow
- **WHEN** user requests GET /api/workflows/{id}
- **THEN** the system returns the workflow definition with all nodes and edges

#### Scenario: Delete workflow
- **WHEN** user requests DELETE /api/workflows/{id}
- **THEN** the system deletes the workflow and all associated execution records

### Requirement: Node Types
The system SHALL support the following node types, each representing an AI capability:

| Node Type | ID Prefix | Input | Output | Description |
|-----------|-----------|-------|--------|-------------|
| Text Input | `text_input` | prompt (string) | prompt (string) | User enters initial text |
| LLM Refine | `llm_refine` | prompt (string), config (object) | refined_prompt (string) | LLM optimizes/enhances the prompt |
| Text Chat | `text_chat` | prompt (string), config (object) | response (string) | Multi-turn chat via LLM |
| Text-to-Image | `text_to_image` | prompt (string), config (object) | image_url (string) | Generates image from text |
| Image-to-Image | `image_to_image` | image_url (string), prompt (string), config (object) | image_url (string) | Modifies image based on prompt |
| Image Understand | `image_understand` | image_url (string), prompt (string), config (object) | description (string) | LLM describes/analyzes an image |
| Text-to-Video | `text_to_video` | prompt (string), config (object) | video_url (string) | Generates video from text |
| Image-to-Video | `image_to_video` | image_url (string), prompt (string), config (object) | video_url (string) | Generates video from image |
| Keyframe Animation | `keyframe_animation` | image_urls (string[]), prompt (string), config (object) | video_url (string) | Creates animation from multiple images |

#### Scenario: Node config is validated
- **WHEN** user configures a node with parameters
- **THEN** the system validates required fields (model, dimensions, etc.) before allowing execution

#### Scenario: Node output connects to next node input
- **WHEN** user draws an edge from Node A's output to Node B's input
- **THEN** the system records the connection and resolves variable references during execution

### Requirement: Variable Resolution
The system SHALL support variable references in node configurations using `${nodeId.field}` syntax.
During execution, references are resolved from the execution context.

#### Scenario: Resolve variable from previous node
- **WHEN** a node config contains `${n2.refined_prompt}` and node n2 has completed with output `refined_prompt: "..."`
- **THEN** the system substitutes the reference with the actual value before executing the node

#### Scenario: Unresolved variable produces error
- **WHEN** a node config references a field that does not exist in upstream output
- **THEN** the system rejects execution with a clear error message indicating the missing field

### Requirement: Execution Engine
The system SHALL execute workflows by:
1. Parsing the DAG and computing topological order
2. Executing nodes in order, respecting dependencies
3. Passing output from each node into the execution context
4. Resolving variable references before each node execution
5. Pushing real-time status updates via WebSocket

#### Scenario: Execute linear workflow
- **WHEN** user triggers execution of a linear workflow (A → B → C)
- **THEN** the system executes A, then B with A's output, then C with B's output, in order

#### Scenario: Execute fan-out workflow
- **WHEN** a workflow has one node branching to multiple downstream nodes
- **THEN** the system executes all dependent nodes in parallel after the upstream node completes

#### Scenario: Handle node failure
- **WHEN** a node execution fails (API error, timeout, invalid input)
- **THEN** the system marks the node as FAILED, stops execution of downstream nodes, and returns the error via WebSocket

#### Scenario: Handle long-running nodes
- **WHEN** a node performs a long-running operation (e.g., video generation takes minutes)
- **THEN** the system does not block other nodes; progress is reported via WebSocket events

### Requirement: Execution Persistence
The system SHALL persist workflow execution state:
- `workflow_executions` table: one row per execution run (workflowId, status, createdAt, completedAt, result)
- `workflow_execution_nodes` table: one row per executed node (executionId, nodeId, status, input, output, error, startedAt, completedAt)

#### Scenario: Execution starts PENDING
- **WHEN** a workflow execution is initiated
- **THEN** a record is created in workflow_executions with status PENDING

#### Scenario: Node execution records progress
- **WHEN** each node completes execution
- **THEN** a record is created/updated in workflow_execution_nodes with input, output, and timing data

#### Scenario: Execution completes SUCCESS or FAILED
- **WHEN** all nodes are executed or a fatal error occurs
- **THEN** the workflow_execution record is updated to SUCCESS or FAILED with final result

### Requirement: WebSocket Progress Events
The system SHALL push real-time events to the frontend via WebSocket during workflow execution.

#### Scenario: Event — execution started
- **WHEN** a workflow execution begins
- **THEN** the system sends `{type: "execution_started", executionId, workflowId}`

#### Scenario: Event — node started
- **WHEN** a node begins execution
- **THEN** the system sends `{type: "node_started", executionId, nodeId, nodeName}`

#### Scenario: Event — node completed
- **WHEN** a node finishes execution
- **THEN** the system sends `{type: "node_completed", executionId, nodeId, nodeId, outputSummary}`

#### Scenario: Event — execution completed
- **WHEN** all nodes finish or execution fails
- **THEN** the system sends `{type: "execution_completed", executionId, status: "SUCCESS"|"FAILED", result: {...}}`
