# Cambios — Iteración 6 / V13

## Experiencia y navegación

- Se conserva el portal con acciones principales: Reservar una cita, Mis reservas y Soy negocio.
- Los filtros de búsqueda se mantienen dentro del primer paso de reserva; no se reintroduce una opción Buscar independiente.
- Al cambiar de catálogo a detalle o Mis reservas se ocultan filtros y paginación, se limpia la lista anterior y se muestra el estado de la sección actual.
- El botón Atrás del sistema vuelve al catálogo desde secciones internas del flujo remoto.
- Se evita que una respuesta asíncrona antigua cambie la sección o los datos que el usuario está viendo.
- Se solicita confirmación antes de cancelar una reserva.

## Estabilidad y reservas

- La consulta de disponibilidad descarta respuestas de fechas anteriores y de diálogos cerrados.
- Los filtros nuevos invalidan solicitudes de catálogo anteriores.
- Se capturan valores de controles en el hilo principal antes de iniciar operaciones de red.
- El panel del negocio ignora respuestas asociadas a otro negocio y evita acciones administrativas repetidas mientras carga.
- La reserva de recojo requiere una fecha y hora seleccionadas explícitamente y futuras.
- Las solicitudes de cancelación concurrentes para el mismo ID se evitan en la pantalla.
- Las tarjetas informativas ya no se presentan como controles interactivos sin acción.

## Sistema visual

- Se centralizan los colores de la aplicación en `UnamadPalette`.
- Las pantallas remotas usan `UnamadBaseActivity` para mantener coherencia de barras del sistema.
- Se uniforman componentes de campo, tarjeta, botón, estado y espaciado en el recorrido remoto.
- Se mantienen mensajes en español orientados a la acción y estados de reserva comprensibles.

## Rendimiento de interfaz

- El cliente API usa un dispatcher I/O compartido con cuatro hilos y cola limitada.
- Las tareas se ejecutan fuera del hilo UI y los callbacks regresan al hilo principal.
- El rechazo por cola saturada se traduce en un error recuperable y no bloquea silenciosamente el botón.

## Verificación

- `npm run check`: aprobado.
- `npm test`: 59/59 aprobadas en la última ejecución.
- Las pruebas incluyen aserciones estáticas de regresión del flujo Android; no sustituyen compilación, pruebas instrumentadas ni integración real con PostgreSQL.
- No se produjo APK en este entorno. La compilación Android debe confirmarse desde Android Studio con SDK configurado.

Ver [informe detallado de auditoría](docs/audit/ITERACION_6_AUDITORIA_FUNCIONAL_UI.md).
