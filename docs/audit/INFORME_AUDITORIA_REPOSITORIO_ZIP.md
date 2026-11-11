# Informe de revisión del ZIP sistemacitasV2

## Hallazgos iniciales

1. **Regresión Gradle:** `libs.versions.toml` declaraba AGP 9.5.1 y el wrapper Gradle 9.6.0; `gradle.properties` y la prueba `project-config.test.js` correspondían a AGP 8.10.0 y Gradle 8.11.1. La prueba original fallaba (58/59). Se alinearon los archivos a AGP 8.10.0, Gradle 8.11.1, Kotlin 2.2.21 y JDK 17.
2. **Ausencia de MVVM:** no existían clases ViewModel/StateFlow. Se migró el catálogo a `MarketplaceCatalogViewModel`, `MarketplaceCatalogUiState` y `MarketplaceCatalogRepository`.
3. **Acoplamiento del catálogo:** la Activity ejecutaba paginación y mantenía cursores/loading. Ahora delega la lógica al ViewModel y observa el estado respetando el ciclo de vida.
4. **Cobertura de interfaz:** había instrumentación básica de contexto y migraciones, pero no pruebas UI del portal/acceso. Se añadieron pruebas Espresso para acciones del portal, recreación, correo inválido y cierre del teclado.

## Resultado de pruebas

- Backend: 60/60 aprobadas.
- `npm run check`: correcto.
- Android XML: parseo estático pendiente de completar al empaquetar.
- APK/AndroidTest: no verificados por falta de SDK y descarga Gradle no disponible.

## Alcance real

La migración MVVM es una primera vertical funcional (catálogo), no una refactorización completa de todas las pantallas. Acceso, registro, reserva e interfaz administrativa quedan para siguientes iteraciones. No se afirma que el proyecto compile al 100% hasta ejecutar Gradle en Android Studio.
