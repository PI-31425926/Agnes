## Why

El usuario tiene un servicio Python de generación musical basado en RNN (Hot/Sad/Fairy) con API REST en localhost:8000. Actualmente la plataforma Agnes no tiene una interfaz para generar música. Se necesita un nuevo tab en MainView que permita interactuar con este servicio, manteniendo el estilo visual existente (dark sci-fi con cyan #0ff).

## What Changes

- **Frontend**: Añadir un nuevo tab "🎵 Música" en MainView con interfaz de generación musical
- **Formulario**: Selector de estilo (hot/sad/fairy), temperatura, longitud de salida, BPM, tonalidad, instrumento
- **Resultado**: Mostrar el texto de la melodía generada y reproducir/escuchar si hay soporte audio
- **API**: Proxy del backend Spring Boot hacia el servicio Python en localhost:8000

## Capabilities

### New Capabilities

- `music-generation`: Generación de melodías mediante API REST al servicio Python de música

### Modified Capabilities

<!-- None — no existing spec-level requirements are changing -->

## Impact

| 区域 | 影响 |
|------|------|
| Frontend | `MainView.vue` — nuevo tab "🎵 Música", panel de generación, resultado |
| Frontend API | `api/music.js` — método para llamar al endpoint de generación |
| Backend | Nuevo `MusicController.java` — proxy HTTP al servicio Python en localhost:8000 |
| Backend Config | `application.yml` — configuración del endpoint del servicio de música |
