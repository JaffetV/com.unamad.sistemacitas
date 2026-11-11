# Contrato de API — UNAMAD Citas v1

Base local: `http://localhost:8080/api/v1`. Android Emulator: `http://10.0.2.2:8080/api/v1`. En producción debe publicarse exclusivamente detrás de HTTPS.

Todas las respuestas exitosas usan `{ "data": ... }` salvo los mensajes informativos. El catálogo de negocios también devuelve `page: { limit, hasMore, nextCursor }`; envía `nextCursor` como parámetro `cursor` para solicitar la página siguiente. Los errores usan `{ "error": { "code": "...", "message": "..." } }`. Las rutas privadas requieren `Authorization: Bearer <token>`.

## Cuentas y autenticación

| Método | Ruta | Autenticación | Función |
|---|---|---|---|
| POST | `/auth/register/customer` | No | Registro gratuito de cliente |
| POST | `/auth/register/business` | No | Crea usuario propietario, negocio, modalidades y prueba de 14 días |
| POST | `/auth/login` | No | Inicia sesión y entrega JWT de 12 horas |
| GET | `/me` | Sí | Perfil de la cuenta |
| GET | `/me/businesses` | Sí | Negocios en los que participa |

El usuario propietario existente puede crear otro negocio con `POST /businesses`.

### Registro gratuito

```json
{
  "fullName": "Ana Pérez",
  "email": "ana@example.com",
  "phone": "999888777",
  "password": "una-clave-de-10-caracteres"
}
```

### Alta de negocio

```json
{
  "fullName": "Carlos Rojas",
  "email": "carlos@example.com",
  "ownerPhone": "999888777",
  "password": "una-clave-de-10-caracteres",
  "business": {
    "name": "Barbería Centro",
    "categoryId": "UUID_DEVUELTO_POR_CATEGORIES",
    "description": "Cortes y arreglo de barba",
    "phone": "999888777",
    "department": "Madre de Dios",
    "province": "Tambopata",
    "district": "Puerto Maldonado",
    "address": "Av. Principal 123",
    "modes": ["CITA", "RECOJO"]
  }
}
```

## Marketplace público

| Método | Ruta | Función |
|---|---|---|
| GET | `/categories` | Categorías activas |
| GET | `/businesses?q=&categoryId=&district=&limit=24&cursor=` | Buscar negocios publicados por páginas (cursor keyset; máximo 50 por respuesta) |
| GET | `/businesses/{businessId}` | Detalle público del negocio |
| GET | `/businesses/{businessId}/listings` | Catálogo activo |
| GET | `/businesses/{businessId}/hours` | Horario semanal |
| GET | `/businesses/{businessId}/availability?listingId={listingId}&date=AAAA-MM-DD` | Turnos reales por trabajador/recurso, calculados según duración y jornada del negocio; rechaza fechas pasadas |

## Operación del negocio

| Método | Ruta | Permiso | Función |
|---|---|---|---|
| POST | `/businesses` | Usuario | Crea otro negocio para la cuenta autenticada |
| POST | `/businesses/{businessId}/listings` | Owner/Manager | Publica servicio o producto |
| PUT | `/businesses/{businessId}/hours` | Owner/Manager | Reemplaza el horario semanal |
| GET | `/businesses/{businessId}/staff` | Miembro autenticado o público si negocio visible | Lista trabajadores activos |
| POST | `/businesses/{businessId}/staff` | Owner/Manager | Agrega un trabajador |
| PATCH | `/businesses/{businessId}/staff/{staffId}` | Owner/Manager | Activa o desactiva trabajador; no permite desactivar con citas futuras/en curso |
| GET | `/businesses/{businessId}/resources` | Miembro autenticado o público si negocio visible | Lista recursos activos |
| POST | `/businesses/{businessId}/resources` | Owner/Manager | Agrega puesto, cabina, equipo o capacidad |
| PATCH | `/businesses/{businessId}/resources/{resourceId}` | Owner/Manager | Activa o desactiva recurso; conserva al menos uno activo |
| GET | `/businesses/{businessId}/reservations` | Miembro activo | Consulta solicitudes del negocio |
| PATCH | `/businesses/{businessId}/reservations/{reservationId}/status` | Miembro activo | Cambia estado respetando transiciones |

Estados: `SOLICITADA -> CONFIRMADA -> LISTA -> COMPLETADA`; se permiten cancelaciones según etapa. `NO_SHOW` solo desde `CONFIRMADA`.

## Cliente y reservas

| Método | Ruta | Función |
|---|---|---|
| POST | `/me/reservations` | Solicita una cita o pedido de recogida |
| GET | `/me/reservations` | Consulta las reservas propias |
| POST | `/me/reservations/{reservationId}/cancel` | Cancela una reserva propia permitida |
| POST | `/me/favorites` | Guarda negocio favorito |
| GET | `/me/favorites` | Lista favoritos |

Ejemplo de solicitud:

```json
{
  "businessId": "UUID_NEGOCIO",
  "listingId": "UUID_PUBLICACION",
  "mode": "CITA",
  "dateTime": "2026-12-10T10:30:00-05:00",
  "quantity": 1,
  "paymentMethod": "EN_LOCAL",
  "note": "Prefiero atención por la mañana"
}
```

`POST /me/reservations` requiere el encabezado `Idempotency-Key` (8–128 caracteres seguros). Android conserva una clave pendiente entre reintentos de red. La base de datos hace única la combinación cliente+clave; repetir la misma clave y el mismo contenido devuelve el registro original; cambiar el contenido con la misma clave devuelve `409 IDEMPOTENCY_KEY_REUSED`.

Para una cita, el cliente debe elegir uno de los turnos de disponibilidad. El POST envía además `staffId` o `resourceId` devuelto por el endpoint. PostgreSQL vuelve a validar la asignación en la transacción. El índice de exclusión por trabajador/recurso permite reservar simultáneamente asignados distintos y bloquea solapamientos del mismo asignado.

Para pedidos de recogida se usa `mode: "RECOJO"` y `dateTime` representa la hora solicitada. El stock se descuenta de forma transaccional cuando existe control de stock. El pago permanece pendiente hasta una confirmación real.

## Códigos relevantes

- `401 UNAUTHENTICATED` / `INVALID_CREDENTIALS`: sesión o credenciales inválidas.
- `403 BUSINESS_FORBIDDEN`: no pertenece al negocio con el rol requerido.
- `402 SUBSCRIPTION_REQUIRED`: el negocio no tiene suscripción vigente para publicar.
- `404 BUSINESS_NOT_FOUND` / `LISTING_NOT_FOUND` / `RESERVATION_NOT_FOUND`.
- `409 SLOT_UNAVAILABLE`: el horario acaba de ocuparse.
- `409 STOCK_UNAVAILABLE`: no hay stock suficiente.
- `409 INVALID_STATE_TRANSITION`: cambio de estado no permitido.
- `400 OUTSIDE_BUSINESS_HOURS`: fecha/hora fuera del horario configurado.

## Prueba rápida

```bash
curl http://localhost:8080/health
curl http://localhost:8080/api/v1/categories
```

La documentación OpenAPI inicial se encuentra en `openapi.yaml`.
