# UNAMAD — Auditoría y corrección del sistema de citas y reservas

**Fecha:** 10 de noviembre de 2026
**Base:** versión del proyecto centralizado recibida en ZIP y revisada nuevamente el 10 de noviembre de 2026.
**Control de versiones:** el ZIP recibido no contenía `.git`; este informe no atribuye una rama ni un commit al paquete final.
**Alcance:** backend Node.js, SQL PostgreSQL, Android Kotlin, experiencia de usuario, configuración Gradle, pruebas y documentación.
**Última actualización:** consulta también [`REVISION_PRE_ANDROID_STUDIO_2026_11.md`](REVISION_PRE_ANDROID_STUDIO_2026_11.md) y la evaluación académica 2026-II.

## 1. Objetivo rector

UNAMAD es, ante todo, un **sistema de gestión de citas y reservas**. El marketplace es una función de descubrimiento de negocios y servicios; no constituye una agenda separada ni una fuente de disponibilidad. Toda reserva compartida entre clientes y negocios se consulta, valida y persiste en Node.js/PostgreSQL.

SQLite conserva datos locales históricos de la aplicación anterior. No se utiliza para crear ni confirmar nuevas reservas desde la navegación principal. No se hace importación automática de citas antiguas porque sus IDs locales no identifican de forma fiable un negocio remoto, cuenta de cliente ni trabajador UUID; borrar o asociar esos datos automáticamente podría atribuir citas a personas equivocadas.

## 2. Estado de la iteración

| Área | Cambio | Estado de validación |
|---|---|---|
| Asignación | Tablas de trabajadores y recursos; una cita ocupa un trabajador o recurso. | Código y migración revisados; falta ejecutar en PostgreSQL real. |
| Concurrencia | Advisory transaction locks por asignado y exclusiones GiST por trabajador/recurso. | Pruebas estáticas; falta prueba simultánea con dos conexiones PostgreSQL. |
| Capacidad | Recurso virtual por negocio existente y nuevos recursos adicionales. | Migración y rutas revisadas; falta integración real. |
| Idempotencia | Clave única por cliente, huella SHA-256, lock por cliente+clave y replay del mismo resultado. | Validadores y SQL revisados; falta carrera end-to-end PostgreSQL. |
| Android idempotente | Clave y huella del payload guardadas antes del POST y conservadas en errores de red. | Código revisado; falta build y prueba en dispositivo. |
| Disponibilidad | Endpoint con fecha local del negocio, duración real, jornada semanal y turnos de 15 minutos. | SQL/ruta revisados; falta integración real. |
| Reserva móvil | Selecciona un turno de disponibilidad remota y envía `staffId` o `resourceId`; se elimina la pantalla con datos de demostración. | Código revisado; falta prueba Android conectada. |
| Fuente de verdad | Navegación vigente redirigida al portal/panel remoto; `MainActivity` antigua ya no crea citas SQLite. | Referencias estáticas revisadas. |
| Catálogo | API keyset con índices de orden, categoría, distrito y búsqueda parcial; Android carga por páginas. | 38 pruebas Node; falta benchmark en staging. |
| Configuración Android | AGP 8.10.0, Gradle 8.11.1, Kotlin 2.2.21 y JDK 17; puentes antiguos reducidos. | TOML/XML/wrapper verificados; falta `assembleDebug` local. |
| Interfaz | Ilustración WebP, textos de reserva, estados vacíos/accesibles y límite de 120 tarjetas por búsqueda. | Revisión de código; falta inspección en dispositivo. |

## 3. Cambios de código realizados

### PostgreSQL — migración `003_appointment_resources_idempotency.sql`

- Crea `business_staff` y `business_resources`, ambos ligados al negocio con claves foráneas compuestas.
- Crea un recurso `Atención general 1` para cada negocio existente que todavía no tenga recursos.
- Asigna las citas históricas sin trabajador/recurso a ese recurso para conservarlas y satisfacer el nuevo modelo.
- Añade `assigned_staff_id`, `assigned_resource_id`, `idempotency_key` y `request_fingerprint` a `reservations`.
- Obliga a que una reserva de tipo `CITA` tenga al menos un asignado y que clave/huella se almacenen juntas.
- Elimina la exclusión anterior por `business_id` y crea exclusiones GiST por trabajador y por recurso, respetando el rango semiabierto `[inicio, fin)`.
- Añade una función `SECURITY DEFINER` que responde solamente si el asignado tiene el intervalo libre. No devuelve nombres, clientes ni detalles de otras reservas.
- La función necesita leer reservas de otros clientes: se quita `FORCE ROW LEVEL SECURITY` únicamente a `reservations`, se mantiene RLS habilitado y el rol runtime `marketplace_app` sigue siendo distinto del propietario y sin `BYPASSRLS`. La función es propiedad del dueño del esquema y su ejecución se concede explícitamente al rol de API.

### Backend — `server.js` y `domain.js`

