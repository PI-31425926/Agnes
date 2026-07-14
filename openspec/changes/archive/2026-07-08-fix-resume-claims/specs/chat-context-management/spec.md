## MODIFIED Requirements

### Requirement: Sliding window via LTRIM
The chat history SHALL be maintained using Redis List with LTRIM for true sliding window semantics. When the message count exceeds the maximum, LTRIM trims the oldest messages atomically on the server side.

#### Scenario: History trimmed with LTRIM on save
- **WHEN** a new conversation pair is saved and the list length exceeds 20 elements
- **THEN** system calls LTRIM key 0 19 to keep only the newest 20 messages (10 rounds)

#### Scenario: History retrieved with LTRIM
- **WHEN** a user sends a message and history exists in Redis
- **THEN** system loads the last 20 messages via LRANGE and appends them to the conversation context

### Requirement: Message count — 10 rounds (20 messages)
The chat history SHALL keep the most recent 10 conversation rounds, i.e., 20 individual messages (10 user + 10 assistant).

#### Scenario: 10 rounds preserved
- **WHEN** more than 20 messages exist in history
- **THEN** system retains exactly the last 20 messages (10 rounds) and discards older ones

### Requirement: 30-minute TTL
Chat history keys SHALL expire after 30 minutes of inactivity.

#### Scenario: TTL auto-cleanup
- **WHEN** 30 minutes pass without a new message from a user
- **THEN** the Redis key `chat:history:{userId}` is automatically deleted
