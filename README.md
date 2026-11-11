# Citas y Reservas — aplicación Android

Proyecto Android en Kotlin con API REST Node.js y PostgreSQL para descubrir negocios, consultar servicios y registrar/gestionar reservas.

## Abrir y ejecutar

Lee primero **[ABRIR_EN_ANDROID_STUDIO.md](ABRIR_EN_ANDROID_STUDIO.md)**. Contiene los pasos de instalación, sincronización, configuración del emulador y puesta en marcha del backend.

## Arquitectura actual

La aplicación se mantiene en un solo módulo Android y separa responsabilidades en:

- `view/`: Activities, navegación y representación de pantallas.
- `presentation/`: ViewModels, `StateFlow`, estados y eventos.
- `domain/`: validaciones y reglas independientes de Android.
- `data/`: repositorios, fuentes remotas, cliente HTTP y almacenamiento local heredado.
- `backend/`: API Node.js, seguridad, lógica de reservas y migraciones PostgreSQL.

El recorrido de reservas vigente usa la API y PostgreSQL como autoridad central. SQLite se conserva para compatibilidad con la agenda local antigua; no es la fuente de las nuevas reservas del marketplace.

## Versiones fijadas

- Android Gradle Plugin: 8.11.1.
- Gradle Wrapper: 8.13.
- Kotlin Gradle Plugin: 2.2.21.
- JDK: 17 o superior.
- Android SDK: Platform 35; `minSdk` 24.
- Backend: Node.js 22 y PostgreSQL 16.

La combinación AGP/Kotlin se mantiene dentro de la matriz de compatibilidad publicada por Kotlin; AGP 8.11 requiere Gradle 8.13. Fuentes: [compatibilidad Kotlin Gradle](https://kotlinlang.org/docs/gradle-configure-project.html) y [requisitos de AGP](https://developer.android.com/build/releases/about-agp).

## Comprobaciones locales

Desde `backend/`:

```bash
npm test
npm run check
```

Desde la raíz Android:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

Para pruebas instrumentadas con el emulador iniciado:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Documentos

- [Guía de Android Studio y emulador](ABRIR_EN_ANDROID_STUDIO.md)
- [Backend, Docker y PostgreSQL](backend/README.md)
- [Arquitectura consolidada](docs/architecture/CONSOLIDACION_TECNICA_2026_11.md)
- [Auditoría y QA de esta entrega](docs/qa/ENTREGA_VERSION_1_7.md)
- [Contrato API](docs/api/CONTRATO_API_V1.md)
- [Evaluación académica](docs/academic/EVALUACION_ACTIVIDAD_PROYECTO_FINAL_2026_II.md)

## Límites conocidos

La separación MVVM es incremental: las pantallas principales ya delegan las operaciones remotas en ViewModels y repositorios, pero parte del renderizado y de la transformación de datos permanece en Activities y aún existen contratos JSON dinámicos en Data/Presentation. El SQLite heredado sigue dentro del módulo y debe retirarse solo después de definir la migración de datos. La compilación Android, Lint, pruebas instrumentadas y la integración con PostgreSQL deben certificarse en el equipo donde se abre el proyecto.
