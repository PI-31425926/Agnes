## 1. Backend — Music Controller

- [x] 1.1 Add music service URL to application.yml configuration
- [x] 1.2 Create MusicController.java with POST /api/music/generate endpoint
- [x] 1.3 Create MusicRequest.java DTO for API parameters
- [x] 1.4 Create MusicResponse.java DTO for API response
- [x] 1.5 Implement HTTP proxy to Python service on localhost:8000
- [x] 1.6 Add error handling for service unavailable scenarios

## 2. Frontend — Music Tab UI

- [x] 2.1 Add "🎵 Música" tab button to MainView.vue header
- [x] 2.2 Create music generation panel template (style selector, parameters)
- [x] 2.3 Add music result display area (generated text + MIDI option)
- [x] 2.4 Implement style selector dropdown (hot/sad/fairy)
- [x] 2.5 Implement parameter inputs (temperature, output_length, bpm, key)

## 3. Frontend — Music API Integration

- [x] 3.1 Create api/music.js module with generateMusic function
- [x] 3.2 Wire up the Generate button to call the backend endpoint
- [x] 3.3 Handle loading states and error messages in UI
- [x] 3.4 Display generated melody text in result area

## 4. Styling & Polish

- [x] 4.1 Add CSS styles matching existing dark sci-fi theme (#0ff accents)
- [x] 4.2 Ensure responsive layout for mobile screens
- [x] 4.3 Add placeholder text and tooltips for parameters
