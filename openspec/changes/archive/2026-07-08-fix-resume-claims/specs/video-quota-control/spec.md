## MODIFIED Requirements

### Requirement: Atomic quota deduction via Lua
Adding a new video task SHALL use a Redis Lua script to atomically check the user's current task count and increment it, preventing race conditions under concurrent requests.

#### Scenario: Atomic quota check and increment
- **WHEN** a user submits a new video task while another request arrives simultaneously
- **THEN** a Lua script atomically reads the count, compares against MAX (5), and increments only if under the limit — no TOCTOU gap

#### Scenario: Quota exceeded returns 429
- **WHEN** the Lua script finds the user already has 5 tasks
- **THEN** the script returns -1 and the controller responds with HTTP 429 Too Many Requests

### Requirement: SSCAN cursor-based task iteration
The video polling scheduler SHALL use Redis SSCAN to iteratively scan the pending tasks set in batches, avoiding loading all members at once.

#### Scenario: SSCAN iterates pending tasks in batches
- **WHEN** the scheduler polls for pending video tasks
- **THEN** system calls SCAN with a cursor and COUNT parameter (e.g., 100), processing each batch without blocking Redis

#### Scenario: SSCAN completes full iteration
- **WHEN** pending tasks exceed the SCAN batch size
- **THEN** the cursor loops until it returns 0, collecting all pending task IDs across batches
