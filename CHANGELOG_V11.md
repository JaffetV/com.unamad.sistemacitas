# Cambios — Iteración v11: pulido de interfaz y navegación

## Enfoque de producto

UNAMAD continúa siendo, ante todo, un **Sistema de Citas y Reservas**. El catálogo es el primer paso del recorrido para elegir el negocio y servicio; no se presenta como una función independiente de búsqueda en la portada.

## Portada y experiencia de cliente

- Se eliminó la barra y el acceso independiente “Buscar” de la portada.
- La acción principal ahora es “Reservar una cita”.
- Se priorizaron “Mis reservas” y “Soy negocio”.
- La navegación inferior ahora contiene Inicio, Reservar, Mis reservas y Mi negocio.
- Se actualizaron los textos para explicar el flujo: elegir negocio, revisar servicios y seleccionar un horario real.

## Recorrido de reserva y navegación

- El catálogo se presenta como “Elige dónde quieres reservar”; los filtros por negocio/categoría y distrito son opcionales dentro del paso de selección.
- Se eliminó el botón “Buscar” del catálogo: los filtros se aplican desde “Aplicar filtros” o desde la acción de teclado.
- El detalle de publicaciones se muestra dentro de la misma Activity, con una acción de regreso explícita; ya no abre un segundo diálogo de selección de servicios.
- Los turnos se muestran dentro del formulario de reserva; desapareció el diálogo adicional de selección de horarios.
- El formulario de reserva usa un contenedor desplazable para pantallas pequeñas y muchos turnos.
- Después de confirmar, el usuario puede ir directamente a “Mis reservas”.
- Si hace falta iniciar sesión durante una reserva, se conserva el negocio y servicio elegidos; al autenticar, la app vuelve al formulario correspondiente.
- Si el usuario entra a “Mis reservas” sin sesión, al autenticarse vuelve a sus reservas, no al flujo de administración del negocio.

## Conectividad del backend

- El catálogo ya no muestra un diálogo modal con el error crudo `Failed to connect...`.
- Se muestra una explicación en pantalla y la acción “Reintentar conexión”.
- Los errores de conexión explican que `10.0.2.2` es el host visto desde el emulador y que Docker/API debe estar activo en el PC anfitrión.
- Se ampliaron `backend/README.md` y `ABRIR_EN_ANDROID_STUDIO.md` con las verificaciones de `/health` y la IP para teléfonos físicos.

## Identidad visual

- Se unificó la paleta de las pantallas generadas dinámicamente: azul marino, verde azulado, menta, fondos claros y bordes suaves.
- Botones con esquinas consistentes, contraste de texto y estados accesibles.
- Tema Android con barra de estado clara y barra de navegación en azul marino.
- Se conserva la ilustración WebP optimizada de portada y su descripción accesible.

## Validación de esta iteración

- `cd backend && npm test`: **41/41 pruebas aprobadas**.
- `cd backend && npm run check`: aprobado.
- La revisión estática incluye que no exista “Buscar” en la portada, que se preserve negocio/servicio al autenticarse y que el error del emulador tenga instrucciones de recuperación.
- No se afirma que `assembleDebug` esté aprobado: este entorno no tiene Android SDK ni una distribución Gradle disponible en caché para compilar sin descarga.
