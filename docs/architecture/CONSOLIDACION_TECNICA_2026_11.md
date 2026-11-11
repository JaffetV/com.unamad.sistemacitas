# UNAMAD — Consolidación técnica y arquitectura Android

**Base:** `UNAMAD_Auditoria_Profunda_Revision.zip`  
**Fecha de trabajo:** 11 de noviembre de 2026  
**Criterio:** mantener un solo módulo Android, responsabilidades claras y PostgreSQL/backend como fuente central para el marketplace y las reservas.

## 1. Decisiones de arquitectura

No se introdujeron módulos Gradle adicionales, Compose, Hilt, Room ni nuevas capas por moda. La estructura se mantiene deliberadamente sencilla:

```text
app/src/main/java/com/example/comunamadsistemacitas/
├── domain/
│   └── validation/       # Reglas puras de validación y fechas
├── data/
│   ├── datasource/       # Adaptadores remotos/locales
│   ├── remote/           # HTTP, sesión segura, configuración
│   └── repository/        # Implementaciones de contratos
├── presentation/
│   ├── auth/             # Estado, eventos y ViewModel de autenticación
│   ├── booking/          # Estado, eventos y ViewModel de reserva
│   ├── business/         # Registro y operaciones del negocio
│   └── catalog/          # Catálogo y consultas del marketplace
└── view/
    ├── *Activity.kt      # Pantallas y renderizado
    ├── MarketplaceUiHelpers.kt
    └── UnamadDesignSystem.kt
```

Flujo de dependencias esperado:

`Activity -> ViewModel -> Repository -> RemoteDataSource -> MarketplaceApiClient -> API -> PostgreSQL`.

El dominio contiene validaciones independientes de Android. Las Activities no deben conocer el cliente HTTP, SQL ni detalles de autenticación.

## 2. Consolidación de pantallas remotas

El archivo original `MarketplaceRemoteActivities.kt` reunía cuatro Activities y utilidades comunes. Se sustituyó por:

- `AccesoRemotoActivity.kt`
- `RegistroNegocioRemotoActivity.kt`
- `MarketplaceRemotoActivity.kt`
- `PanelNegocioRemotoActivity.kt`
- `MarketplaceUiHelpers.kt`

Las utilidades de campos, tarjetas, botones, chips, fechas y mensajes de red quedan definidas una sola vez en `MarketplaceUiHelpers.kt`. Se eliminaron las copias privadas repetidas que habían quedado después de la primera separación.

Los nombres de clases y las rutas declaradas en el Manifest se conservaron para no romper la navegación existente.

## 3. Marketplace y acceso a datos

`MarketplaceBrowseViewModel` concentra consultas de negocios, detalle, publicaciones, reservas del cliente y cancelaciones. Las operaciones se ejecutan mediante el `BusinessRepositoryContract`, y las Activities consumen eventos con una clave de solicitud para descartar respuestas obsoletas.

Las solicitudes reemplazadas se cancelan. El registro de trabajos se sincroniza al insertar o retirar tareas para no depender del hilo desde el que se notifique la finalización.

Las llamadas de red siguen en la capa de datos/repositorio. No se trasladó lógica HTTP a las Activities.

## 4. Sesión y respaldo

La comprobación de existencia de sesión consulta la presencia del token guardado sin descifrarlo desde el hilo principal. El descifrado del token se mantiene en el flujo de autorización de las solicitudes.

Las preferencias que contienen sesión y claves de idempotencia se excluyen del backup en nube y de la transferencia del dispositivo. Esto evita restaurar credenciales vinculadas a Android Keystore sin su clave correspondiente.

## 5. Presentación

Se mantienen Material Components compatibles con el proyecto y la paleta comercial UNAMAD. La barra inferior usa iconos vectoriales en lugar de glifos Unicode y la portada usa vectores propios para las categorías principales.

Los helpers compartidos centralizan dimensiones, campos, botones, tarjetas, mensajes de error y chips de estado. Esto no sustituye la inspección de capturas reales: la validación visual final requiere ejecutar la aplicación en emulador o dispositivo.

## 6. Compatibilidad heredada

El proyecto aún conserva el módulo antiguo de agenda SQLite (`CitasDbHelper`, `CitaRepository` y Activities heredadas). Se mantiene como compatibilidad porque eliminarlo sin una decisión funcional podría perder pantallas o datos históricos.

La ruta principal del marketplace y las reservas remotas usa backend/PostgreSQL. Antes de retirar SQLite se debe completar una migración funcional y de datos, verificar todas las entradas de navegación y acordar qué ocurre con las instalaciones antiguas.

No se recomienda mantener indefinidamente dos fuentes activas para una misma reserva. El criterio para nuevas funciones es no crear reservas comerciales en SQLite.

## 7. Reglas prácticas para continuar

1. Una Activity renderiza, navega y delega; no ejecuta HTTP ni contiene reglas de negocio.
2. Un ViewModel mantiene estado/eventos y cancela trabajo al desaparecer su scope.
3. Un Repository coordina fuentes de datos y ejecuta red en `Dispatchers.IO`.
4. Un RemoteDataSource conoce endpoints y serialización.
5. Los modelos/validadores de dominio no dependen de Android ni de `JSONObject`.
6. Las fechas comerciales se interpretan con la zona horaria acordada por el backend (`America/Lima` para el entorno actual).
7. No marcar una reserva como confirmada localmente hasta tener respuesta satisfactoria del servidor.
8. Mantener idempotencia para reintentos de creación y tratar errores de red como resultado incierto hasta reconciliar con el servidor.

## 8. Deuda técnica conocida

- `MarketplaceRemotoActivity.kt` y `PanelNegocioRemotoActivity.kt` siguen teniendo bastante código de renderizado. La siguiente extracción debe separar vistas/renderers por sección, con pruebas y sin modificar contratos HTTP.
- Los contratos de datos aún exponen `JSONObject`/`JSONArray` en varias rutas. La migración a DTOs Kotlin debe hacerse endpoint por endpoint y conservar compatibilidad de nombres/campos.
- Hay más de una instancia de cliente HTTP porque los repositorios/datasources administran su propio ciclo de vida. Antes de convertirlo en singleton debe definirse claramente su propiedad/cierre; no se debe compartir un cliente que un ViewModel pueda cerrar mientras otro lo usa.
- La rama legacy SQLite sigue dentro del mismo módulo y debe aislarse mejor cuando se decida su destino.

## 9. Validación y límites

En la entrega consolidada se ejecutan pruebas backend con `node --test`, `npm run check`, análisis XML, referencias de Activities del Manifest y referencias de drawables. Estos controles no sustituyen Gradle, el compilador Android, Android Lint, pruebas instrumentadas, emulador, Memory Profiler ni pruebas contra PostgreSQL real.

La compilación Android no se certifica en este entorno: no dispone de Android SDK y la descarga de la distribución Gradle 8.13 falla cuando el DNS no resuelve `services.gradle.org`. No se incluye APK como si estuviera probado.
