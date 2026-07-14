## ADDED Requirements

### Requirement: Admin navigation entry in main view
The system SHALL display a "管理面板" (Admin Panel) navigation button in the main view header for users with ADMIN role. The button MUST be visible only when the current user's role is ADMIN, and MUST NOT be visible for regular users.

#### Scenario: Admin sees the navigation button
- **WHEN** a user with role "ADMIN" is logged in and visits the main page (`/`)
- **THEN** the system displays a "管理面板" button in the header navigation area

#### Scenario: Regular user does not see the navigation button
- **WHEN** a user with role "USER" is logged in and visits the main page (`/`)
- **THEN** the system does NOT display the "管理面板" button

#### Scenario: Clicking admin button redirects to admin panel
- **WHEN** an admin user clicks the "管理面板" button
- **THEN** the system navigates the user to the `/admin` route

#### Scenario: Admin button matches existing UI style
- **WHEN** the admin navigation button is rendered
- **THEN** it MUST use the same styling as existing navigation tabs (cyberpunk dark theme, cyan `#0ff` color, rounded border)