# Iteración 6 — Auditoría funcional, navegación y consistencia visual

**Proyecto:** UNAMAD Sistema de Citas y Reservas  
**Fecha:** 10 de noviembre de 2026  
**Alcance:** flujo Android vigente, actividades remotas, cliente API, navegación y pruebas Node.js de regresión.

## 1. Objetivo

Mantener la aplicación como un sistema de **citas y reservas centralizadas** y reducir fricciones al pasar entre catálogo, detalle de negocio, selección de horario, autenticación, confirmación y Mis reservas. La auditoría busca corregir fallos reproducibles, evitar respuestas asíncronas obsoletas y unificar los elementos visuales del flujo que está enlazado desde el portal.

## 2. Hallazgos y correcciones

| Severidad | Área | Defecto observado | Corrección aplicada | Evidencia/prueba |
|---|---|---|---|---|
| Alta | Catálogo | Una respuesta de una búsqueda anterior podía llegar después de cambiar los filtros y reemplazar el estado más reciente. | Identificador de solicitud (`contentRequestId`) para invalidar respuestas antiguas; captura de filtros en el hilo UI antes de ejecutar I/O. | Pruebas 19, 21 y 24 de `backend/test/mobile-flow.test.js`. |
| Alta | Disponibilidad | Al cambiar fecha o cerrar el diálogo, una consulta lenta podía mostrar turnos que ya no correspondían. | Identificador por consulta (`availabilityRequestId`) y comprobación de que el diálogo siga visible. | Prueba 22. |
| Alta | Reserva de recojo | Se podía conservar una fecha/hora predeterminada sin que el usuario hubiera hecho una selección explícita. | Validación de selección explícita y de que fecha/hora sean futuras antes del envío. | Prueba 23. |
| Alta | Panel del negocio | Se podían iniciar varias cargas o acciones repetidas; una respuesta de otro negocio podía pintar el panel actual. | Bloqueo de acciones mientras hay carga, captura del ID de negocio y descarte de respuestas con ID de solicitud o negocio obsoleto. | Pruebas 25, 29 y 31. |
| Media | Mis reservas | La lista previa del catálogo y el botón de paginación podían permanecer visibles al entrar a las reservas. | Se ocultan filtros/paginación, se limpia la lista y se presenta estado de carga propio. | Pruebas 27, 32 y 34. |
| Media | Cancelación | Una respuesta tardía podía refrescar o alterar una sección distinta a aquella desde la que se inició la cancelación. | Se captura sección e ID de contenido; solo se refresca si el usuario sigue en la misma sección y solicitud. Se bloquean cancelaciones duplicadas por ID. | Pruebas 28 y 36. |
| Media | Regreso | El botón Atrás del sistema podía dejar al usuario en una pila de Activities confusa al salir de detalle/Mis reservas. | En `MarketplaceRemotoActivity`, Atrás vuelve al catálogo si la sección actual no es el catálogo; desde catálogo permite el comportamiento normal del sistema. | Prueba 27 y revisión del callback de navegación. |
| Media | Autenticación durante reserva | Si el servicio seleccionado desaparecía mientras el usuario iniciaba sesión, la app podía dejarlo sin explicación. | Mensaje explícito que invita a elegir otra opción del negocio. | Prueba 35. |
| Media | Confirmación | Cancelar una cita era una acción inmediata sin confirmación. | Diálogo de confirmación antes del POST; botón protegido contra operaciones duplicadas. | Prueba 36. |
| Media | Jerarquía visual | Algunas tarjetas informativas se exponían como clicables sin ejecutar ninguna acción. | Eliminado el estado clicable/focusable de tarjetas sin listener. | Prueba 30. |
| Media | Consistencia visual | Las pantallas remotas tenían estilos parcialmente repetidos. | Tokens compartidos en `UnamadPalette` y base `UnamadBaseActivity`, con colores, barras del sistema y componentes de formulario/tarjeta/botón coherentes. | Prueba 20 y revisión de clases activas. |

## 3. Archivos principales modificados

