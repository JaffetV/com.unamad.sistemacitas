> **Documento de referencia histórico.** Para el estado y las pruebas de la entrega actual, consulta `docs/qa/ENTREGA_VERSION_1_7.md`.

# UNAMAD Citas — Fases 2 y 3

**Base:** ZIP de Fase 1 con SHA-256 verificado. **Fecha:** 11-11-2026. **Estilo:** comercial / Material Design 3.

## 1. Auditoría QA y correcciones

| Riesgo encontrado | Corrección aplicada |
|---|---|
| Respuestas REST sin `data`, `user`, `business` o arrays podían continuar como objetos vacíos. | El cliente exige objetos/listas de contrato y valida identidad (`id` + nombre) antes de guardar sesión o aceptar el perfil. La paginación exige `data`, `page`, `limit`, `hasMore` y cursor cuando corresponde. |
| Negocios, publicaciones y turnos incompletos podían mostrarse como opciones utilizables. | Se filtran negocios sin ID/nombre, publicaciones sin ID/título/tipo/precio válido y turnos sin fecha/asignación STAFF o RESOURCE. El panel no presenta un perfil de negocio vacío como correcto. |
| Fechas inválidas, truncadas o interpretadas en la zona del teléfono podían desplazar una cita. | `IsoDateTimeParser` usa parseo no leniente y consume la cadena completa. Valida `YYYY-MM-DD`, respeta offsets explícitos y trata históricos sin offset como `America/Lima`, igual que el backend. |
| Cancelación de coroutine podía convertirse en error funcional. | `suspendResult` vuelve a lanzar `CancellationException`; catálogo, panel y registro lo usan. Reserva ya conserva la cancelación. |
| Cargas y diálogos podían retener vistas de Activities. | Los colectores `StateFlow` siguen dentro de `repeatOnLifecycle(STARTED)`. Las Activities liberan clientes HTTP, cierran diálogos y referencias; los ViewModels cierran repositorios en `onCleared`. El flujo activo no usa Fragments, así que `viewLifecycleOwner` no aplica. |
| Una operación HTTP síncrona podía bloquear Main. | `MarketplaceApiClient.request` prohíbe llamadas en el hilo principal. Los repositorios remotos usan `Dispatchers.IO` y las Activities un executor IO acotado. |
| Si el servidor confirmaba una reserva y se perdía la respuesta, el cliente podía reintentar y duplicarla. | El móvil conserva una clave idempotente por cuenta+payload antes del POST y la elimina solo tras una respuesta completa. El backend serializa reintentos con advisory lock, compara la huella del payload y ejecuta la creación dentro de una transacción. No se simuló una caída real de red en este entorno. |
| Formularios podían enviar espacios o valores excesivos. | Validadores de login, cliente y negocio aplican `trim`, campos obligatorios, longitudes máximas, teléfono y contraseña; inputs limitan caracteres y muestran errores inline. |

## 2. Rediseño Material 3

- Tema base `Theme.Material3.Light.NoActionBar`.
- Tokens: azul `#0F4C81`, verde `#00A86B`, naranja `#E59866`, fondo `#F8F9FA`; tono de texto ajustado para contraste sobre el verde.
- `colors.xml`, `dimens.xml` y `UnamadPalette` centralizan color, espacios, control 52dp, input 12dp, tarjeta 16dp y elevación de 2dp.
- Formularios dinámicos usan `TextInputLayout` outlined, errores de validación, toggle de contraseña y botones con estados habilitado/deshabilitado.
- Tarjetas del catálogo/panel, chips de estado (verde confirmada, ámbar pendiente, rojo cancelada) y `LinearProgressIndicator` al consultar horarios.
- Los estilos XML heredados se migraron a referencias `Widget.Material3.*`.

Las pantallas vigentes se construyen mayormente con Views programáticas; por eso se actualizaron tanto los recursos como los constructores compartidos. El proyecto tiene panel de gestión por negocio, pero no un dashboard/rol independiente de administrador global; no se inventaron pantallas ni permisos.

## 3. Validaciones ejecutadas

| Comprobación | Resultado |
|---|---:|
| `backend/npm test` | **74/74 aprobadas**, 0 fallidas |
| `backend/npm run check` | Aprobado (`node --check`) |
| Parser y validadores Kotlin puros | **8/8** compilados/ejecutados con `kotlinc` |
| Recursos XML | **20/20** bien formados |
| Referencias app-owned a color/dimen/string/drawable | 0 faltantes |
| Referencias XML `Widget.MaterialComponents.*` restantes | 0 |
| Errores sintácticos Kotlin detectados en parse check | 0 (sin classpath Android no fue compilación semántica) |

## 4. Límites pendientes

- No se pudo ejecutar `:app:assembleDebug`: no hay `ANDROID_HOME`/`ANDROID_SDK_ROOT`, Gradle 8.13 no está en caché y el Wrapper falla al descargar por `UnknownHostException: services.gradle.org`.
- No se ejecutaron pruebas instrumentadas en emulador/dispositivo, Memory Profiler, rotación real ni corte real de red.
- No se ejecutó PostgreSQL real ni prueba de concurrencia. La idempotencia se revisó en código y pruebas backend, no en una caída de red real.
- `74/74` corresponde a `node --test`; no certifica un APK.

## 5. Archivos principales

- `app/src/main/res/values/{colors.xml,dimens.xml,themes.xml}` y layouts `activity_main`, `activity_registrar_cita`, `activity_detalle_cita`, `activity_listar_citas`.
- `domain/validation/{IsoDateTimeParser,AuthValidation,BusinessValidation}.kt`.
- `data/remote/MarketplaceApiClient.kt`.
- `presentation/SuspendResult.kt` y ViewModels de `booking`, `catalog`, `business`.
- `view/{UnamadDesignSystem,MarketplaceActivities,MarketplaceRemoteActivities}.kt`.
- `backend/test/phase23-qa-ui.test.js` y pruebas unitarias Kotlin para fechas/validaciones.

## 6. Comandos para validación local

Abrir el proyecto en Android Studio con JDK 17 y Android SDK Platform 35 y ejecutar:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

Después validar login, registro, selección de negocio, cita, recojo, cancelación, cambio de estado y reintento con conectividad interrumpida contra PostgreSQL de prueba.
