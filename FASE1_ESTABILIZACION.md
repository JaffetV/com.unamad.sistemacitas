> **Documento de referencia histórico.** Para el estado y las pruebas de la entrega actual, consulta `docs/qa/ENTREGA_VERSION_1_7.md`.

# UNAMAD — Fase 1: auditoría y estabilización funcional

**Base revisada:** `p.unamad.sistemacitas-master.zip`  
**Fecha:** 11 de noviembre de 2026  
**Alcance:** estabilización previa al rediseño comercial. No se modificó el lenguaje visual integral.

## 1. Configuración Android

- Se añadió `agp = "8.11.1"` al catálogo de versiones, que faltaba aunque el plugin `com.android.application` lo referenciaba.
- Se incorporó `distributionUrl` al Gradle Wrapper: Gradle 8.13.
- Se aumentó el timeout de descarga del Wrapper a 30 segundos.
- Se marcó `gradlew` como ejecutable para entornos Linux/macOS.
- Se conservaron Kotlin 2.2.21, `compileSdk 35`, `targetSdk 35`, `minSdk 24` y bytecode Java 17.

La combinación queda dentro de la matriz oficial: Kotlin Gradle Plugin 2.2.21 admite Gradle hasta 8.14 y AGP hasta 8.11.1; AGP 8.11 requiere Gradle 8.13 y JDK 17.

## 2. Flujo de citas y reservas

- `BookingViewModel` valida que el negocio y la publicación del payload correspondan al contexto activo.
- Para una cita, exige que la hora enviada sea exactamente la del turno seleccionado y que el ID del profesional/recurso coincida con la asignación devuelta por disponibilidad.
- Rechaza cantidades inválidas y fechas/hora no futuras antes de llamar al repositorio.
- Se separaron los errores de consulta de disponibilidad (`availabilityErrorMessage`) de los errores de envío (`errorMessage`) para que la pantalla no muestre un error de disponibilidad cuando falla la creación.
- Al cerrar un formulario de reserva se limpia el turno anterior. Durante una recreación de Activity por cambio de configuración se conserva el estado necesario.
- La clave de idempotencia persistente del cliente y las transacciones/controles del servidor se conservaron.

## 3. Panel del negocio y MVVM

- La lista de negocios, la carga de panel y las mutaciones (publicaciones, horarios, trabajadores, recursos y estados de reservas) pasan por `BusinessPanelViewModel` y `BusinessRepository`.
- Se retiró la segunda carga HTTP que `PanelNegocioRemotoActivity` ejecutaba en paralelo a la petición del ViewModel.
- La Activity escucha eventos para mostrar el selector de negocio, confirmar operaciones y reactivar las acciones en caso de error.
- Las operaciones de cambio de estado mantienen un bloqueo por reserva para evitar doble envío.
- Los diálogos de mutación no se pueden cerrar mientras la operación está pendiente; vuelven a habilitarse si el servidor rechaza la operación.

## 4. Validación del backend

- `validateReservationPayload` ahora rechaza payloads con `staffId` y `resourceId` simultáneos.
- También rechaza asignaciones de personal/recursos en el modo `RECOJO`.
- Se añadieron aserciones de regresión para estas reglas y para la ruta MVVM del panel.

## 5. Comprobaciones realizadas

| Comprobación | Resultado |
|---|---:|
| `backend/npm test` | 68/68 aprobadas |
| `backend/npm run check` | Aprobado |
| Integridad del ZIP original | Correcta |
| `./gradlew --version` | No completó: DNS no resolvió `services.gradle.org` |
| `:app:assembleDebug` | No ejecutado; depende de descargar Gradle y de Android SDK |
| Tests instrumentados / emulador | No ejecutados |
| PostgreSQL real / concurrencia | No ejecutados en este entorno |

**Importante:** no se certifica todavía un APK compilado. La configuración de Gradle está corregida, pero el Wrapper no pudo descargar la distribución por `UnknownHostException`. Tampoco se detectó un Android SDK disponible en el entorno de auditoría.

## 6. Siguiente paso

El estilo elegido para la siguiente fase es **comercial**. Esta iteración no adelanta ese rediseño: primero debe abrirse el ZIP en Android Studio, sincronizar Gradle, instalar Android SDK Platform 35 y ejecutar `:app:assembleDebug` y `:app:connectedDebugAndroidTest`. Después de validar el flujo real en emulador, continuar con el rediseño integral de estilo comercial.
