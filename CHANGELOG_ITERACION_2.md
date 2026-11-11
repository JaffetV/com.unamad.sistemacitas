# Cambios — Iteración 2 de MVVM

## Añadido
- `AuthViewModel`, `AuthUiState` y `AuthEvent` para login y registro de clientes.
- Contrato `AuthRepositoryContract`, `AuthRepository` y `AuthRemoteDataSource`.
- Validaciones puras en `AuthValidation` y pruebas JVM.
- `BookingViewModel`, `BookingUiState` y `BookingEvent` para disponibilidad y creación de reservas.
- Contrato `BookingRepositoryContract`, `BookingRepository` y `BookingRemoteDataSource`.
- Pruebas instrumentadas de AuthViewModel y BookingViewModel.
- Prueba instrumentada del flujo completo disponibilidad/selección/confirmación y prueba de reconexión simulada.

## Corregido
- Evita navegación doble al completar autenticación durante la recreación de Activity.
- Asegura que errores de validación no realicen llamadas de red.
- Mueve autenticación y operaciones de reserva a `Dispatchers.IO` mediante repositorios.
- Protege disponibilidad contra respuestas fuera de orden y cancela consultas anteriores.
- Mantiene el estado del diálogo de reserva y del ViewModel durante cambios de configuración.
- Conserva cuatro destinos principales en una barra inferior compartida.

## Verificación
- Backend: `npm test` — 66/66 aprobadas.
- Backend: `npm run check` — aprobado.
- Android XML: 20 archivos bien formados.
- Gradle TOML: válido.
- Android build y AndroidTest instrumentados: pendientes de ejecutar en una instalación con Android SDK; el entorno no pudo resolver `services.gradle.org` para descargar Gradle 8.11.1.

## Pendiente deliberado
Las pantallas de administración y alta de negocio todavía usan parte de su lógica directamente en Activities. Se recomienda migrarlas como iteraciones separadas para evitar una reescritura masiva sin pruebas reales.
