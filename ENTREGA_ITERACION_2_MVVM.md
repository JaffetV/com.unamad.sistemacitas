# Entrega — segunda iteración MVVM

## Incluido

1. Autenticación de cliente: `AuthViewModel`, `AuthUiState`, `AuthEvent`, `AuthRepository`, `AuthRemoteDataSource` y validación de dominio.
2. Reservas: `BookingViewModel`, `BookingUiState`, `BookingEvent`, `BookingRepository` y `BookingRemoteDataSource`.
3. Navegación inferior común con Inicio, Reservar, Mis citas y Negocio en las pantallas principales.
4. Restauración del contexto de reserva tras rotación, incluyendo negocio, publicación, fecha, elección de recojo, cantidad y nota.
5. Protección contra doble ruta de autenticación durante recreación.
6. Pruebas instrumentadas para AuthViewModel y BookingViewModel; pruebas de UI de portal, login, navegación y recreación.

## Verificaciones ejecutadas

- Backend `npm test`: 66 pruebas aprobadas.
- Backend `npm run check`: correcto.
- Android XML: 20 de 20 archivos parseados correctamente.
- TOML Gradle: válido.
- Gradle Wrapper: ejecutable.

## Validaciones no ejecutadas

No se pudo ejecutar `:app:assembleDebug`, `:app:testDebugUnitTest` ni `:app:connectedDebugAndroidTest` en el entorno de preparación. El Wrapper intentó descargar Gradle 8.11.1 y falló con `UnknownHostException: services.gradle.org`; no hay `ANDROID_HOME` ni `ANDROID_SDK_ROOT`. No se afirma que el APK haya compilado.

## Abrir y verificar en Android Studio

- JDK 17.
- Android SDK Platform 35.
- Sincronizar Gradle.
- Ejecutar `:app:assembleDebug`.
- Ejecutar `:app:testDebugUnitTest`.
- Iniciar el backend/PostgreSQL y el emulador.
- Ejecutar `:app:connectedDebugAndroidTest`.

En emulador, el backend local suele ser accesible por `http://10.0.2.2:8080`; para un dispositivo físico configura la IP LAN de la computadora mediante `-PmarketplaceApiBaseUrl=http://IP_DEL_PC:8080` en debug.

## Alcance MVVM

Esta iteración migra catálogo, autenticación y flujo de reserva. El panel de administración y el alta de negocio mantienen partes de lógica en Activity y quedan como siguiente migración incremental.
