# CHANGELOG V14 — MVVM inicial y corrección de Gradle

- Corregida inconsistencia AGP 9.5.1 / Gradle 9.6.0 frente a `gradle.properties` y tests: AGP 8.10.0 + Gradle 8.11.1 + Kotlin 2.2.21 + JDK 17.
- Añadidas dependencias Lifecycle ViewModel/Runtime KTX.
- Migrado catálogo a `MarketplaceCatalogViewModel`, `MarketplaceCatalogUiState` y `MarketplaceCatalogRepository`.
- El ViewModel mantiene filtros, cursor, loading/error y descarta respuestas obsoletas.
- La UI observa StateFlow con `repeatOnLifecycle`; carga incremental agrega solo nuevas tarjetas para conservar scroll.
- Añadidas pruebas Espresso de portal/recreación y validación de correo con teclado.
- IDs estables para los campos de acceso y botón de login.
- Backend suite: 60/60; `npm run check`: correcto.
- No se certificó APK por falta de Android SDK y timeout descargando Gradle 8.11.1.
