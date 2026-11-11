# Prueba manual en Android Studio — Iteración 6

Esta lista complementa las pruebas Node.js. Debe ejecutarse con el backend y PostgreSQL iniciados y un emulador Android activo. Marcar una prueba solo después de observar el resultado en pantalla.

## Preparación

- [ ] Sincronizar Gradle sin errores.
- [ ] Ejecutar `:app:assembleDebug`.
- [ ] Instalar en emulador API 24 o superior.
- [ ] Iniciar el backend y comprobar `http://localhost:8080/health` desde el PC.
- [ ] Abrir la app y verificar que el emulador llega al backend mediante `10.0.2.2:8080`.

## Flujo de cliente

- [ ] La portada presenta Reservar una cita, Mis reservas y Soy negocio; no aparece una opción Buscar independiente.
- [ ] Reservar abre el catálogo y muestra filtros opcionales dentro del primer paso.
- [ ] Cambiar el texto de filtro rápidamente dos veces: deben mostrarse resultados de la última búsqueda, no resultados atrasados.
- [ ] Abrir un negocio: los filtros y el botón de paginación desaparecen; se muestran solo sus servicios.
- [ ] Volver a negocios con el botón visible y con el botón físico Atrás; ambos deben regresar al catálogo sin duplicar pantallas.
- [ ] Abrir un servicio, cambiar la fecha durante la consulta y comprobar que solo aparecen turnos para la última fecha elegida.
- [ ] Cerrar el formulario mientras carga disponibilidad; la respuesta tardía no debe abrir ni modificar otra pantalla.
- [ ] Intentar confirmar sin seleccionar un turno; la app debe pedir que selecciones uno.
- [ ] Confirmar una reserva válida; debe aparecer el código/estado devuelto por el servidor.
- [ ] Abrir Mis reservas; no deben permanecer visibles tarjetas ni controles del catálogo.
- [ ] Cancelar una reserva: debe solicitar confirmación; al confirmar, actualizar el estado en servidor.
- [ ] Mientras cancela, navegar a otra sección; la respuesta no debe sacarte de esa sección.
- [ ] Repetir el recorrido con la sesión cerrada; tras iniciar sesión debe retomarse la operación elegida, sin perder negocio/servicio.

## Flujo de negocio

- [ ] Entrar como propietario y seleccionar un negocio.
- [ ] Pulsar varias veces una acción administrativa durante la carga; no debe crear duplicados.
- [ ] Cambiar entre negocios (si la cuenta tiene más de uno); la respuesta del negocio anterior no debe reemplazar el panel actual.
- [ ] Crear/editar servicios, trabajadores, recursos y horarios; comprobar que el resumen se actualice.
- [ ] Cambiar estado de una reserva; confirmar que las transiciones inválidas son rechazadas por el backend.

## Red y ciclo de vida

- [ ] Detener la API temporalmente; la app debe mostrar un mensaje entendible y una opción de reintento, no una excepción cruda.
- [ ] Restaurar la API y reintentar; la pantalla debe recuperarse sin reiniciar la app.
- [ ] Rotar pantalla si el emulador lo permite; revisar que no haya errores ni operaciones duplicadas.
- [ ] Pulsar Inicio y volver a la app; comprobar que no se bloquea ni muestra datos de otra sección.

## Registro de defectos

Para cada fallo, adjuntar:

1. Pantalla y acción exacta.
2. Resultado esperado y resultado observado.
3. Mensaje completo de Logcat (stack trace), ocultando tokens y datos personales.
4. Si el backend estaba activo y resultado de `/health`.
5. Modelo/API del emulador y versión de Android.
