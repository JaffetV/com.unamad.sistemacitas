# Revisión de entrega — Citas y Reservas 1.7

Esta entrega aplica al proyecto base los cambios de marca visible, catálogo sin filtro Distrito, navegación HOME/back y configuración segura por build type. También alinea AGP con Kotlin 2.2.21 y retira `local.properties` de la computadora de origen.

La arquitectura continúa siendo incremental: Activities, ViewModels, repositorios y datasources están separados, pero queda deuda de DTOs, renderizado de Activities y SQLite legacy. Los pendientes y límites de validación están descritos en `docs/qa/ENTREGA_VERSION_1_7.md`.

Antes de presentar, compilar y ejecutar en Android Studio con JDK 17+, Android SDK Platform 35 y backend PostgreSQL activo. El proyecto ZIP incluye el código fuente y los scripts, no un APK certificado.
