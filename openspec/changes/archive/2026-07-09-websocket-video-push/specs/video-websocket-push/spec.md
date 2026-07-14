## ADDED Requirements

### Requirement: WebSocket connection for video status
The system SHALL provide a WebSocket endpoint `/api/ws/video` for authenticated users to receive real-time video status updates.

#### Scenario: User connects to WebSocket
- **WHEN** an authenticated user navigates to the video tab
- **THEN** the frontend opens a WebSocket connection to `/api/ws/video` with a Bearer token in the URL query parameter

#### Scenario: Connection rejection for unauthenticated users
- **WHEN** an unauthenticated user attempts to connect to `/api/ws/video`
- **THEN** the server closes the connection with status 401

### Requirement: Video status push on update
When the video polling scheduler detects a status change, it SHALL push the update to the connected WebSocket client for that user.

#### Scenario: Status change pushed to client
- **WHEN** `VideoPollingScheduler` detects a task status changed from `processing` to `completed`
- **THEN** the server sends a `video_status` WebSocket message with the updated `VideoTaskInfo` payload to the user's WebSocket session

#### Scenario: Completion notification pushed to client
- **WHEN** a video task transitions to `completed` status with a valid URL
- **THEN** the server sends a `video_completed` WebSocket message with the video URL, enabling the frontend to display the video player immediately

### Requirement: Client reconnect and fallback
The frontend SHALL automatically reconnect the WebSocket connection and fall back to HTTP polling when WebSocket is unavailable.

#### Scenario: Automatic reconnection on disconnect
- **WHEN** the WebSocket connection is closed unexpectedly
- **THEN** the frontend waits 3 seconds and attempts to reconnect, up to a maximum of 5 retries

#### Scenario: HTTP polling fallback
- **WHEN** the WebSocket connection fails after max retries
- **THEN** the frontend falls back to HTTP GET `/api/video/tasks` polling every 30 seconds until WebSocket reconnects successfully

### Requirement: Message format
WebSocket messages SHALL follow a consistent JSON format.

#### Scenario: Status update message format
- **WHEN** the server pushes a video status update
- **THEN** the message is a JSON object: `{"type": "video_status", "data": {...VideoTaskInfo...}}`

#### Scenario: Completion message format
- **WHEN** the server pushes a video completion
- **THEN** the message is a JSON object: `{"type": "video_completed", "data": {...VideoTaskInfo...}}`
