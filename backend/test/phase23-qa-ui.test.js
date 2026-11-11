import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { loadRemoteActivities } from '../test-support/remote-activities-source.js';

const root = new URL('../../', import.meta.url);
const read = (path) => readFile(new URL(path, root), 'utf8');
const [theme, colors, dimens, api, booking, parser, panel, catalog, suspendResult] = await Promise.all([
  read('app/src/main/res/values/themes.xml'),
  read('app/src/main/res/values/colors.xml'),
  read('app/src/main/res/values/dimens.xml'),
  read('app/src/main/java/com/example/comunamadsistemacitas/data/remote/MarketplaceApiClient.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/presentation/booking/BookingViewModel.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/domain/validation/IsoDateTimeParser.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/presentation/business/BusinessPanelViewModel.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/presentation/catalog/MarketplaceCatalogViewModel.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/presentation/SuspendResult.kt')
]);
const activity = await loadRemoteActivities((path) => read(`app/src/main/java/com/example/comunamadsistemacitas/${path}`));

test('la app activa usa tema Material 3 y tokens comerciales definidos', () => {
  assert.match(theme, /Theme\.Material3\.Light\.NoActionBar/);
  assert.match(colors, /md_theme_light_primary[^>]*>#0F4C81/);
  assert.match(colors, /md_theme_light_secondary[^>]*>#00A86B/);
  assert.match(colors, /md_theme_light_tertiary[^>]*>#E59866/);
  assert.match(colors, /md_theme_light_background[^>]*>#F8F9FA/);
  assert.match(dimens, /shape_input/);
  assert.match(dimens, /shape_card/);
});

test('los recolectores Android siguen dentro de repeatOnLifecycle STARTED', () => {
  assert.match(activity, /repeatOnLifecycle\(Lifecycle\.State\.STARTED\)/);
  assert.doesNotMatch(activity, /GlobalScope/);
});

test('el cliente HTTP impide la red síncrona en el hilo principal y rechaza respuestas incompletas', () => {
  assert.match(api, /Looper\.myLooper\(\) != Looper\.getMainLooper\(\)/);
  assert.match(api, /requiredObject\(key: String\)/);
  assert.match(api, /requiredArray\(key: String\)/);
});

test('los turnos inválidos y las fechas no válidas se descartan antes de reservar', () => {
  assert.match(booking, /IsoDateTimeParser\.parseLocalDate\(localDate\)/);
  assert.match(booking, /IsoDateTimeParser\.parseDateTime\(startsAt\)/);
  assert.match(booking, /assigneeId\.isBlank\(\)/);
  assert.match(parser, /position\.index == value\.length/);
  assert.match(activity, /parseIsoDateStrict\(time\) == null/);
});

test('no se convierte una cancelación de coroutine en error de negocio', () => {
  assert.match(suspendResult, /catch \(cancelled: CancellationException\)/);
  assert.match(suspendResult, /throw cancelled/);
  assert.match(panel, /suspendResult/);
  assert.match(catalog, /suspendResult/);
});

test('registro de negocio y reserva limpian y validan los campos antes de enviar', () => {
  assert.match(activity, /BusinessValidation\.validateBusinessRegistration/);
  assert.match(activity, /AuthValidation\.validateCustomer/);
  assert.match(activity, /rSetValidationError/);
  assert.match(activity, /businessName\.text\.toString\(\)\.trim\(\)/);
});


test('el selector de categorías reconstruye el adapter aunque cambie el contenido y conserva selección por ID', () => {
  assert.match(activity, /val previousCategoryId = categories/);
  assert.match(activity, /categorySpinner\.adapter = ArrayAdapter/);
  assert.match(activity, /json\.optJSONObject\(index\)\?\.optString\("id"\) == previousCategoryId/);
  assert.doesNotMatch(activity, /categorySpinner\.adapter == null \|\| categorySpinner\.adapter\.count != json\.length\(\)/);
});


test('liberar el cliente HTTP desconecta las solicitudes que siguen activas', () => {
  assert.match(api, /activeConnections = ConcurrentHashMap\.newKeySet/);
  assert.match(api, /activeConnections\.toList\(\)\.forEach/);
  assert.match(api, /activeConnections\.remove\(connection\)/);
});
