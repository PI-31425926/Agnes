## 1. Fix Chat History — LTRIM and 20-message window

- [x] 1.1 Update `MAX_HISTORY_MESSAGES` from 10 to 20 in `AgnesService.java`
- [x] 1.2 Rewrite `saveHistory()` to use `rightPushAll` + `LTRIM key 0 19` instead of delete-then-replace
- [x] 1.3 Update the comment on `MAX_HISTORY_MESSAGES` to say "保留最近 10 轮（20条消息）"
- [x] 1.4 Update the Java-side sublist guard in `chat()` and `chatStreamReal()` to use the new constant 20

## 2. Fix Video Quota — Lua atomic check

- [x] 2.1 Define the Lua script string in `VideoTaskManager` for atomic SCARD+SADD quota check
- [x] 2.2 Replace the non-atomic `size()` + `add()` pattern in `addTask()` with `redisTemplate.execute()` + `DefaultRedisScript`
- [x] 2.3 Handle Lua return value: -1 → throw RuntimeException (triggers 429), 1 → continue
- [x] 2.4 Keep the subsequent `set`/`expire` calls after the atomic quota check (they are not critical for concurrency)

## 3. Fix Video Polling — SSCAN iteration

- [x] 3.1 Replace `opsForSet().members(PENDING_SET_KEY)` in `getPendingTasks()` with SSCAN via `redisTemplate.execute()` + `Cursor`
- [x] 3.2 Use `ScanOptions.scanOptions().count(100).build()` for batch size
- [x] 3.3 Loop cursor until exhausted, collecting pending task IDs
- [x] 3.4 Keep the rest of the logic (fetch task, filter completed/failed, cleanup) unchanged
