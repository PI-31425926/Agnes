## ADDED Requirements

### Requirement: User can generate music melody
The system SHALL allow users to generate a musical melody by calling the Python music generation service through the backend proxy.

#### Scenario: Successful melody generation with default parameters
- **WHEN** user selects style "hot" and clicks "Generate"
- **THEN** system returns the generated melody text and optional MIDI base64

#### Scenario: Generation with custom parameters
- **WHEN** user sets temperature 0.7, output_length 256, bpm 100
- **THEN** system passes these parameters to the music API and returns the result

#### Scenario: Generation fails (service unavailable)
- **WHEN** the Python music service is not reachable
- **THEN** system returns an error message to the user

### Requirement: Music generation UI in MainView
The system SHALL display a new "🎵 Música" tab in MainView with controls for style, parameters, and results.

#### Scenario: User navigates to music tab
- **WHEN** user clicks the "🎵 Música" tab button
- **THEN** system displays the music generation interface with style selector and parameter inputs

#### Scenario: User generates melody and sees result
- **WHEN** user clicks "⚡ Generar" after selecting parameters
- **THEN** system shows the generated melody text in a scrollable area
