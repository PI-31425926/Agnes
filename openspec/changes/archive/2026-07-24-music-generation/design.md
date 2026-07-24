## Context

La plataforma Agnes tiene un frontend Vue 3 con tabs en `MainView.vue` (chat, text2img, img2img, video, workflow). El usuario tiene un servicio Python independiente de generación musical en localhost:8000 con API REST. No hay integración actual entre ambos.

## Goals / Non-Goals

**Goals:**
- Nuevo tab "🎵 Música" en MainView con interfaz de generación
- Proxy del backend Spring Boot hacia el servicio Python
- Mantener estilo visual existente (dark sci-fi cyan #0ff)
- Mostrar resultado (texto de melodía + opción de audio)

**Non-Goals:**
- No integrar el servicio Python como parte del backend (se mantiene separado)
- No añadir historial de generaciones musicales (futuro)
- No añadir reproducción de audio avanzada (solo texto por ahora)

## Decisions

1. **Proxy via Spring Boot**: En lugar de llamar al servicio Python directamente desde el frontend, el backend Spring Boot actúa como proxy. Esto permite:
   - Centralizar la gestión de errores
   - Añadir autenticación/autorización si fuera necesario
   - Evitar CORS issues

2. **Configuración externa**: La URL del servicio Python se configura en `application.yml` para poder cambiarla sin recompilar.

3. **Frontend monolítico**: El nuevo tab se añade a `MainView.vue` siguiendo el patrón existente, no se crea un componente separado.

## Risks / Trade-offs

[Risk] Servicio Python fuera de línea → [Mitigation] Mostrar error claro al usuario, timeout configurable

[Risk] CORS si se llama directo desde frontend → [Mitigation] Proxy via backend evita este problema

[Risk] Formato de respuesta del servicio Python cambie → [Mitigation] DTOs estrictos en el controller
