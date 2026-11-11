# Informe QA — Consolidación técnica UNAMAD

**Archivo de partida:** `UNAMAD_Auditoria_Profunda_Revision.zip`  
**Fecha:** 11 de noviembre de 2026

## Cambios incluidos

- Se retiró el archivo monolítico `MarketplaceRemoteActivities.kt` y se separaron las cuatro Activities remotas.
- Se extrajeron y centralizaron los helpers UI una sola vez en `MarketplaceUiHelpers.kt`; se eliminaron las copias repetidas de cada pantalla.
- Se agregó `MarketplaceBrowseViewModel` para que la Activity de marketplace no ejecute operaciones HTTP directamente.
- Consultas, detalle, publicaciones, reservas del cliente y cancelación pasan por ViewModel/Repository/RemoteDataSource.
- Se agregaron claves de solicitud para descartar respuestas obsoletas y se sincronizó el mapa de trabajos cancelables.
- La sesión se comprueba sin descifrar el JWT en el hilo principal.
- Las preferencias de sesión e idempotencia se excluyen de backups en nube y transferencia entre dispositivos.
- La navegación inferior y categorías principales usan iconos vectoriales centralizados.
- Se conserva el módulo legacy SQLite sin permitir que se convierta en la fuente de nuevas reservas marketplace.

## Validación ejecutada

| Comprobación | Resultado |
|---|---:|
| `backend/npm test` | 82 aprobadas, 0 fallidas |
| `backend/npm run check` | Aprobado |
| XML Android | 29 documentos bien formados |
| Actividades del Manifest | 14 referencias resueltas |
| Referencias a drawables | 12 resueltas |
| Cliente HTTP desde Activities remotas | No detectado |
| Helpers compartidos duplicados en pantallas remotas | Eliminados y cubiertos por prueba |
| Integridad ZIP de origen | SHA-256 coincide |

## Riesgos y límites pendientes

- No se ejecutó `:app:assembleDebug`: Android SDK ausente y descarga de Gradle 8.13 bloqueada por resolución DNS de `services.gradle.org`.
- No se ejecutaron pruebas instrumentadas ni se instaló el APK en emulador/dispositivo.
- No se usó Android Lint ni Memory Profiler.
- No se conectó a PostgreSQL real; las pruebas backend automatizadas no prueban un despliegue real ni la concurrencia de dos clientes conectados.
- No se puede certificar visualmente cada pantalla, tamaño de fuente, teclado, accesibilidad y orientación sin capturas/ejecución de la app.

## Próximo control obligatorio en Android Studio

1. Abrir el proyecto con JDK 17 o superior y Android SDK Platform 35.
2. Ejecutar `./gradlew :app:assembleDebug`.
3. Ejecutar `./gradlew :app:testDebugUnitTest` y `./gradlew :app:lintDebug`.
4. Instalar el APK y recorrer acceso, registro, catálogo, reserva, Mis citas, cancelación y panel de negocio.
5. Repetir reserva con pérdida de red y con dos clientes para verificar idempotencia/contención en PostgreSQL.
6. Inspeccionar rotación, navegación atrás, teclado, fuentes grandes y uso de memoria.
