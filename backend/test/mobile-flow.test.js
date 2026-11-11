import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { loadRemoteActivities } from '../test-support/remote-activities-source.js';

const root = new URL('../../app/src/main/java/com/example/comunamadsistemacitas/', import.meta.url);
const bookingBridge = await readFile(new URL('view/ProcesoReservaActivity.kt', root), 'utf8');
const readProject = (path) => readFile(new URL(path, root), 'utf8');
const remoteBooking = await loadRemoteActivities(readProject);
const apiClient = await readFile(new URL('data/remote/MarketplaceApiClient.kt', root), 'utf8');
const legacyEntry = await readFile(new URL('../../app/src/main/java/com/example/comunamadsistemacitas/MainActivity.kt', import.meta.url), 'utf8');
const designSystem = await readFile(new URL('view/UnamadDesignSystem.kt', root), 'utf8');
const portal = await readFile(new URL('view/MarketplaceActivities.kt', root), 'utf8');
const theme = await readFile(new URL('../../app/src/main/res/values/themes.xml', import.meta.url), 'utf8');
const catalogViewModel = await readFile(new URL('presentation/catalog/MarketplaceCatalogViewModel.kt', root), 'utf8');
const catalogRepository = await readFile(new URL('data/repository/MarketplaceCatalogRepository.kt', root), 'utf8');
const bookingRepository = await readFile(new URL('data/repository/BookingRepository.kt', root), 'utf8');
const bookingDataSource = await readFile(new URL('data/datasource/remote/BookingRemoteDataSource.kt', root), 'utf8');
const bookingViewModel = await readFile(new URL('presentation/booking/BookingViewModel.kt', root), 'utf8');
const businessPanelViewModel = await readFile(new URL('presentation/business/BusinessPanelViewModel.kt', root), 'utf8');
const businessPanelEvent = await readFile(new URL('presentation/business/BusinessPanelEvent.kt', root), 'utf8');
const browseViewModel = await readFile(new URL('presentation/catalog/MarketplaceBrowseViewModel.kt', root), 'utf8');

test('la pantalla antigua de reserva solo redirige al flujo central y no crea citas SQLite', () => {
  assert.match(bookingBridge, /MarketplaceRemotoActivity::class\.java/);
  assert.doesNotMatch(bookingBridge, /CitaRepository|insertarCita|SQLiteDatabase|PERSONAL_DEMO|WHATSAPP/i);
});

