# Cambios — Iteración v12: recorrido de reserva, identidad visual y navegación

## Objetivo de producto

UNAMAD se mantiene como un sistema de citas y reservas. La portada debe orientar al usuario a reservar, no presentar la búsqueda como una función aislada.

## Experiencia de usuario

- La portada presenta como acción principal **Reservar una cita** y conserva accesos visibles a **Mis reservas** y **Soy negocio**.
- El acceso independiente **Buscar** no aparece en portada ni en la navegación inferior.
- El catálogo se presenta como paso 1 del recorrido de reserva. Los filtros por nombre, servicio, categoría y distrito son opcionales y están dentro de ese recorrido.
- El detalle de negocio/servicio y la selección de turnos se mantienen dentro del flujo de reserva, con controles de retorno explícitos.
- La selección de negocio y servicio se conserva al pasar por autenticación.
- Las respuestas antiguas de API se descartan cuando el usuario cambia de sección, evitando que una respuesta tardía reemplace la pantalla actual.

## Identidad visual

- Se unificó la paleta azul marino, verde azulado, menta y fondos claros en portada y pantallas remotas.
- Botones, tarjetas, campos, textos y fondos utilizan tokens compartidos `UnamadPalette`.
- Se mejoró el contraste y la jerarquía visual de títulos, estados, acciones y tarjetas.
- Se incrementó la versión Android a `1.5` (`versionCode 6`).

## Conectividad y mensajes

- La dirección debug `http://10.0.2.2:8080` se conserva porque es el host del PC visto desde el emulador.
- El catálogo informa el problema dentro de la pantalla y ofrece reintentar, en lugar de abrir un diálogo con `Failed to connect`.
- La explicación incluye el inicio del backend, la comprobación `/health` y la diferencia entre emulador y dispositivo físico.

## Calidad y validación

- El test de JWT modifica ahora un carácter de la firma en lugar del último carácter Base64URL, evitando una falsa alteración por bits de relleno.
- Se añadieron pruebas de protección frente a respuestas asíncronas obsoletas y de uso de la misma paleta en las pantallas remotas.
- `backend/npm test`: **43/43 pruebas aprobadas**.
- `backend/npm run check`: aprobado.
- XML Android validado estáticamente.
- La compilación Android debe confirmarse en Android Studio con SDK instalado. El entorno de preparación no dispone de SDK Android ni de distribución Gradle descargada.
