## 1. Backend — Global Response Wrapping

- [x] 1.1 Create `ApiResponseBodyAdvice.java` — `@RestControllerAdvice` implementing `ResponseBodyAdvice<Object>`
- [x] 1.2 Skip wrapping when return type is already `ApiResponse`
- [x] 1.3 Wrap all other return values with `ApiResponse.success(result)`

## 2. Backend — Controller Updates

- [x] 2.1 `ImageController.java` — change return types from raw DTO/List to `ApiResponse<T>`
- [x] 2.2 `ChatController.java` — change `/history` return from raw List to `ApiResponse<List>`
- [x] 2.3 `AuthController.java` — change login/register/logout responses to `ApiResponse`
- [x] 2.4 `FileUploadController.java` — change upload/clear responses to `ApiResponse`
- [x] 2.5 `AdminController.java` — change users/logs responses to `ApiResponse`

## 3. Frontend — Axios Interceptor

- [x] 3.1 Update `frontend/src/api/request.js` response interceptor to extract `response.data.data`
- [x] 3.2 Handle error responses: extract `response.data.message` and throw

## 4. Frontend — Remove unwrapResponse

- [x] 4.1 Remove `unwrapResponse` function from `MainView.vue`
- [x] 4.2 Update all API calls in `MainView.vue` to use direct data access (no unwrap)
- [x] 4.3 `frontend/src/api/*.js` files already use axios instance — interceptor handles unwrapping transparently
