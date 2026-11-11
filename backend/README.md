# Backend — UNAMAD Sistema de Citas y Reservas

API REST Node.js 22 + PostgreSQL 16. **El backend y PostgreSQL son la autoridad de todas las citas y reservas compartidas** entre clientes, trabajadores y negocios. Android consulta disponibilidad y envía solicitudes a esta API; SQLite no confirma ni crea reservas compartidas.

## 1. Inicio local

Requisitos: Docker Engine/Desktop y Docker Compose v2.

```bash
cd backend
cp .env.example .env
# Editar .env y definir contraseñas/secretos únicos y fuertes.
docker compose up --build -d
docker compose ps
curl http://localhost:8080/health
```

La API escucha en `8080`. En producción debe publicarse exclusivamente mediante HTTPS. Nunca subir `.env`.

En Windows también puedes ejecutar `./iniciar-backend-windows.ps1` desde PowerShell dentro de `backend`. El script crea `.env` desde el ejemplo solo si no existe, se detiene si detecta secretos de ejemplo, inicia Docker Compose y comprueba `/health`.

### Conectar el emulador Android

La dirección debug predeterminada de Android es `http://10.0.2.2:8080`: es la dirección del PC anfitrión vista desde el emulador, no una dirección que levante el servidor por sí sola. El mensaje `Failed to connect to /10.0.2.2:8080` indica que el emulador no encontró un proceso escuchando en ese puerto o que Docker no publicó el puerto.

En Windows PowerShell, desde la carpeta raíz del proyecto:

```powershell
cd backend
Copy-Item .env.example .env  # solo la primera vez
notepad .env                 # reemplaza todas las contraseñas/secretos de ejemplo
docker compose up --build -d
docker compose ps
curl http://localhost:8080/health
```

No uses los valores de ejemplo para producción. Espera a que `db` y `api` aparezcan activos y que `/health` devuelva `status: ok` y `database: connected`. Mantén Docker ejecutándose mientras pruebas la app. Si el puerto 8080 está ocupado, libera ese puerto o cambia el mapeo y la propiedad `marketplaceApiBaseUrl` en Gradle. Para un teléfono físico usa la IP LAN del PC, no `10.0.2.2`.

## 2. Migraciones PostgreSQL

En un volumen nuevo, Docker ejecuta en orden `001_bootstrap.sh`, `002_marketplace.sql`, `003_appointment_resources_idempotency.sql` y `004_marketplace_pagination.sql`.

En una instalación que ya tiene datos, **agregar el archivo al directorio de inicialización no ejecuta la migración sobre un volumen existente**. Primero identifica qué migraciones ya aplicaste y crea un respaldo. No vuelvas a ejecutar una migración que ya se aplicó:

```bash
cd backend
# Asegura que el contenedor de PostgreSQL esté activo y respalda antes de migrar.
docker compose exec -T db pg_dump -U marketplace_owner -d marketplace -Fc > ../backup_pre_migracion.dump

# Si tu base todavía está en el esquema 002, ejecuta 003 y luego 004:
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U marketplace_owner -d marketplace < db/003_appointment_resources_idempotency.sql
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U marketplace_owner -d marketplace < db/004_marketplace_pagination.sql

# Si 003 ya fue aplicada anteriormente, ejecuta únicamente 004:
# docker compose exec -T db psql -v ON_ERROR_STOP=1 -U marketplace_owner -d marketplace < db/004_marketplace_pagination.sql
```

Ajusta usuario/base si tu `.env` utiliza otros nombres. No ejecutes `docker compose down -v`: elimina el volumen y los datos. Aplica únicamente las migraciones pendientes, en orden, y verifica el resultado antes de desplegar el backend nuevo.

## 3. Modelo de citas y reservas

- `business_staff`: trabajadores activos/inactivos del negocio.
- `business_resources`: sillas, cabinas, equipos o capacidad de atención. La migración crea un recurso virtual `Atención general 1` para cada negocio existente.
- `reservations.assigned_staff_id` / `assigned_resource_id`: asignación de cada cita.
- La restricción GiST impide solapamientos sobre el mismo trabajador o recurso. Trabajadores/recursos diferentes pueden atender en paralelo.
- Si el negocio tiene trabajadores activos, la asignación automática elige un trabajador disponible. Si no tiene trabajadores activos, utiliza recursos de atención. El administrador puede añadir más de un recurso para aumentar capacidad.
- La jornada semanal del negocio es el límite global. Aún no hay horario individual por trabajador, vacaciones ni excepciones/feriados por fecha; todos los trabajadores heredan por ahora el horario del negocio.
- Cada cita se revalida en el servidor al crearla. La consulta previa de disponibilidad es informativa, no una garantía: PostgreSQL y la asignación transaccional vuelven a comprobar el turno antes del INSERT.
- Los instantes se guardan como `timestamptz`; la fecha de disponibilidad y la jornada se interpretan con la zona IANA del negocio (`America/Lima` por defecto).

