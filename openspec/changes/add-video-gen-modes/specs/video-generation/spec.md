## ADDED Requirements

### Requirement: Support text-to-video generation
The system SHALL support text-to-video generation using the Agnes Video V2.0 API (`agnes-video-v2.0` model). Users provide a text prompt and optional parameters (width, height, num_frames, frame_rate), and the system creates an asynchronous video task.

#### Scenario: Successful text-to-video task creation
- **WHEN** user selects "文生视频" mode, enters a prompt and parameters, and clicks submit
- **THEN** the system calls the Agnes API with `model`, `prompt`, `width`, `height`, `num_frames`, `frame_rate` and returns a task_id and video_id

#### Scenario: Invalid num_frames rejected
- **WHEN** user submits with num_frames that exceeds 441 or does not follow the 8n+1 rule
- **THEN** the system returns a 400 error with a descriptive message before calling the upstream API

### Requirement: Support image-to-video generation
The system SHALL support image-to-video generation. Users upload a source image (converted to a public URL via Alibaba Cloud OSS), provide a text prompt describing the video motion, and the system sends the image URL via the `image` field to the Agnes API.

#### Scenario: Successful image-to-video task creation
- **WHEN** user selects "图生视频" mode, uploads an image, enters a prompt, and clicks submit
- **THEN** the system uploads the image to OSS, obtains a public URL, and calls the Agnes API with `model`, `prompt`, `image` (URL string), and optional parameters

#### Scenario: Image upload fails
- **WHEN** the image upload to OSS fails
- **THEN** the system displays an error message and does not proceed with video generation

### Requirement: Support keyframe animation generation
The system SHALL support keyframe animation generation. Users upload multiple source images (converted to public URLs via Alibaba Cloud OSS), provide a text prompt describing the transition, and the system sends the image URLs via `extra_body.image` to the Agnes API with `extra_body.mode` set to `"keyframes"`.

#### Scenario: Successful keyframe animation task creation
- **WHEN** user selects "关键帧动画" mode, uploads multiple images, enters a prompt, and clicks submit
- **THEN** the system uploads all images to OSS, obtains public URLs, and calls the Agnes API with `model`, `prompt`, `extra_body.image` (URL array), `extra_body.mode: "keyframes"`, and optional parameters

#### Scenario: Keyframe image count limit enforced
- **WHEN** user uploads more than 10 images in keyframe mode
- **THEN** the system prevents uploading additional images and displays a limit message

### Requirement: Video generation mode selector
The system SHALL provide a mode selector in the video generation UI with three options: "文生视频" (text-to-video), "图生视频" (image-to-video), and "关键帧动画" (keyframes). The default mode is "文生视频".

#### Scenario: Mode selection changes UI
- **WHEN** user changes the mode selector from "文生视频" to "图生视频" or "关键帧动画"
- **THEN** the UI shows the appropriate image upload area (single image for image-to-video, multiple images for keyframes)

### Requirement: Image upload for video generation
The system SHALL provide an image upload endpoint for video generation that uploads images to Alibaba Cloud OSS and returns public URLs. The endpoint supports single and multiple file uploads.

#### Scenario: Single image upload returns URL
- **WHEN** a user uploads a single image via `/api/video/upload-image`
- **THEN** the system returns a JSON response containing the public OSS URL of the uploaded image

#### Scenario: Multiple image upload returns URL array
- **WHEN** a user uploads multiple images via `/api/video/upload-images`
- **THEN** the system returns a JSON response containing an array of public OSS URLs

#### Scenario: Unsupported image format rejected
- **WHEN** a user uploads a file that is not jpg/png/webp
- **THEN** the system returns a 400 error with an appropriate message

### Requirement: Asynchronous task management
The system SHALL manage video generation tasks asynchronously. Task creation returns immediately with a task_id/video_id, and task progress is pushed via WebSocket. The existing polling fallback mechanism is retained.

#### Scenario: Task status updates via WebSocket
- **WHEN** a video generation task progresses (queued → processing → completed/failed)
- **THEN** the backend sends WebSocket messages to the frontend with updated status and progress

#### Scenario: Completed task notification
- **WHEN** a video generation task completes successfully
- **THEN** the frontend displays the generated video and shows a browser notification if permitted
