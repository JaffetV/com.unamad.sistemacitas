# Evaluación de UNAMAD Sistema de Citas y Reservas frente a la actividad final

**Base de evaluación:** indicaciones entregadas para “Desarrollo de Aplicaciones Móviles II · 2026-II”.  
**Alcance:** revisión estática del código fuente y pruebas ejecutables disponibles en esta entrega. No se atribuyen resultados de emulador, compilación Android ni PostgreSQL real que no se hayan podido ejecutar.

## Resultado general

El proyecto **cumple la mayor parte de los requisitos de diseño e implementación que pueden comprobarse en el código**, y supera el mínimo de cuatro pantallas en estructura. Sin embargo, **todavía no puede declararse 100 % verificado**, porque la compilación Android y la ejecución en emulador/dispositivo deben confirmarse desde Android Studio y la integración de PostgreSQL debe probarse con una instancia real.

## Matriz de cumplimiento

| Requisito de la actividad | Evidencia del proyecto | Evaluación |
|---|---|---|
| Atender una necesidad concreta | La app administra citas, disponibilidad, reservas, negocios y servicios. | **Cumple** |
| Utilizar Kotlin | Código principal en `app/src/main/java/...` con Activities, repositorios, modelos y cliente API Kotlin. | **Cumple** |
| Abrir en Android Studio / herramientas Android compatibles | Proyecto Gradle con `settings.gradle.kts`, módulo `app`, manifest y Gradle Wrapper; versiones fijadas en rango compatible. | **Preparado; falta sincronización local** |
| Interfaz organizada | Portal con ilustración WebP optimizada, paleta unificada, CTA “Reservar una cita”, accesos a “Mis reservas” y “Soy negocio”, catálogo integrado al flujo de reserva y estados accesibles. | **Cumple parcialmente**: la UI existe, requiere validación visual en distintos tamaños y dispositivos. |
| Al menos cuatro pantallas | `PortalMarketplaceActivity`, `AccesoRemotoActivity`, `RegistroNegocioRemotoActivity`, `MarketplaceRemotoActivity`, `PanelNegocioRemotoActivity`, más actividades heredadas. | **Cumple en estructura** |
| Navegación entre pantallas | Navegación nativa mediante `Intent`; el flujo conserva negocio y servicio seleccionados durante el inicio de sesión y vuelve a reservas cuando corresponde. | **Implementada; falta recorrido de aceptación en dispositivo** |
| Formularios con validación | Validación de correo, contraseña, teléfono, negocio, categoría, modalidad y selección obligatoria de turno; el servidor vuelve a validar. | **Cumple** |
| CRUD cuando corresponda | Citas locales heredadas tienen operaciones de agenda; el flujo central permite crear, consultar, cancelar y cambiar estados de reservas; servicios, trabajadores y recursos tienen altas/gestión. | **Cumple parcialmente**: el ciclo de reservas está cubierto, pero no todas las entidades exponen edición/eliminación completa. |
| Almacenamiento persistente local o remoto | PostgreSQL guarda cuentas, negocios, servicios, horarios, trabajadores, recursos y reservas; SQLite conserva el historial anterior del teléfono. | **Cumple** |
| Código organizado y comentarios | Separación actual `view/`, `data/remote/`, `data/local/`, `data/repository/`, `model/`; puentes antiguos simplificados para que apunten al flujo PostgreSQL y comentarios en la integración de reservas. | **Cumple parcialmente**: el flujo remoto aún concentra lógica en Activities; falta extraer ViewModels y casos de uso por etapas. |
| Evitar pantallas estáticas | Los formularios llaman a la API, el catálogo consulta páginas del servidor, la reserva consulta disponibilidad real y el negocio gestiona reservas. | **Cumple por implementación**; requiere prueba manual en emulador. |
| Pruebas | 41 pruebas Node.js pasan, incluidos validadores, esquema, idempotencia y cursor de catálogo. Existen pruebas unitarias/instrumentadas Android en el proyecto. | **Parcial**: las 38 Node pasan; no se ejecutó Gradle ni las pruebas Android instrumentadas aquí. |
| Documentación y sustentación | README, guía de Android Studio, contrato API, OpenAPI, arquitectura y este informe. | **Cumple en documentación base**; el estudiante debe estudiar y explicar el código. |
| Firebase/API REST, autenticación, mapas o notificaciones (opcionales) | API REST Node.js, autenticación JWT y PostgreSQL. | **Ampliación API y autenticación implementada**; mapas, cámara, push y Firebase no están implementados ni son obligatorios. |

