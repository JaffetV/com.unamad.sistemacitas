# Solucionar `Failed to connect to /10.0.2.2:8080`

Este mensaje significa que la app Android no encuentra el backend en el puerto 8080 del PC. `10.0.2.2` es la dirección especial que usa el emulador Android para llegar al equipo anfitrión; no inicia el servidor automáticamente.

## En Windows con Docker Desktop

1. Abre Docker Desktop y espera a que indique que está ejecutándose.
2. Abre PowerShell en la carpeta `backend` del proyecto.
3. Si todavía no tienes `.env`, copia `.env.example` a `.env` y cambia todos los valores de ejemplo por secretos propios.
4. Inicia los servicios:

   ```powershell
   docker compose up --build -d
   docker compose ps
   ```

5. Comprueba el backend:

   ```powershell
   curl.exe http://localhost:8080/health
   ```

   La respuesta debe indicar `status: ok` y `database: connected`.
6. Mantén Docker Desktop abierto mientras pruebas la aplicación.
7. Cierra y vuelve a abrir la pantalla **Reservar** o pulsa **Reintentar conexión**.

## Si sigue sin conectar

- Comprueba `docker compose ps`; los servicios `api` y `db` deben estar activos.
- Consulta los logs: `docker compose logs --tail=100 api db`.
- Si el puerto 8080 ya lo usa otro programa, cambia el puerto publicado en `backend/docker-compose.yml` y configura el mismo puerto en Gradle mediante `-PmarketplaceApiBaseUrl=http://10.0.2.2:PUERTO`.
- Si usas un teléfono físico, no utilices `10.0.2.2`: configura la IP LAN del PC, por ejemplo `-PmarketplaceApiBaseUrl=http://192.168.1.20:8080`, y permite el puerto en el firewall de Windows.

No uses `.env.example` con sus contraseñas de ejemplo en producción. No ejecutes `docker compose down -v` para intentar reparar la conexión: esa opción elimina el volumen de datos de PostgreSQL.
