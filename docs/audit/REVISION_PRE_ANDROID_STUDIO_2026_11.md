# Revisión previa a Android Studio — 10 de noviembre de 2026

## Propósito

Preparar una versión coherente del **sistema de citas y reservas UNAMAD** antes de importarlo en Android Studio, comparar su código con la actividad final 2026-II y dejar documentadas las comprobaciones que dependen del equipo del estudiante.

## Correcciones realizadas

### Configuración Android

- Se fijó Android Gradle Plugin 8.10.0, Gradle Wrapper 8.11.1 y Kotlin Gradle Plugin 2.2.21.
- Se eliminó la configuración local de Gradle que forzaba una ruta de JDK de Windows o intentaba descargar un JDK diferente.
- Se dejó el bytecode objetivo en Java 17 y `compileSdk`/`targetSdk` 35.
- Se habilitó el wrapper `gradlew` para su ejecución en Unix.
- No se migró la interfaz a Compose: la app usa Activities y Views/XML, tecnología válida para la actividad y que se conserva para evitar una reescritura sin beneficio probado.

### Flujo de citas y limpieza

- El portal y las pantallas de reserva usan la API central y PostgreSQL.
- Se simplificaron las Activities antiguas `RegistroNegocioActivity`, `RegistroClienteActivity`, `MarketplaceActivity` y `PanelNegocioActivity`: quedan como puentes de compatibilidad hacia el flujo remoto.
- Se retiró código local inalcanzable detrás de `return`, incluidos formularios y mensajes que afirmaban que la reserva seguía guardándose solo en SQLite.
- `MainActivity` y `ProcesoReservaActivity` conservan sus redirecciones de compatibilidad.
- SQLite histórica se conserva; no se borran ni se importan automáticamente los registros antiguos.

### Interfaz y experiencia

- El portal identifica explícitamente la app como “Sistema de Citas y Reservas”.
- Se añadió una ilustración WebP propia del flujo de búsqueda, selección de horario y confirmación (aprox. 75 KB), con descripción accesible.
- Se mejoraron textos de búsqueda, descripción, estados vacíos, carga, errores y acciones principales.
- Los botones principales mantienen alturas táctiles de al menos 48 dp donde se revisó el catálogo.
- Se eliminó un distrito precargado para no restringir el catálogo a una localidad sin elección del usuario.

### Rendimiento y preparación para más volumen

- Catálogo API con cursor keyset por `(created_at, id)`, página predeterminada de 24 y máximo 50.
- Cursores conservan la precisión de microsegundos entregada por PostgreSQL para evitar solapamientos entre páginas.
- Migración 004 agrega índices de orden, categoría, distrito y búsqueda parcial por trigramas.
- Android comparte un executor IO de 4 hilos con cola limitada a 96 solicitudes, y muestra un error controlado si se satura.
- La vista de catálogo limita a 120 tarjetas renderizadas por búsqueda; se anima a refinar filtros para continuar sin acumular una lista ilimitada en memoria.
- El pool PostgreSQL es configurable con `PG_POOL_MAX` (15 por defecto). El presupuesto debe calcularse para el total de réplicas.

## Arquitectura y futura web

La arquitectura operativa se mantiene como cliente Android Kotlin → API REST Node.js → PostgreSQL. PostgreSQL es la única fuente de verdad para reservas compartidas. Una futura web consumirá el contrato OpenAPI y los mismos endpoints; no tendrá conexión directa con PostgreSQL ni duplicará la lógica de disponibilidad.

La base está separada por responsabilidad (`view`, `data/remote`, `data/local`, `data/repository`, `model`), pero aún hay Activities extensas en el flujo remoto. La extracción de ViewModels y módulos de dominio es una mejora posterior, incremental y cubierta por pruebas. No se afirma que el proyecto ya sea una arquitectura Clean/MVVM completa.

Para picos fuertes aún hacen falta Redis o un gateway para rate limiting distribuido, métricas, pruebas P95/P99, revisión de consultas con `EXPLAIN (ANALYZE, BUFFERS)`, y coordinación del pool con PgBouncer o PostgreSQL administrado según mediciones reales.

## Resultado académico

La revisión encuentra implementados en código Kotlin, varias pantallas, navegación, formularios validados, persistencia remota y local histórica, API REST, JWT y un flujo real de reserva. Las 38 pruebas Node.js son aprobadas.

La actividad no puede declararse 100 % verificada hasta ejecutar Gradle Sync, `assembleDebug`, pruebas unitarias Android y un recorrido en emulador o teléfono. Tampoco se ejecutó la migración ni la concurrencia contra PostgreSQL real en el entorno de preparación.

## Verificaciones de esta preparación

- `cd backend && npm test`: 38 pruebas aprobadas.
- `cd backend && npm run check`: aprobado.
- XML: 19/19 archivos bien formados; esta comprobación no equivale a AAPT2.
- Android Gradle Sync/compilación: no ejecutados; no se dispone de SDK Android y el wrapper necesita descargar Gradle.
- PostgreSQL/Docker: no disponibles en el entorno de preparación.

## Apertura en equipo del estudiante

1. Extraer ZIP.
2. Abrir la carpeta raíz con `settings.gradle.kts` en Android Studio.
3. Configurar JDK 17 y Android SDK Platform 35.
4. Esperar Gradle Sync.
5. Ejecutar `:app:assembleDebug` y `:app:testDebugUnitTest`.
6. Iniciar el backend siguiendo `backend/README.md` y probar el recorrido de reserva.

No entregar esta iteración como APK ya probado: se entrega el proyecto fuente preparado para sincronización local y validación final.