test('la reserva móvil delega disponibilidad y creación a BookingViewModel y Repository', () => {
  assert.match(remoteBooking, /bookingViewModel\.consultarDisponibilidad\(businessId/);
  assert.match(remoteBooking, /bookingViewModel\.crearReserva\(payload\)/);
  assert.match(remoteBooking, /payload\.put\("staffId"/);
  assert.match(remoteBooking, /payload\.put\("resourceId"/);
  assert.match(bookingViewModel, /requestVersion\.incrementAndGet\(\)/);
  assert.match(bookingViewModel, /validarPayloadReserva\(payload, state\)/);
  assert.match(bookingViewModel, /dateTime == selected\.optString\(\"starts_at\"\)/);
  assert.match(remoteBooking, /bookingViewModel\.reset\(\"\", \"\"\)/);
  assert.match(bookingRepository, /withContext\(Dispatchers\.IO\)/);
  assert.match(bookingDataSource, /api\.obtenerDisponibilidad\(businessId/);
  assert.match(bookingDataSource, /api\.crearReserva\(payload\)/);
});

test('Android guarda durablemente la clave idempotente antes del POST', () => {
  assert.match(apiClient, /pending_key:\$signature/);
  assert.match(apiClient, /cuentaDeToken/);
  assert.match(apiClient, /getString\(\"sub\"\)/);
  assert.doesNotMatch(apiClient, /pending_signature/);
  assert.match(apiClient, /bookingIdempotencyLock/);
  assert.match(apiClient, /\.commit\(\)/);
  assert.match(apiClient, /Idempotency-Key/);
});

test('la entrada de agenda SQLite antigua se redirige al portal central', () => {
  assert.match(legacyEntry, /PortalMarketplaceActivity::class\.java/);
  assert.doesNotMatch(legacyEntry, /CitaRepository|RegistrarCitaActivity|ListarCitasActivity/);
});

test('la portada dirige al flujo Reservar y elimina la entrada independiente Buscar', async () => {
  const portal = await readFile(new URL('view/MarketplaceActivities.kt', root), 'utf8');
  const strings = await readFile(new URL('../../app/src/main/res/values/strings.xml', import.meta.url), 'utf8');
  assert.match(strings, /portal_explore_action.*Reservar una cita/);
  assert.match(portal, /Mis reservas/);
  assert.doesNotMatch(portal, /\\nBuscar|btnBuscar/);
  assert.match(portal, /abrirFlujoReserva\(\)/);
});

test('el acceso conserva el negocio y servicio elegidos antes de iniciar sesión', () => {
  assert.match(remoteBooking, /AFTER_LOGIN", "BOOKING"/);
  assert.match(remoteBooking, /RESERVE_LISTING/);
  assert.match(remoteBooking, /OPEN_BUSINESS_ID/);
  assert.match(remoteBooking, /FLAG_ACTIVITY_CLEAR_TOP or Intent\.FLAG_ACTIVITY_SINGLE_TOP/);
  assert.match(remoteBooking, /pendingListing/);
});

test('el error de conexión del emulador muestra pasos de recuperación y no texto crudo en catálogo', () => {
  assert.match(remoteBooking, /10\.0\.2\.2 apunta a tu PC/);
  assert.match(remoteBooking, /docker compose up --build -d/);
  assert.match(remoteBooking, /Reintentar conexión/);
  assert.doesNotMatch(remoteBooking, /status\.text = "No se pudo consultar el catálogo\. Comprueba tu conexión/);
});

test('las pantallas remotas descartan respuestas antiguas al cambiar de sección', () => {
  assert.match(remoteBooking, /private var contentRequestId = 0/);
  assert.match(remoteBooking, /nuevaContentRequestId\(\)/);
  assert.match(remoteBooking, /event\.requestKey == contentRequestId/);
  assert.match(browseViewModel, /nuevaClaveSolicitud\(\): Int = requestSequence\.incrementAndGet\(\)/);
  assert.match(remoteBooking, /event\.requestKey == contentRequestId && currentSection == "BUSINESS"/);
  assert.match(remoteBooking, /event\.requestKey == contentRequestId && currentSection == "RESERVATIONS"/);
});

test('todas las pantallas activas comparten la base visual y la misma paleta', () => {
  assert.match(designSystem, /internal object UnamadPalette/);
  assert.match(portal, /UnamadPalette\.navy/);
  assert.match(remoteBooking, /UnamadPalette\.navy/);
  assert.match(remoteBooking, /UnamadPalette\.teal/);
  assert.match(remoteBooking, /UnamadPalette\.background/);
  assert.match(portal, /PortalMarketplaceActivity : UnamadBaseActivity/);
  for (const activity of ['AccesoRemotoActivity', 'RegistroNegocioRemotoActivity', 'MarketplaceRemotoActivity', 'PanelNegocioRemotoActivity']) {
    assert.match(remoteBooking, new RegExp(`${activity} : UnamadBaseActivity`));
  }
  assert.match(theme, /Theme\.Material3\.Light\.NoActionBar/);
});

test('un nuevo filtro reemplaza una carga anterior y la respuesta atrasada se descarta', () => {
  const reset = remoteBooking.indexOf('private fun cargarNegocios(reset: Boolean = true)');
  const end = remoteBooking.indexOf('private fun agregarTarjetaNegocio', reset);
  const source = remoteBooking.slice(reset, end);
  assert.match(source, /catalogViewModel\.buscar/);
  assert.match(catalogViewModel, /requestVersion\.incrementAndGet\(\)/);
  assert.match(catalogViewModel, /requestVersion\.get\(\) != version/);
  assert.match(catalogViewModel, /pageJob\?\.cancel\(\)/);
});

test('BookingViewModel descarta respuestas antiguas y reinicia la reserva al cerrar el diálogo', () => {
  assert.match(bookingViewModel, /requestVersion\.incrementAndGet\(\)/);
  assert.match(bookingViewModel, /requestVersion\.get\(\) != version/);
  assert.match(bookingViewModel, /availabilityJob\?\.cancel\(\)/);
  assert.match(remoteBooking, /bookingViewModel\.reset\(\"\", \"\"\)/);
  assert.match(remoteBooking, /if \(!isChangingConfigurations\)/);
  assert.match(remoteBooking, /activeBookingDialog\?\.isShowing/);
});

test('un pedido para recoger exige seleccionar explícitamente fecha y hora futuras', () => {
  assert.match(remoteBooking, /var pickupDateTimeSelected = activeBookingPickupSelected/);
  assert.match(remoteBooking, /pickupDateTimeSelected = true/);
  assert.match(remoteBooking, /!pickupDateTimeSelected \|\| calendar\.timeInMillis <= System\.currentTimeMillis\(\)/);
});

test('las vistas se leen en UI antes de iniciar operaciones en segundo plano', () => {
  assert.match(remoteBooking, /catalogViewModel\.buscar\(search\.text\.toString\(\)\)/);
  const marketplaceScreen = remoteBooking.slice(
    remoteBooking.indexOf('class MarketplaceRemotoActivity'),
    remoteBooking.indexOf('class PanelNegocioRemotoActivity')
  );
  assert.doesNotMatch(marketplaceScreen, /district\s*=\s*rField|SEARCH_DISTRICT|distrito \(opcional\)/i);
  assert.match(remoteBooking, /district\s*=\s*rField\("Distrito \(ej\. Puerto Maldonado\)"\)/);
  assert.match(catalogRepository, /withContext\(Dispatchers\.IO\)/);
  assert.match(remoteBooking, /val customerName = name\.text\.toString\(\)\.trim\(\)/);
  assert.match(remoteBooking, /val workerName = name\.text\.toString\(\)\.trim\(\)/);
  assert.match(remoteBooking, /val resourceName = name\.text\.toString\(\)\.trim\(\)/);
  assert.doesNotMatch(remoteBooking, /ejecutarAsync\(\{[^}]*\b(?:search|district|name|specialty)\.text/);
});

test('el panel y las acciones de administración usan una sola ruta MVVM', () => {
  const panel = remoteBooking.slice(remoteBooking.indexOf('class PanelNegocioRemotoActivity'));
  assert.match(panel, /panelViewModel\.seleccionarNegocio\(\)/);
  assert.match(panel, /panelViewModel\.cargarPanel\(businessId\)/);
  assert.match(panel, /reservationChangesInFlight\.add\(reservationId\)/);
  assert.match(panel, /submit\.isEnabled = false/);
  assert.match(panel, /Actualizar panel/);
  assert.doesNotMatch(panel, /api\.ejecutarAsync|api\.obtenerMisNegocios|api\.obtenerNegocio/);
  assert.match(businessPanelEvent, /BusinessListLoaded/);
});

test('los estados de las reservas se presentan en español comprensible', () => {
  assert.match(designSystem, /"SOLICITADA", "PENDIENTE" -> "Pendiente de confirmación"/);
  assert.match(designSystem, /"CONFIRMADA" -> "Confirmada"/);
  assert.match(designSystem, /"CANCELADA" -> "Cancelada"/);
  assert.match(remoteBooking, /estadoReservaAmigable/);
});


test('al abrir Mis reservas se limpia el catálogo y se oculta la paginación', () => {
  const start = remoteBooking.indexOf('private fun cargarMisReservas()')
  const end = remoteBooking.indexOf('private fun cancelarReserva', start)
  const source = remoteBooking.slice(start, end)
  assert.match(source, /currentSection = "RESERVATIONS"/)
  assert.match(source, /loadMoreButton\.visibility = View\.GONE/)
  assert.match(source, /list\.removeAllViews\(\)/)
  assert.match(source, /list\.addView\(rText\("Estamos consultando tus reservas/)
})

test('la respuesta de una cancelación no cambia la sección si el usuario ya navegó', () => {
  const start = remoteBooking.indexOf('private fun cancelarReserva')
  const end = remoteBooking.indexOf('override fun onDestroy()', start)
  const source = remoteBooking.slice(start, end)
  assert.match(source, /val requestAtStart = contentRequestId/)
  assert.match(source, /marketplaceViewModel\.cancelarReserva\(id, requestAtStart\)/)
  assert.match(remoteBooking, /event\.requestKey == contentRequestId && currentSection == "RESERVATIONS"/) 
  assert.match(browseViewModel, /Operation\.CANCEL_RESERVATION/)
})

test('el ViewModel del panel descarta cargas antiguas asociadas a otro negocio', () => {
  assert.match(businessPanelViewModel, /dashboardRequestId = AtomicInteger\(0\)/);
  assert.match(businessPanelViewModel, /dashboardRequestId\.incrementAndGet\(\)/);
  assert.match(businessPanelViewModel, /requestId != dashboardRequestId\.get\(\)/);
  assert.match(businessPanelViewModel, /requestedBusinessId != _uiState\.value\.businessId/);
});

test('las tarjetas informativas no se anuncian como controles clicables sin acción', () => {
  const start = remoteBooking.indexOf('private fun Context.rCard()')
  const end = remoteBooking.indexOf('private fun LinearLayout.rAddField', start)
  const source = remoteBooking.slice(start, end)
  assert.doesNotMatch(source, /isClickable = true|isFocusable = true/)
})

test('las mutaciones del panel pasan por BusinessPanelViewModel y Repository', () => {
  const panel = remoteBooking.slice(remoteBooking.indexOf('class PanelNegocioRemotoActivity'));
  assert.match(panel, /panelViewModel\.agregarTrabajador\(targetBusinessId/);
  assert.match(panel, /panelViewModel\.agregarRecurso\(targetBusinessId/);
  assert.match(panel, /panelViewModel\.cambiarEstadoReserva\(businessId/);
  assert.match(panel, /panelViewModel\.publicar\(targetBusinessId/);
  assert.match(panel, /panelViewModel\.configurarHorarios\(targetBusinessId/);
  assert.doesNotMatch(panel, /api\.(crearTrabajador|crearRecurso|cambiarEstadoReserva|publicar|configurarHorarios)\(/);
});

test('los filtros del catálogo se ocultan en detalle y en Mis reservas, y regresan al catálogo', () => {
  assert.match(remoteBooking, /private lateinit var catalogFiltersContainer: LinearLayout/)
  const catalogLoad = remoteBooking.indexOf('private fun cargarNegocios(reset: Boolean = true)')
  const businessLoad = remoteBooking.indexOf('private fun mostrarPublicaciones', catalogLoad)
  const reservationsLoad = remoteBooking.indexOf('private fun cargarMisReservas()', businessLoad)
  const catalogSource = remoteBooking.slice(catalogLoad, businessLoad)
  const businessSource = remoteBooking.slice(businessLoad, reservationsLoad)
  const reservationsSource = remoteBooking.slice(reservationsLoad, remoteBooking.indexOf('private fun cancelarReserva', reservationsLoad))
  assert.match(catalogSource, /catalogFiltersContainer\.visibility = View\.VISIBLE/)
  assert.match(businessSource, /catalogFiltersContainer\.visibility = View\.GONE/)
  assert.match(reservationsSource, /catalogFiltersContainer\.visibility = View\.GONE/)
})

test('el portal evita una tercera entrada redundante al panel del negocio', () => {
  assert.doesNotMatch(portal, /Administrar citas y reservas de mi negocio/)
  assert.match(portal, /Soy negocio/)
})


test('los accesos directos a un negocio ocultan los filtros mientras carga su detalle', () => {
  const create = remoteBooking.indexOf('override fun onCreate(savedInstanceState: Bundle?)', remoteBooking.indexOf('class MarketplaceRemotoActivity'))
  const newIntent = remoteBooking.indexOf('override fun onNewIntent(intent: Intent)', create)
  const createSource = remoteBooking.slice(create, newIntent)
  const newIntentEnd = remoteBooking.indexOf('private fun construirPantallaCatalogo()', newIntent)
  const newIntentSource = remoteBooking.slice(newIntent, newIntentEnd)
  assert.match(createSource, /catalogFiltersContainer\.visibility = View\.GONE/)
  assert.match(newIntentSource, /catalogFiltersContainer\.visibility = View\.GONE/)
})

test('si el servicio guardado para continuar después del login ya no existe, la app informa al usuario', () => {
  assert.match(remoteBooking, /Este servicio ya no está disponible\. Elige otra opción del negocio\./)
})

test('la cancelación de una reserva solicita confirmación antes de enviar la operación', () => {
  const start = remoteBooking.indexOf('val reservationId = item.optString("id")')
  const end = remoteBooking.indexOf('card.addView(content)', start)
  const source = remoteBooking.slice(start, end)
  assert.match(source, /setTitle\("Cancelar reserva"\)/)
  assert.match(source, /setPositiveButton\("Sí, cancelar"\)/)
  assert.match(source, /cancelarReserva\(reservationId, cancelButton\)/)
})
