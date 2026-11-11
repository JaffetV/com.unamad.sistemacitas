# Pruebas Android — Iteración 2

## Nuevas pruebas instrumentadas

### `AuthViewModelInstrumentedTest`
- login exitoso y evento de sesión;
- validación de correo sin invocar el servidor;
- error de red y reintento;
- registro y origen del evento de autenticación.

### `BookingViewModelInstrumentedTest`
- disponibilidad → selección de profesional/recurso → creación de reserva;
- doble confirmación sin ejecutar dos creaciones;
- pérdida simulada de conexión y recuperación posterior.

### Pruebas existentes complementadas
- `BookingFlowUiTest`: presencia de elementos del catálogo y barra inferior tras recreación de Activity;
- `PortalAndLoginUiTest`: cuatro destinos, validación de correo y conservación de los campos de acceso tras recreación.

## Ejecutar en Android Studio

1. Inicia el emulador o conecta un dispositivo.
2. Inicia el backend y PostgreSQL si ejecutarás pruebas de integración real.
3. Ejecuta pruebas instrumentadas desde Android Studio o con:

```bash
./gradlew :app:connectedDebugAndroidTest
```

4. Compila el APK debug con:

```bash
./gradlew :app:assembleDebug
```

5. Ejecuta pruebas unitarias con:

```bash
./gradlew :app:testDebugUnitTest
```

Las pruebas con repositorios falsos no dependen de la red. Para validar la integración HTTP real, la API debe responder en `http://10.0.2.2:8080` desde el emulador.

## Estado de ejecución en el entorno de preparación

- Backend `npm test`: 66/66 aprobadas.
- Backend `npm run check`: aprobado.
- Recursos Android XML: 20/20 parseados correctamente.
- Catálogo TOML de Gradle: válido.
- `:app:assembleDebug`: no ejecutable aquí. El wrapper intentó obtener Gradle 8.11.1 y falló por `UnknownHostException: services.gradle.org`; además, `ANDROID_HOME` y `ANDROID_SDK_ROOT` no están definidos.
- `connectedDebugAndroidTest`: no ejecutado; no hay Android SDK/emulador disponible en este entorno.

No se declara compilación APK ni ejecución Espresso exitosa sin evidencia de su ejecución.