- `POST /api/v1/me/reservations` exige clave idempotente en el encabezado `Idempotency-Key` o en el cuerpo.
- La solicitud se bloquea por cliente+clave antes de consultar registros previos. Dos POST idénticos simultáneos esperan el mismo lock; el segundo recupera la reserva ya creada.
- La misma clave con contenido diferente devuelve `409 IDEMPOTENCY_KEY_REUSED`.
- La reserva y su pago pendiente se insertan en una sola transacción; el replay no repite el pago ni el descuento de inventario.
- Antes de insertar, el servidor valida negocio, publicación, modalidad, duración, horario semanal y asignación actual del trabajador/recurso.
- `GET /api/v1/businesses/{id}/availability` genera turnos cada 15 minutos según duración del servicio, zona horaria del negocio y ocupación efectiva. Rechaza fechas pasadas.
- Se añaden endpoints de consulta/alta/activación/desactivación para trabajadores y recursos. No se permite desactivar el último recurso activo.
- Si se añade personal después de haber utilizado puestos virtuales, las reservas que sigan asociadas a recursos también bloquean intervalos de los nuevos trabajadores durante esa transición.
- CORS permite `Idempotency-Key` en encabezados.

### Android

- `MarketplaceApiClient` consulta disponibilidad y administra trabajadores/recursos contra el backend.
- `crearReserva()` persiste una clave UUID por solicitud lógica en SharedPreferences privadas antes del POST. La huella usa el identificador `sub` del JWT y el payload, de modo que una renovación del token no cambia la clave pendiente; distintas reservas no se sobrescriben entre sí. Ante una excepción de red conserva la clave para reintentar; al recibir respuesta exitosa elimina solo ese pendiente.
- `MarketplaceRemotoActivity` ya no permite escribir a mano una hora para una cita: muestra los turnos devueltos por PostgreSQL y transmite el asignado real.
- `ProcesoReservaActivity` se convirtió en puente hacia el flujo central, sin trabajadores/horarios ficticios, número telefónico fijo ni escritura local.
- El portal y el panel utilizan el recorrido remoto. La antigua `MainActivity` redirige al portal para impedir que las nuevas citas se creen en SQLite.
- Se mantiene el archivo SQLite y no se borran filas históricas.
- El nombre de la aplicación identifica expresamente el producto como “UNAMAD · Sistema de Citas y Reservas”.

## 4. Riesgos y límites todavía abiertos

1. **No hay prueba de integración PostgreSQL en este entorno.** La exclusión GiST, RLS, los locks y la migración deben probarse con una instancia PostgreSQL 16 real antes de producción.
2. **No se pudo compilar Android.** No hay Android SDK configurado y el wrapper no pudo descargar Gradle debido al fallo DNS del entorno (`services.gradle.org`).
3. **Jornada por trabajador:** todos heredan todavía el horario semanal general del negocio. Faltan jornada individual, descansos, feriados, vacaciones y excepciones por fecha.
4. **Transición de recursos a trabajadores:** las reservas históricas se conservan; los recursos que sigan ocupados bloquean temporalmente los nuevos turnos de trabajadores para evitar cruces. Se debe verificar con datos de staging.
5. **Datos históricos SQLite:** permanecen en el dispositivo, no se sincronizan automáticamente. Hace falta un proceso de conciliación con confirmación de propietario/cliente antes de importarlos al servidor.
6. **Sesiones:** el cierre de sesión elimina el token del dispositivo, pero no se implementó revocación server-side de JWT.
7. **Pagos:** se registran como pendientes; no hay pasarela ni webhooks.

## 5. Pruebas y verificaciones

Comandos y comprobaciones ejecutadas para la revisión más reciente:

```bash
cd backend
npm test       # 38/38 aprobadas
npm run check  # sintaxis Node.js aprobada
```

Además, se validaron 19/19 XML con parser XML, el catálogo TOML de Gradle, el JAR del wrapper, el permiso ejecutable de `gradlew`, la presencia y tamaño de la ilustración WebP y la ausencia de `.env`/keystores en la fuente preparada. Estas verificaciones estáticas no sustituyen AAPT2 ni Gradle.

No se afirma compilación APK, migración aplicada en una base viva, concurrencia end-to-end ni rendimiento medido. No hay Android SDK, Docker ni `psql` en el entorno de preparación; tampoco fue posible descargar la distribución de Gradle desde `services.gradle.org`. La compilación y ejecución final deben confirmarse en Android Studio.

## 6. Comparación antes/después

| Tema | Antes | Ahora |
|---|---|---|
| Concepto del producto | Mezcla de agenda local y marketplace. | Sistema central de citas y reservas; catálogo solo descubre servicios. |
| Concurrencia | Un solapamiento bloqueaba el negocio completo. | Exclusión por trabajador o recurso asignado. |
| Sin trabajador | No había modelo de capacidad central. | Puesto virtual de capacidad por negocio y recursos adicionales. |
| Reintentos | Sin clave de idempotencia. | Clave/huella persistentes, lock transaccional y replay. |
| Horarios en Android | La ruta clásica tenía horarios y trabajadores de demostración. | El cliente selecciona turnos de la API según horario y duración real. |
| Confirmación | Podía guardar una cita local sin reserva central. | La solicitud se crea en PostgreSQL y el cliente espera resultado HTTP exitoso. |
| SQLite | Segundo flujo local de marketplace/agenda. | Conservado como archivo histórico; no es autoridad para nuevas reservas. |
| Catálogo | Lista limitada y sin paginación keyset. | 24 por página en API, máximo 50 y tope visual de 120 por búsqueda en Android. |
| Pantallas heredadas | Conservaban formularios y paneles locales inalcanzables tras `return`. | Reducidas a puentes remotos para evitar rutas operativas duplicadas. |
| Validación de fecha | Endpoint de disponibilidad no tenía flujo real de slots. | Slots calculados por fecha/zona horaria del negocio y rechazo de fechas pasadas. |

No se publican porcentajes de mejora de rendimiento: no se dispuso de medición reproducible ni entorno de carga.
