# Abrir Citas y Reservas en Android Studio

## 1. Preparar el entorno

Instala:

- Android Studio actualizado.
- JDK 17 o superior (puedes seleccionar **Embedded JDK** en Android Studio).
- Android SDK Platform 35 y las Build Tools que sugiera el SDK Manager.
- Para el backend: Node.js 22, Docker Desktop y Docker Compose v2.

No copies ningún `local.properties` de otra computadora. Android Studio lo genera con la ruta del SDK de tu equipo.

## 2. Abrir el proyecto

1. Extrae el ZIP completo en una carpeta normal, no dentro de `Downloads` comprimido ni dentro de otra app.
2. Abre Android Studio y selecciona **Open**.
3. Selecciona la carpeta raíz `p.unamad.sistemacitas-master`, donde están `settings.gradle.kts`, `build.gradle.kts` y `app/`. No abras únicamente `app/`.
4. En **Settings > Build, Execution, Deployment > Build Tools > Gradle**, selecciona JDK 17 o superior.
5. Abre **Tools > SDK Manager** e instala **Android SDK Platform 35**.
6. Acepta **Sync Project with Gradle Files**. La primera sincronización necesita Internet para descargar Gradle 8.13 y dependencias.

## 3. Versiones fijadas

- Android Gradle Plugin: **8.11.1**.
- Gradle Wrapper: **8.13**.
- Kotlin Gradle Plugin: **2.2.21**.
- JDK: **17 o superior**.
- `compileSdk = 35`, `targetSdk = 35`, `minSdk = 24`.

La versión de AGP está fijada a 8.11.1 porque Kotlin Gradle Plugin 2.2.21 declara compatibilidad plena hasta AGP 8.11.1. AGP 8.11 requiere Gradle 8.13. Consulta la matriz oficial de [Kotlin](https://kotlinlang.org/docs/gradle-configure-project.html) y [Android Gradle Plugin](https://developer.android.com/build/releases/about-agp).

## 4. Ejecutar solo la app en el emulador

1. En Android Studio abre **Device Manager**.
2. Crea o inicia un dispositivo virtual con Android 7.0/API 24 o superior. Para las pruebas actuales es recomendable una imagen reciente con API 35.
3. Selecciona la configuración `app` en la barra superior.
4. Pulsa **Run**.

También puedes compilar desde la Terminal de Android Studio, situada en la raíz del proyecto:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

El APK Debug, si la compilación termina correctamente, queda en:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## 5. Iniciar backend y PostgreSQL (para login y reservas reales)

La app puede abrir sus pantallas sin que el backend esté activo, pero el login, catálogo remoto y las reservas requieren API y PostgreSQL disponibles.

En PowerShell, desde la raíz del proyecto:

```powershell
cd backend
Copy-Item .env.example .env
notepad .env
```

Edita `.env`: cambia todas las contraseñas, JWT secrets y demás valores de ejemplo por secretos locales fuertes. No compartas ni subas ese archivo.

Luego ejecuta:

```powershell
docker compose up --build -d
docker compose ps
Invoke-RestMethod http://localhost:8080/health
```

El endpoint de salud debe devolver `status: ok` y `database: connected`. Mantén Docker Desktop y los contenedores activos durante las pruebas. Consulta [backend/README.md](backend/README.md) para migraciones, respaldos, seguridad y limitaciones conocidas.

### URL del emulador

En Debug la app usa por defecto `http://10.0.2.2:8080`. Esa IP permite al emulador Android llegar al puerto 8080 del PC anfitrión. No reemplaza el inicio del backend.

### URL en un teléfono físico

El teléfono debe estar en la misma red que la computadora que ejecuta Docker. Usa la IP LAN real del PC, por ejemplo:

```powershell
.\gradlew.bat :app:assembleDebug -PmarketplaceApiBaseUrl=http://192.168.1.20:8080
```

Sustituye `192.168.1.20` por la IP real del equipo y permite el puerto en el firewall local si corresponde.

## 6. Verificaciones recomendadas

Desde la raíz del proyecto:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

`connectedDebugAndroidTest` requiere emulador iniciado o teléfono conectado con depuración USB.

Desde `backend/`:

```powershell
npm test
npm run check
```

Para validar una base limpia, usa Docker con un volumen de prueba. No ejecutes `docker compose down -v` sobre una base con datos que quieras conservar. Las migraciones de inicialización no se vuelven a aplicar automáticamente en volúmenes existentes.

## 7. Compilación Release

Release exige una dirección HTTPS del backend real. No compiles una versión de producción con localhost ni con el host de ejemplo:

```powershell
.\gradlew.bat :app:assembleRelease -PmarketplaceApiBaseUrl=https://api.tu-dominio-real
```

La compilación release se detiene si no se proporciona una URL HTTPS.

## 8. Si aparece `Failed to connect to /10.0.2.2:8080`

1. Comprueba que Docker Desktop esté iniciado.
2. Ejecuta `docker compose ps` dentro de `backend/` y confirma que `api` y `db` estén activos.
3. Comprueba `http://localhost:8080/health` desde el PC.
4. Confirma que el puerto 8080 no esté ocupado por otro proceso.
5. Si pruebas en teléfono físico, no uses `10.0.2.2`: configura la IP LAN del PC con `-PmarketplaceApiBaseUrl=...`.

## 9. Flujo manual para probar

1. Portal de inicio.
2. Registro de cliente e inicio de sesión.
3. Catálogo, búsqueda por negocio/servicio/categoría y detalle de negocio.
4. Selección de servicio, fecha y turno devuelto por la API.
5. Envío de reserva y consulta de **Mis reservas**.
6. Registro de negocio y panel de gestión de propietario.
7. Horarios, trabajadores/recursos y cambios de estado de reservas.

El distrito se conserva como parte de la dirección del negocio y del formulario de registro; no es un filtro del catálogo de reservas.
