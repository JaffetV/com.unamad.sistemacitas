import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = new URL('../../', import.meta.url);
const read = (relative) => readFile(new URL(relative, projectRoot), 'utf8');
const javaRoot = new URL('app/src/main/java/com/example/comunamadsistemacitas/', projectRoot);
const [browseVm, browseEvent, businessRepository, remoteDataSource, sessionStore, apiClient,
  designSystem, portal, backupRules, extractionRules, sharedHelpers] = await Promise.all([
  read('app/src/main/java/com/example/comunamadsistemacitas/presentation/catalog/MarketplaceBrowseViewModel.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/presentation/catalog/MarketplaceBrowseEvent.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/data/repository/BusinessRepository.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/data/datasource/remote/BusinessRemoteDataSource.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/data/remote/MarketplaceSessionStore.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/data/remote/MarketplaceApiClient.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/view/UnamadDesignSystem.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/view/MarketplaceActivities.kt'),
  read('app/src/main/res/xml/backup_rules.xml'),
  read('app/src/main/res/xml/data_extraction_rules.xml'),
  read('app/src/main/java/com/example/comunamadsistemacitas/view/MarketplaceUiHelpers.kt')
]);
const screens = await Promise.all(['AccesoRemotoActivity.kt','RegistroNegocioRemotoActivity.kt','MarketplaceRemotoActivity.kt','PanelNegocioRemotoActivity.kt']
  .map(name => readFile(new URL(`view/${name}`, javaRoot), 'utf8')));

test('las cuatro pantallas remotas están separadas en archivos independientes', async () => {
  const names = await readdir(new URL('view/', javaRoot));
  for (const name of ['AccesoRemotoActivity.kt','RegistroNegocioRemotoActivity.kt','MarketplaceRemotoActivity.kt','PanelNegocioRemotoActivity.kt','MarketplaceUiHelpers.kt']) {
    assert.ok(names.includes(name), `${name} debe existir`);
  }
  assert.ok(!names.includes('MarketplaceRemoteActivities.kt'), 'no debe volver el archivo monolítico');
  for (const screen of screens) {
    assert.doesNotMatch(screen, /private const val REMOTE_PREFS|private fun Context\.rdp|private fun Context\.rText/);
  }
  assert.match(sharedHelpers, /internal fun Context\.rdp/);
  assert.match(sharedHelpers, /internal fun Context\.rText/);
});

test('el marketplace no llama al cliente HTTP desde las Activities', () => {
  for (const screen of screens) {
    assert.doesNotMatch(screen, /MarketplaceApiClient|MarketplaceApiConfig|ejecutarAsync/);
  }
  assert.match(browseVm, /viewModelScope\.launch/);
  assert.match(browseVm, /suspendResult/);
  assert.match(browseVm, /repository\.obtenerNegocio/);
  assert.match(browseVm, /repository\.obtenerPublicaciones/);
  assert.match(browseVm, /repository\.obtenerMisReservas/);
  assert.match(browseVm, /repository\.cancelarMiReserva/);
  assert.match(browseEvent, /data class Failed/);
});

test('consultas del marketplace usan claves únicas y cancelan peticiones reemplazadas', () => {
  assert.match(browseVm, /AtomicInteger\(0\)/);
  assert.match(browseVm, /nuevaClaveSolicitud\(\): Int = requestSequence\.incrementAndGet\(\)/);
  assert.match(browseVm, /synchronized\(operationJobs\)/);
  assert.match(browseVm, /previous\?\.cancel\(\)/);
});

test('la sesión se comprueba sin descifrar el JWT en la interfaz principal', () => {
  assert.match(sessionStore, /fun hayTokenGuardado\(\): Boolean/);
  assert.match(apiClient, /fun haySesion\(\): Boolean = session\.hayTokenGuardado\(\)/);
});

test('backup excluye sesiones e idempotencia tanto en nube como en transferencia', () => {
  for (const source of [backupRules, extractionRules]) {
    assert.match(source, /marketplace_secure_session\.xml/);
    assert.match(source, /central_booking_idempotency\.xml/);
    assert.match(source, /marketplace_local_session\.xml/);
  }
  assert.match(extractionRules, /<device-transfer>/);
});

test('la barra inferior usa iconos vectoriales y no símbolos tipográficos', async () => {
  assert.match(designSystem, /ICON_GRAVITY_TOP/);
  for (const icon of ['ic_nav_home.xml','ic_nav_calendar_plus.xml','ic_nav_calendar_check.xml','ic_nav_store.xml']) {
    const xml = await read(`app/src/main/res/drawable/${icon}`);
    assert.match(xml, /<vector/);
    assert.match(xml, /<path/);
  }
  assert.match(portal, /ic_category_beauty/);
  assert.match(portal, /ic_category_tools/);
  assert.match(portal, /ic_category_health/);
  assert.match(portal, /ic_category_retail/);
});
