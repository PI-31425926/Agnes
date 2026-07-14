## ADDED Requirements

### Requirement: Uniform ApiResponse envelope
All REST API endpoints SHALL return responses wrapped in a consistent `ApiResponse<T>` envelope with `code`, `message`, and `data` fields.

#### Scenario: Successful data request
- **WHEN** a GET request is made to any API endpoint
- **THEN** the response is `{ "code": 200, "message": "success", "data": <payload> }`

#### Scenario: Error response
- **WHEN** a request fails (validation error, not found, etc.)
- **THEN** the response is `{ "code": <error_code>, "message": "<description>", "data": null }`

### Requirement: Automatic response wrapping
The system SHALL use a global `ResponseBodyAdvice` to automatically wrap controller return values in `ApiResponse`, eliminating the need for manual wrapping in every controller method.

#### Scenario: Non-ApiResponse return value auto-wrapped
- **WHEN** a controller method returns a DTO, List, or String
- **THEN** the framework automatically wraps it in `ApiResponse.success(result)`

#### Scenario: ApiResponse return value passthrough
- **WHEN** a controller method already returns `ApiResponse<T>`
- **THEN** the response is sent as-is without double-wrapping

### Requirement: Frontend Axios interceptor
The frontend SHALL use an Axios response interceptor to automatically extract `response.data.data`, providing a clean data object to all callers.

#### Scenario: Interceptor extracts data
- **WHEN** an API call returns successfully
- **THEN** the interceptor returns `response.data.data` directly to the caller

#### Scenario: Interceptor preserves error info
- **WHEN** an API call returns an error response (HTTP 4xx/5xx)
- **THEN** the interceptor throws with the error message from `response.data.message`

### Requirement: Removed unwrapResponse hack
The frontend SHALL remove the `unwrapResponse()` function and all direct `res.data` accesses in favor of the standardized interceptor.

#### Scenario: All API calls use consistent data access
- **WHEN** any component calls an API
- **THEN** it receives the unwrapped data directly without conditional checks