## Pantallas que se pueden mostrar en la sustentación

1. **Portal principal:** identidad del sistema, botón principal “Reservar una cita”, accesos a “Mis reservas” y “Soy negocio”, categorías y promociones.
2. **Acceso y registro de cliente:** formulario con validación y sesión remota.
3. **Registro de negocio:** categoría, datos comerciales, ubicación y modalidades.
4. **Elegir negocio:** primer paso del recorrido de reserva, con filtros opcionales y carga incremental.
5. **Servicios del negocio:** selección de un servicio o producto dentro de la misma pantalla.
6. **Reserva:** fecha, turnos reales, trabajador/recurso y envío de solicitud; el formulario conserva la selección durante el acceso.
7. **Mis reservas:** consulta y cancelación de las reservas de la cuenta.
8. **Panel del negocio:** catálogo, horarios, trabajadores/recursos y gestión de reservas.

Algunas funciones se presentan como formularios o diálogos dentro de una Activity; la actividad académica pide pantallas funcionales, no necesariamente una Activity independiente por cada operación.

## Mejoras recientes de interfaz y rendimiento

- Ilustración de portada WebP optimizada y descripción accesible.
- Textos del portal y catálogo orientados a acciones claras: explorar, consultar horarios y reservar.
- El catálogo carga 24 negocios por página y limita la vista a 120 tarjetas por búsqueda para no acumular vistas indefinidamente.
- La portada ya no incluye la entrada independiente “Buscar”; su acción principal abre el recorrido de reserva. Los filtros opcionales quedan dentro de la selección de negocio.
- El detalle del negocio se presenta dentro de la misma Activity y los turnos se seleccionan dentro del formulario, reduciendo saltos entre pantallas y diálogos.
- El inicio de sesión conserva el negocio y el servicio elegidos, y recupera el destino pendiente tras autenticarse.
- Los errores de conectividad del emulador explican cómo iniciar el backend y permiten reintentar sin mostrar una excepción técnica modal.
- Las pantallas antiguas de registro/catálogo/panel fueron reducidas a puentes hacia las pantallas remotas, eliminando formularios locales inalcanzables y mensajes obsoletos.

## Pruebas realizadas en esta entrega

| Comando / verificación | Resultado | Alcance |
|---|---|---|
| `cd backend && npm test` | **41/41 aprobadas** | Pruebas Node.js de validación, paginación, seguridad y comprobaciones estáticas; no crean una base PostgreSQL real. |
| `cd backend && npm run check` | **Aprobado** | Sintaxis de `server.js`, `domain.js`, `security.js`, `db.js` y `pagination.js`. |
| Validación XML | **19/19 archivos bien formados** | Verificación sintáctica XML; no sustituye AAPT2 ni el proceso de recursos de Gradle. |
| Sincronización Gradle / `assembleDebug` | **No ejecutado** | El entorno de preparación no dispone de Android SDK y no puede descargar el Wrapper por red. |
| Emulador / teléfono | **No ejecutado** | Requiere Android Studio y SDK en el equipo del estudiante. |
| Migraciones / concurrencia PostgreSQL | **No ejecutado con DB real** | Requiere Docker/PostgreSQL de pruebas; las pruebas de texto SQL no son una prueba de integración. |

## Qué debes demostrar para cerrar el cumplimiento

Antes de la presentación, verifica en tu equipo:

1. Abrir la carpeta raíz en Android Studio y terminar **Gradle Sync** sin errores.
2. Ejecutar `:app:assembleDebug` y `:app:testDebugUnitTest`.
3. Instalar y abrir en un emulador o teléfono.
4. Completar un registro e inicio de sesión.
5. Registrar/publicar un negocio y configurar su horario.
6. Crear un servicio, consultar turnos y realizar una reserva.
7. Consultar la reserva como cliente y gestionarla como negocio.
8. Repetir una solicitud con el mismo `Idempotency-Key` y verificar que no se duplica.

Cuando todos esos pasos se ejecuten y queden registrados, tendrás evidencia práctica para sostener el cumplimiento de los objetivos de la asignatura.

## Conclusión académica

**La base funcional del proyecto sí corresponde a la actividad y supera el mínimo de cuatro pantallas en estructura.** La parte pendiente no es inventar más módulos: es conseguir una compilación limpia, ejecutar el recorrido en Android real/emulado, validar PostgreSQL y preparar una demostración que el estudiante pueda explicar paso a paso.
