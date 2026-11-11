# Evolución de datos — UNAMAD Sistema de Citas y Reservas

## Estado actual

- `001_bootstrap.sh`: crea el rol de runtime `marketplace_app` sin `BYPASSRLS`.
- `002_marketplace.sql`: usuarios, negocios, miembros, catálogo, horario semanal, reservas, pagos y RLS.
- `003_appointment_resources_idempotency.sql`: trabajadores, recursos, asignación de citas, claves idempotentes, huella del payload y restricciones de exclusión por trabajador/recurso.

## Decisiones de integridad

1. PostgreSQL es la autoridad única para las reservas compartidas.
2. Cada cita ocupa un trabajador o un recurso. Un mismo asignado no puede tener intervalos que se solapen.
3. Trabajadores distintos o recursos distintos pueden tener citas simultáneas.
4. Un negocio sin trabajadores individuales utiliza recursos de capacidad; se crea uno virtual durante el alta/migración.
5. El backend valida nuevamente disponibilidad y jornada al crear la reserva; la consulta de slots no reserva el turno.
6. Las claves idempotentes son únicas por cliente y se bloquean transaccionalmente para resolver reintentos concurrentes.
7. SQLite histórico se conserva, pero no replica ni confirma las reservas centrales.

## Aplicación de migración en bases existentes

1. Generar y verificar backup.
2. Confirmar que el esquema 002 está aplicado.
3. Ejecutar 003 como propietario de las tablas.
4. Validar que cada cita histórica tiene `assigned_staff_id` o `assigned_resource_id`.
5. Verificar RLS, función de disponibilidad y exclusiones.
6. Ejecutar la prueba de concurrencia con transacciones reales antes de producción.

No probar migraciones destructivas sobre información real sin backup y entorno de staging.

## Pendientes del modelo de calendario

- `business_staff_hours` o equivalente: jornada individual con herencia de horario global explícita.
- Excepciones por fecha: feriados, vacaciones, descanso, horario especial y cierre del negocio.
- Buffers configurables entre servicios.
- Recursos compuestos (por ejemplo, trabajador + cabina) cuando el negocio requiera que una cita ocupe ambos al mismo tiempo.
- Historial/auditoría de reasignación y modificación de reservas.

## Pruebas de integridad requeridas

- Dos clientes, mismo trabajador, mismo intervalo: una operación aceptada y otra con conflicto.
- Dos clientes, trabajadores diferentes, mismo intervalo: ambas aceptadas.
- Dos clientes, mismo recurso, mismo intervalo: una operación aceptada y otra con conflicto.
- Misma clave idempotente y mismo cuerpo simultáneos: mismo ID/código y un solo pago.
- Misma clave y cuerpo distinto: HTTP 409.
- Fecha fuera de jornada o fecha pasada: rechazo.
- Desactivar trabajador/recurso con cita futura: rechazo.
- Fallo de red después del commit y repetición del POST: replay sin duplicado.

Las pruebas de base de datos no se consideran ejecutadas hasta correrlas con PostgreSQL real.
