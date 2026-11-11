# Citas y Reservas — arquitectura y recorrido

## Propósito

La app permite a un cliente descubrir un negocio, revisar publicaciones, consultar disponibilidad y solicitar una reserva. El propietario administra sus negocios, horarios, publicaciones y reservas con permisos comprobados en el backend.

## Flujo cliente

`Portal -> Catálogo -> Negocio -> Servicio -> Disponibilidad -> Crear reserva -> Mis reservas`

El catálogo tiene un único buscador por nombre de negocio, título/descripción de servicios activos y categoría. El distrito se mantiene en la dirección del negocio, no como filtro de reserva. La aplicación solo muestra disponibilidad devuelta por el servidor; no inventa turnos ni confirma una reserva antes de recibir una respuesta satisfactoria.

## Capas Android

- `view/`: Activities y renderizado Views/Material.
- `presentation/`: ViewModels, `StateFlow`, UiState y eventos.
- `domain/`: validadores y reglas puras de fechas/campos.
- `data/`: repositorios, datasources HTTP, configuración, sesión e SQLite legado.

Las pantallas principales de autenticación, registro de negocio, catálogo, reservas y panel cuentan con ViewModels/repositorios para las operaciones remotas principales. La separación es incremental, no una Clean Architecture completa: aún hay JSON dinámico en contratos y las Activities principales conservan código de renderizado considerable.

## Backend y persistencia

Node.js expone la API REST y PostgreSQL mantiene usuarios, negocios, publicaciones, horarios, asignaciones, reservas e idempotencia. Las migraciones `001` a `004` preparan esquema, marketplace, recursos/idempotencia y paginación. SQLite permanece para conservar la agenda local anterior y no crea las nuevas reservas del marketplace.

## Inicio

Consulta [ABRIR_EN_ANDROID_STUDIO.md](ABRIR_EN_ANDROID_STUDIO.md) para abrir el proyecto, compilar Debug, iniciar Docker/PostgreSQL, configurar un emulador y conectar un teléfono físico.

## Deuda técnica abierta

- Extraer renderizadores de `MarketplaceRemotoActivity` y `PanelNegocioRemotoActivity`.
- Sustituir gradualmente `JSONObject`/`JSONArray` por DTOs Kotlin y mappers en Data.
- Aislar completamente las consultas SQLite legadas y mover cualquier operación residual fuera del hilo principal.
- Completar edición/desactivación de publicaciones donde la UI ofrezca esas acciones.
- Probar compilación Android, Lint, instrumentadas, PostgreSQL real, cancelación de red y concurrencia.

No se introduce panel ADMIN global: las autorizaciones actuales del backend corresponden a roles de membresía del negocio (`OWNER`, `MANAGER`, `STAFF`). Tampoco se inventan fotos remotas de negocios: se reutiliza el recurso local `hero_reservas` y los vectores de categoría.