## 4. Idempotencia

`POST /api/v1/me/reservations` requiere `Idempotency-Key` (8–128 caracteres seguros) o `body.idempotencyKey`.

- PostgreSQL conserva la clave por cliente con un índice único parcial.
- El servidor toma un advisory transaction lock por cliente+clave antes de buscar una operación previa.
- Una repetición con el mismo contenido devuelve la reserva y código originales; no crea otro pago ni vuelve a descontar stock.
- Reutilizar la misma clave con otro contenido devuelve `409 IDEMPOTENCY_KEY_REUSED`.
- Android persiste la clave pendiente junto a la huella del payload en preferencias privadas. Se conserva si se pierde la respuesta de red y se borra después de una respuesta exitosa.

## 5. Endpoints principales

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/v1/businesses?limit=24&cursor=` | Catálogo público por páginas, cursor keyset (24 por defecto; máximo 50) |
| GET | `/api/v1/businesses/{id}/availability?listingId={listingId}&date=AAAA-MM-DD` | Turnos reales de servicio, con trabajador o recurso asignado |
| GET/POST/PATCH | `/api/v1/businesses/{id}/staff[/{staffId}]` | Consultar, crear o activar/desactivar trabajadores |
| GET/POST/PATCH | `/api/v1/businesses/{id}/resources[/{resourceId}]` | Consultar, crear o activar/desactivar puestos/recursos |
| POST | `/api/v1/me/reservations` | Crear/repetir cita o pedido con idempotencia |
| GET | `/api/v1/me/reservations` | Ver las reservas del cliente y su asignación |
| GET | `/api/v1/businesses/{id}/reservations` | Agenda del negocio, incluye trabajador/recurso |

Los endpoints de gestión de personal/recursos requieren membresía de propietario o administrador. La disponibilidad solo devuelve turnos de negocios públicos y fechas no pasadas.

## 6. Catálogo y crecimiento

El catálogo público usa paginación keyset por `(created_at, id)`, con un tamaño predeterminado de 24 y máximo de 50 elementos por página. La API no necesita descargar el conjunto completo ni usar `OFFSET`, y Android ofrece una acción explícita para cargar más resultados. La migración 004 agrega índices parciales por orden de creación, categoría y distrito, además de índices trigram para las búsquedas parciales por nombre y descripción. El pool se configura con `PG_POOL_MAX` (15 por defecto); al desplegar varias réplicas, el límite debe presupuestarse globalmente y puede requerir PgBouncer.

Los índices y la paginación reducen el trabajo por petición, pero no certifican capacidad para cualquier pico. Antes de producción se requieren pruebas de carga, métricas, límites de conexiones, revisión de consultas con `EXPLAIN (ANALYZE, BUFFERS)` y una política de escalado.

## 7. Pruebas sin base de datos

```bash
npm test
npm run check
```

Estas pruebas cubren validaciones de dominio y comprobaciones estáticas de migraciones. No sustituyen una prueba de integración en PostgreSQL real.

## 8. Seguridad operativa

- `marketplace_app` es un rol runtime separado, sin `BYPASSRLS` ni privilegios de propietario.
- RLS continúa habilitado para el rol de API. La migración 003 quita `FORCE ROW LEVEL SECURITY` solo a `reservations` para que la función `SECURITY DEFINER` de disponibilidad, propiedad del dueño de las tablas, pueda comprobar ocupación sin exponer datos personales. La función devuelve únicamente un booleano y su ejecución se concede al rol de API.
- No usar credenciales de `.env.example` en producción.
- Configurar `ALLOWED_ORIGINS` con dominios conocidos y `TRUST_PROXY=true` únicamente detrás de un proxy controlado.
- Pagos permanecen `PENDIENTE` hasta confirmación real de una pasarela/proveedor.

## 9. Límites actuales

No se ha integrado pasarela de pago, envío de correo/SMS, notificaciones push, recuperación de contraseña ni calendario individual/excepciones. La disponibilidad por trabajador/recurso y la idempotencia ya tienen implementación en código y migración, pero en esta entrega no se pudo ejecutar una prueba real contra PostgreSQL porque el entorno de desarrollo no contiene Docker ni `psql`.

