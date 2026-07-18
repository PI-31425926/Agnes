# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when operating in this repository.

## Project Overview

Agnes AI Platform — a multi-modal AI application (chat, text-to-image, image-to-image, video generation) built with Vue 3 + Spring Boot, connected to the Agnes AI external API.

## Common Commands

**Backend (Spring Boot):**
```bash
cd backend
mvn spring-boot:run          # Run dev server (port 8080)
mvn package                   # Build jar (produces target/backend-0.0.1-SNAPSHOT.jar)
mvn test                      # Run tests
```

**Frontend (Vue 3 + Vite):**
```bash
cd frontend
npm install                   # Install dependencies
npm run dev                   # Dev server with proxy (/api -> localhost:8080)
npm run build                 # Build to dist/ (copy to backend/src/main/resources/static/)
```

**Full flow:** Run backend first, then frontend dev server. Frontend Vite proxies `/api` requests to the backend. For production, build the frontend and serve via Spring Boot.

## Architecture

**Backend** (`backend/src/main/java/com/bilibili/`):
- **Entry**: `BackendApplication.java`
- **Auth**: Sa-Token (not Spring Security) with JWT-style tokens. `SaTokenConfig` registers an interceptor that checks login on `/api/**` (excludes `/api/auth/**` and `/api/guest/**`). User identity stored in `RequestContext` (InheritableThreadLocal).
- **Controllers**: `AuthController` (login/register/logout), `ChatController` (chat/stream/history/upload), `ImageController` (text2img/img2img/history), `VideoController` (generate/tasks/delete), `GuestController` (anonymous chat), `AdminController` (user management/logs)
- **Services**: `AgnesService` (chat + streaming), `AgnesImageService`, `AgnesVideoService`, `UserService`, `LogService`
- **Entities**: `User` (phone, encrypted apiKey, role), `Conversation`, `OperationLog`
- **Data**: JPA + MySQL (auto-create tables via `ddl-auto: update`), Redis (chat history, file content temp storage)
- **External API**: Calls `apihub.agnes-ai.com` for chat (OpenAI-compatible SSE), image, and video generation
- **Config**: `application.yml` (dev) and `application-dev.yml` — contains DB/Redis URLs, API endpoints, AES/JWT secrets

**Frontend** (`frontend/src/`):
- **Routing**: `router/index.js` — `/` (MainView, auth required), `/login` (LoginView), `/guest` (GuestView), `/admin` (AdminView, ADMIN role required). Route guards check `localStorage.getItem('token')` and role.
- **Views**: `MainView.vue` (single file with chat/text2img/img2img/video tabs), `LoginView.vue`, `GuestView.vue`, `AdminView.vue`
- **API layer**: `api/request.js` (Axios instance with `/api` base URL + interceptors for auth token/401 redirect), plus modular API files (`auth.js`, `chat.js`, `image.js`, `video.js`, `admin.js`)
- **State**: `stores/user.js` (Pinia store for token/role), `localStorage` as primary auth persistence
- **Composables**: `useTts.js` (SpeechSynthesis buffering/queue), `useImageHistory.js`
- **Components**: Organized by feature (`chat/`, `image/`, `video/`, `admin/`, `common/`)
- **Style**: Dark sci-fi theme with cyan (#0ff) accents, shared in `styles/common.css`, mostly scoped styles within views

**Key patterns:**
- Frontend stores `token` and `role` in `localStorage`; Axios interceptor adds `Bearer` header automatically.
- Backend uses `RequestContext` (ThreadLocal) for user identity across async layers.
- Chat history stored in Redis lists (`chat:history:{phone}`), TTL 30 min, max 10 rounds.
- Video tasks use polling (client-side `setInterval` + server-side `VideoPollingScheduler`).
- File uploads: documents parsed via Apache Tika, content stored in Redis temp key (`file:content:{userId}`).
