## ADDED Requirements

### Requirement: Conversation list management
The system SHALL allow users to create, switch, delete, and list conversations.

#### Scenario: Create new conversation
- **WHEN** user clicks "New Conversation" button
- **THEN** system creates a new conversation with auto-generated title (first user message) and switches to it

#### Scenario: List user's conversations
- **WHEN** user opens the chat page
- **THEN** system loads all conversations for the current user, ordered by creation time descending

#### Scenario: Switch to existing conversation
- **WHEN** user clicks a conversation in the sidebar
- **THEN** system loads that conversation's message history and displays it

#### Scenario: Delete conversation
- **WHEN** user clicks delete on a conversation
- **THEN** system removes the conversation and its associated Redis history data

### Requirement: Per-conversation message history
Each conversation SHALL maintain its own independent message history in Redis.

#### Scenario: Messages isolated by conversation
- **WHEN** user sends a message in conversation A
- **THEN** the message is stored under `chat:history:{userId}:{conversationId_A}`, not affecting conversation B

#### Scenario: Conversation history loads on switch
- **WHEN** user switches to a conversation
- **THEN** system loads only that conversation's messages from Redis

### Requirement: Auto-title generation
New conversations SHALL be auto-named based on the first user message.

#### Scenario: First message determines title
- **WHEN** a new conversation is created and the first message is sent
- **THEN** the conversation title is set to the first 20 characters of the user message
