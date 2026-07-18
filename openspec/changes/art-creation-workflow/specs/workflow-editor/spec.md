## ADDED Requirements

### Requirement: Workflow Canvas
The system SHALL provide a visual canvas where users can create and edit workflow diagrams.
The canvas uses Vue Flow as the rendering engine.

#### Scenario: Open workflow editor
- **WHEN** user navigates to the workflow page (e.g., /workflow)
- **THEN** the system displays an empty canvas with a node palette sidebar

#### Scenario: Drag node from palette to canvas
- **WHEN** user drags a node type from the sidebar onto the canvas
- **THEN** a new node appears on the canvas at the drop position with a default label

#### Scenario: Reposition node on canvas
- **WHEN** user drags a node already on the canvas
- **THEN** the node moves with the cursor and snaps to the final position on release

#### Scenario: Connect two nodes
- **WHEN** user drags from an output handle of Node A to an input handle of Node B
- **THEN** a directed edge appears connecting the two nodes

#### Scenario: Delete node or edge
- **WHEN** user selects a node or edge and presses Delete or clicks a delete button
- **THEN** the selected element is removed from the canvas

### Requirement: Node Configuration Panel
The system SHALL provide a configuration panel when a node is selected.

#### Scenario: Open node config panel
- **WHEN** user clicks on a node on the canvas
- **THEN** a side panel opens showing configuration fields for that node type

#### Scenario: Configure LLM node parameters
- **WHEN** user configures an LLM node (refine/chat)
- **THEN** the panel shows fields for system prompt, temperature, max tokens, and the model to use

#### Scenario: Configure image generation parameters
- **WHEN** user configures a text-to-image or image-to-image node
- **THEN** the panel shows fields for prompt, size preset, aspect ratio, and quality

#### Scenario: Configure video generation parameters
- **WHEN** user configures a video node (text-to-video / image-to-video / keyframe)
- **THEN** the panel shows fields for prompt, resolution, frame count, frame rate, and image upload area

#### Scenario: Bind variable to node input
- **WHEN** a node config field contains a variable reference like `${n2.refined_prompt}`
- **THEN** the panel displays the resolved value and allows the user to edit or re-bind it

### Requirement: Workflow Save and Load
The system SHALL serialize the canvas state to JSON and persist it via the backend API.

#### Scenario: Save workflow
- **WHEN** user clicks "Save"
- **THEN** the system sends the workflow JSON (nodes, edges, viewport) to POST /api/workflows and stores it

#### Scenario: Load workflow into editor
- **WHEN** user selects an existing workflow from the list
- **THEN** the system loads the workflow JSON and renders it on the canvas with all nodes and edges positioned

#### Scenario: Export workflow as JSON
- **WHEN** user clicks "Export"
- **THEN** the system downloads a .json file containing the workflow definition

### Requirement: Workflow Execution from Editor
The system SHALL allow users to execute workflows directly from the editor.

#### Scenario: Trigger workflow execution
- **WHEN** user clicks "Run" on the editor toolbar
- **THEN** the system sends a request to POST /api/workflows/{id}/execute and begins watching WebSocket events

#### Scenario: Display execution progress on canvas
- **WHEN** a workflow is executing
- **THEN** nodes are visually highlighted (running/completed/failed) on the canvas in real time

#### Scenario: Show node output on hover
- **WHEN** a node has completed and the user hovers over it
- **THEN** a tooltip displays the node's output (e.g., generated image thumbnail, text result)

#### Scenario: Stop execution
- **WHEN** user clicks "Stop" during execution
- **THEN** the system cancels the execution and marks remaining nodes as cancelled

### Requirement: Node Palette
The system SHALL provide a categorized palette of available node types in the sidebar.

#### Scenario: Display node categories
- **WHEN** the editor loads
- **THEN** the palette shows categories: "Text" (Text Input, LLM Refine, Text Chat), "Image" (Text-to-Image, Image-to-Image, Image Understand), "Video" (Text-to-Video, Image-to-Video, Keyframe Animation)

#### Scenario: Drag-only from palette (not canvas)
- **WHEN** user tries to drag a node from the palette onto the canvas
- **THEN** a new node instance is created; dragging an existing canvas node only repositions it
