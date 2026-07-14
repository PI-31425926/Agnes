## 1. Fix Chat History Loading (Frontend)

- [x] 1.1 In `MainView.vue` onMounted, change `res.data && res.data.length > 0` to `res && res.length > 0`
- [x] 1.2 In `MainView.vue` onMounted, change `res.data.forEach(msg => ...)` to `res.forEach(msg => ...)`

## 2. Fix Image History Loading (Frontend)

- [x] 2.1 In `MainView.vue` loadImageHistory, change `if (res.data)` to `if (res)`
- [x] 2.2 Assign `imageHistory.value = res` instead of `imageHistory.value = res.data`

## 3. Fix Chat History Saving (Backend — True LTRIM Sliding Window)

- [x] 3.1 In `AgnesService.saveHistory()`, replace `rightPushAll(history)` + `trim(0, 19)` with `rightPushAll(userMsg, assistantMsg)` + `trim(key, -20, -1)`
- [x] 3.2 Remove Java-side subList truncation in `chat()` and `chatStreamReal()` — Redis LTRIM handles it
