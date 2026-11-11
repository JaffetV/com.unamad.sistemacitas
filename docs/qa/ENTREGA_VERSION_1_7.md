# Entrega de trabajo — Citas y Reservas 1.7

**Fecha:** 11 de noviembre de 2026.  
**Base:** proyecto Kotlin Android + API Node.js + PostgreSQL incluido en este ZIP.

## Cambios incluidos en esta entrega

1. Toolchain alineado: AGP 8.11.1, Gradle 8.13 y Kotlin 2.2.21. Se corrigió la expectativa de la prueba de configuración para validar la combinación real.
2. Configuración de API por build type: Debug usa `http://10.0.2.2:8080` por defecto; Release requiere una URL HTTPS explícita mediante `-PmarketplaceApiBaseUrl=...` o `MARKETPLACE_API_BASE_URL`.
3. Marca visible del launcher/portal unificada a **Citas y Reservas** sin cambiar `namespace`, `applicationId`, paquetes ni identificadores internos.
4. Eliminado el filtro Distrito del catálogo y de su estado/ViewModel/repository/consulta. Distrito sigue almacenándose y mostrándose en la dirección del negocio.
5. La consulta `q` del catálogo también busca títulos/descripciones de publicaciones activas en el backend, además de negocio/categoría.
6. Política de navegación de Marketplace: cerrar diálogo si está abierto; desde detalle volver al catálogo; desde catálogo o Mis reservas regresar al portal. El botón Inicio lleva al portal mediante `CLEAR_TOP | SINGLE_TOP`.
7. Reutilizada la ilustración local `hero_reservas` como cabecera compacta del catálogo.
8. Retirado `local.properties` específico de una estación Windows para que Android Studio genere la ruta SDK en la computadora que abra el ZIP.
9. Actualizadas guías de apertura, backend, configuración del emulador y compilación release.

## Verificaciones ejecutadas en este entorno

| Verificación | Resultado |
|---|---|
| `backend/npm test` | **87/87 aprobadas, 0 fallidas** |
| `backend/npm run check` | **Aprobado** (`node --check` en cinco módulos del servidor) |
| Parseo de XML Android | **30/30 documentos bien formados** |
| Actividades del Manifest | **14/14 clases encontradas** en fuentes Kotlin |
| Validadores de dominio Kotlin | **3 archivos compilados** con `kotlinc` |
| `:app:assembleDebug` | **Bloqueado**: el Wrapper no pudo resolver `services.gradle.org` (`UnknownHostException`); tampoco hay Android SDK configurado |
| `:app:testDebugUnitTest` | No certificado aquí |
| `:app:lintDebug` | No certificado aquí |
| `:app:connectedDebugAndroidTest` | Requiere emulador/dispositivo |
| PostgreSQL real y concurrencia | No ejecutados en este entorno |

Las pruebas Node.js son pruebas unitarias/estáticas del backend y contratos de arquitectura. La compilación Android se intentó, pero se detuvo antes de Gradle por DNS y falta Android SDK; por tanto, no se certifica APK, ejecución en emulador ni integración real con PostgreSQL.

## Limitaciones que permanecen

- Las Activities remotas aún contienen una cantidad considerable de renderizado.
- Parte de los contratos Android usa `JSONObject`/`JSONArray`; la migración a DTOs todavía es parcial.
- El SQLite heredado continúa para la agenda local antigua y necesita un plan separado de migración/retirada.
- La UI del panel requiere completar edición y desactivación de publicaciones si se mantienen esas acciones como parte de su alcance.
- No se ha certificado visualmente el comportamiento en tamaños de pantalla, rotación, fuentes grandes ni cortes de red en un dispositivo real.

## Siguiente comprobación obligatoria en Android Studio

Ejecutar, desde la raíz:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Luego iniciar `backend/` con Docker Compose, verificar `/health` y recorrer registro, login, catálogo, disponibilidad, reserva, Mis reservas y panel del negocio. Para una prueba de concurrencia real deben conectarse dos clientes a la misma instancia PostgreSQL de pruebas.
