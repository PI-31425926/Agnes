## 1. Backend — Conversation CRUD

- [x] 1.1 Create `ConversationController.java` — REST API for conversation CRUD (list/create/switch/delete)
- [x] 1.2 Implement `listConversations(userId)` — return all conversations ordered by createdAt desc
- [x] 1.3 Implement `createConversation(userId, title)` — create new conversation, return Conversation DTO
- [x] 1.4 Implement `deleteConversation(userId, conversationId)` — delete conversation and clean up Redis history
- [x] 1.5 Create `ConversationDTO` — lightweight DTO for frontend (id, title, createdAt)

## 2. Backend — Redis History Isolation by Conversation

- [x] 2.1 Modify `AgnesService` methods to accept `conversationId` parameter
- [x] 2.2 Change Redis key from `chat:history:{userId}` to `chat:history:{userId}:{conversationId}`
- [x] 2.3 Update `getHistory()`, `saveHistory()` to use conversation-aware key
- [x] 2.4 Auto-generate conversation title from first user message

## 3. Frontend — Conversation Selector Bar

- [x] 3.1 Add conversation selector bar with "New Conversation" button
- [x] 3.2 Fetch and display conversation list from `/api/conversations`
- [x] 3.3 Click to switch conversation — load messages and update active conversationId
- [x] 3.4 Delete conversation with confirmation dialog
- [x] 3.5 Auto-scroll sidebar to active conversation on load

## 4. Frontend — Chat Integration with Conversation

- [x] 4.1 Pass conversationId in chat API calls (`/api/chat/stream`)
- [x] 4.2 Load conversation history on mount/switch
- [x] 4.3 Clear chatMessages when switching conversations
- [x] 4.4 Update sidebar title when first message is sent
