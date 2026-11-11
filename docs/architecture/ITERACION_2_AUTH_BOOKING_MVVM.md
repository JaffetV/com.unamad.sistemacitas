# Iteración 2 — Autenticación y reservas con MVVM

## Alcance

Esta iteración amplía la vertical MVVM del catálogo existente e incorpora autenticación y reserva centralizada sin reemplazar Kotlin nativo, XML/Views, Node.js ni PostgreSQL.

## Autenticación

- `presentation/auth/AuthViewModel.kt`: coordina login y alta de cliente con `viewModelScope` y `StateFlow<AuthUiState>`.
- `presentation/auth/AuthUiState.kt`: expone carga, mensaje de estado y error para la interfaz.
- `presentation/auth/AuthEvent.kt`: emite navegación por éxito y fallos como eventos de una sola vez mediante `Channel.BUFFERED`.
- `data/repository/AuthRepository.kt`: contrato y repositorio; mueve llamadas de red a `Dispatchers.IO`.
- `data/datasource/remote/AuthRemoteDataSource.kt`: adapta las operaciones de la API REST.
- `domain/validation/AuthValidation.kt`: validación pura de correo, nombre, teléfono y contraseña.

La Activity se limita a leer campos, observar el estado, mostrar errores y resolver la navegación. No almacena contraseñas en el `UiState`.

## Reservas

- `presentation/booking/BookingViewModel.kt`: consulta disponibilidad, conserva el turno elegido, evita respuestas antiguas con versión de petición y evita doble envío durante una operación activa.
- `presentation/booking/BookingUiState.kt`: mantiene negocio, servicio, fecha, turnos, selección y estados de carga/envío.
- `presentation/booking/BookingEvent.kt`: comunica la reserva creada o el error a la pantalla.
- `data/repository/BookingRepository.kt`: expone contratos y ejecuta las operaciones remotas en `Dispatchers.IO`.
- `data/datasource/remote/BookingRemoteDataSource.kt`: llama a los endpoints de disponibilidad y creación de reserva.

PostgreSQL sigue siendo la autoridad de reservas. La protección persistente de idempotencia y la validación final de disponibilidad permanecen en el backend.

## Restauración y autenticación

El contexto de negocio, publicación, fecha y selección de recojo se guardan en el estado de instancia al recrearse la Activity. El `BookingViewModel` retiene la disponibilidad y selección del turno durante cambios de configuración. El destino pendiente tras login se transporta mediante extras de Intent. `accountRoutingStarted` hace idempotente el enrutamiento para evitar dos navegaciones si coinciden la restauración y el evento de autenticación.

## Navegación principal

La barra compartida ofrece cuatro destinos: Inicio, Reservar, Mis citas y Negocio. Se monta en las pantallas principales del portal, catálogo/reserva y panel del negocio. Login y alta de cuenta son pantallas auxiliares de autenticación y conservan su acción de volver; no se presentan como destinos principales autenticados.

## Límites de esta iteración

- La migración MVVM cubre catálogo, autenticación y flujo de reserva. Las pantallas de administración y alta de negocio aún conservan lógica en Activities y deben migrarse gradualmente.
- La prueba de flujo de reserva utiliza un repositorio falso para validar ViewModel sin depender de un backend disponible; la integración extremo a extremo requiere API y PostgreSQL en ejecución.
- Las pruebas instrumentadas se añadieron, pero no se ejecutaron en este entorno porque no hay Android SDK y el Wrapper no pudo descargar Gradle por DNS.
