# Validación — Iteración 14

## Automatizado en este entorno

- `npm run check --prefix backend`: correcto.
- `npm test --prefix backend`: 60 pruebas aprobadas.
- Prueba nueva de configuración: AGP, Gradle, Kotlin y dependencias lifecycle alineadas.
- Prueba de arquitectura: catálogo usa ViewModel, StateFlow y Repository.
- Prueba de flujo: búsqueda nueva invalida respuestas de búsqueda anteriores.
- Pruebas Android instrumentadas añadidas para portada, recreación y validación de correo/teclado.

## No ejecutado

No se pudo completar `:app:assembleDebug`: el entorno no tiene Android SDK (`ANDROID_HOME` y `ANDROID_SDK_ROOT` vacíos) y la descarga de Gradle 8.11.1 desde `services.gradle.org` agotó el tiempo de espera. Por tanto, no se certifica compilación Kotlin ni ejecución de AndroidTest desde esta sesión.

## Prueba en Android Studio

1. Abrir el proyecto y seleccionar JDK 17.
2. Instalar Android SDK Platform 35 y Build Tools 35.0.0.
3. Ejecutar Gradle Sync.
4. Ejecutar `:app:assembleDebug`.
5. Ejecutar `:app:testDebugUnitTest` y `:app:connectedDebugAndroidTest` con un emulador.
6. Iniciar el backend y recorrer: portal -> reservar -> filtros -> negocio -> servicio -> disponibilidad -> confirmar -> mis reservas.
7. Repetir cambiando filtros rápidamente y rotando el dispositivo durante la carga del catálogo.
8. Detener temporalmente la API, verificar el mensaje de error y reintentar después de levantar el backend.

La prueba de UI de acceso usa un correo inválido y verifica que el formulario muestre el error sin enviar una petición de red. La prueba del portal verifica que las acciones principales sobrevivan a la recreación de Activity.