- `app/src/main/java/com/example/comunamadsistemacitas/view/UnamadDesignSystem.kt`: paleta, base de pantalla y textos legibles de estados/modalidades.
- `app/src/main/java/com/example/comunamadsistemacitas/view/MarketplaceRemoteActivities.kt`: navegación del catálogo, detalle, reserva, Mis reservas, cancelación y panel de negocio.
- `app/src/main/java/com/example/comunamadsistemacitas/view/MarketplaceActivities.kt`: portal y accesos principales sin acción redundante.
- `app/src/main/java/com/example/comunamadsistemacitas/data/remote/MarketplaceApiClient.kt`: dispatcher de I/O compartido y con cola limitada; los callbacks regresan al hilo principal y manejan rechazo de cola.
- `backend/test/mobile-flow.test.js`: pruebas de regresión de navegación, respuestas asíncronas, guardas de operaciones y consistencia visual.

## 4. Estado de la experiencia

Recorrido previsto:

1. **Inicio:** Reservar una cita, Mis reservas o Soy negocio.
2. **Reservar:** catálogo y filtros opcionales en el mismo paso.
3. **Negocio:** servicios publicados y modalidad.
4. **Reserva:** fecha, disponibilidad real y trabajador/recurso cuando corresponda.
5. **Confirmación:** envío al servidor, clave idempotente y estado de respuesta.
6. **Mis reservas:** consulta central, estado legible y cancelación confirmada.

El cambio de sección limpia las vistas y controles que pertenecían a la sección anterior. Las respuestas de solicitudes obsoletas no deben reemplazar la pantalla actual.

## 5. Verificaciones ejecutadas

- `cd backend && npm run check`: aprobado.
- `cd backend && npm test`: **59/59 pruebas aprobadas** en esta ejecución.
- Las pruebas Node.js incluyen reglas de dominio, paginación, seguridad y varias aserciones estáticas de regresión sobre el código Android. No equivalen a pruebas instrumentadas de Android.
- XML Android: **19/19 archivos parseados correctamente**; los 14 nombres de Activity declarados en el Manifest se encontraron en el código Kotlin. Esta es una comprobación estática, no una compilación.

## 6. Limitaciones y riesgos pendientes

1. **No se ejecutó `assembleDebug` ni `assembleRelease` en este entorno.** No hay Android SDK disponible y no se consiguió descargar Gradle aquí. Que el usuario pueda abrir el proyecto en Android Studio es una señal positiva, pero no sustituye una compilación limpia después de estos cambios.
2. **No se ejecutaron pruebas Espresso/UI en emulador.** La navegación real, accesibilidad táctil, teclado, rotación y estados de ciclo de vida deben probarse en el emulador del usuario.
3. **No se ejecutó PostgreSQL real ni pruebas de carga/concurrencia** en esta iteración. Las 59 pruebas no prueban la infraestructura desplegada.
4. `MarketplaceRemoteActivities.kt` conserva Activities con lógica de presentación y coordinación API. Para una evolución mantenible conviene extraer ViewModels y estados UI gradualmente, sin hacer una reescritura integral.
5. Las actividades SQLite antiguas permanecen como compatibilidad/histórico. El flujo central actual usa PostgreSQL; las pantallas antiguas no son el recorrido recomendado y todavía requieren una decisión explícita de retirada o migración.
6. No se midió rendimiento visual (jank, frame time, startup) en dispositivo. Los límites de catálogo y dispatcher reducen acumulaciones conocidas, pero no acreditan capacidad ante picos de usuarios.

## 7. Siguiente paso recomendado

En Android Studio, ejecutar `:app:assembleDebug` y `:app:testDebugUnitTest`; luego seguir la [lista de prueba manual del emulador](../qa/PRUEBA_MANUAL_EMULADOR_ITERACION_6.md) y recorrer: registro/inicio de sesión, reserva completa, reintento por red, Mis reservas, cancelar, botón Atrás, cambiar fecha durante carga y administrar dos negocios. Registrar cualquier `Logcat` con stack trace. Después conviene extraer `MarketplaceRemoteActivities` a ViewModels/UiState por flujo, empezando por catálogo y reserva.
