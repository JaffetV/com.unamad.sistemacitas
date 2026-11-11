# Arquitectura Android — Iteración 14

## Estado de la migración

Se inicia una migración MVVM incremental sin reemplazar el flujo de citas existente.

### Vertical migrada: catálogo de negocios

- `view/MarketplaceRemoteActivities.kt`: Activity de presentación. Captura los filtros en el hilo principal, representa `MarketplaceCatalogUiState` y delega búsqueda/paginación.
- `presentation/catalog/MarketplaceCatalogViewModel.kt`: conserva el estado con `StateFlow`, gestiona la paginación, evita solicitudes duplicadas y descarta respuestas obsoletas mediante una versión de petición.
- `presentation/catalog/MarketplaceCatalogUiState.kt`: estado inmutable de carga, resultados, cursor, error y límite de renderizado.
- `data/repository/MarketplaceCatalogRepository.kt`: encapsula el acceso al catálogo y ejecuta I/O con `Dispatchers.IO`.
- `data/remote/MarketplaceApiClient.kt`: conserva el contrato HTTP y la autenticación existente.

### Flujo

`Activity -> ViewModel -> Repository -> API REST -> PostgreSQL`

La Activity no gestiona cursores ni ejecuta consultas del catálogo. El ViewModel sobrevive a cambios de configuración y mantiene el estado del catálogo. Cada búsqueda incrementa un identificador; una respuesta de una búsqueda anterior no puede sobrescribir la nueva.

## Configuración Android fijada

- Android Gradle Plugin: 8.10.0
- Gradle Wrapper: 8.11.1
- Kotlin Gradle Plugin: 2.2.21
- JDK: 17
- compileSdk / targetSdk: 35

Se corrigió la regresión en la que el catálogo Gradle y el wrapper declaraban AGP 9.5.1 / Gradle 9.6.0 mientras `gradle.properties` y las pruebas esperaban AGP 8.10.0 / Gradle 8.11.1.

## Lo que aún no se ha migrado

Las pantallas de acceso, registro, reserva, historial y panel de negocio continúan usando Activities con lógica de presentación y llamadas al cliente API. No se debe describir todavía toda la app como MVVM completa. La siguiente secuencia recomendada es:

1. Acceso y registro: `AuthViewModel` + `AuthRepository`.
2. Reserva: `BookingViewModel` + `AvailabilityRepository`, conservando idempotencia en servidor y dispositivo.
3. Historial: `ReservationsViewModel`.
4. Administración: `BusinessDashboardViewModel` y estados por acción.

No se añadieron casos de uso ni inyección de dependencias hasta que aporten valor; se mantiene el patrón Repository y el contenedor de dependencias manual.

## Reutilización futura web

El contrato REST y PostgreSQL siguen siendo la frontera compartida. La futura web debe consumir la misma API; no debe conectarse directamente a PostgreSQL. La capa Android `presentation` no debe importarse desde backend/web.
