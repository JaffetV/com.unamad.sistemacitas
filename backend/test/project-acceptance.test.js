import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const projectRoot = new URL('../../', import.meta.url);
const read = (path) => readFile(new URL(path, projectRoot), 'utf8');
const [manifest, strings, portal, catalog, catalogViewModel, catalogState, catalogRepository, apiClient, server] = await Promise.all([
  read('app/src/main/AndroidManifest.xml'),
  read('app/src/main/res/values/strings.xml'),
  read('app/src/main/java/com/example/comunamadsistemacitas/view/MarketplaceActivities.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/view/MarketplaceRemotoActivity.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/presentation/catalog/MarketplaceCatalogViewModel.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/presentation/catalog/MarketplaceCatalogUiState.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/data/repository/MarketplaceCatalogRepository.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/data/remote/MarketplaceApiClient.kt'),
  read('backend/src/server.js')
]);

test('el nombre visible de la aplicación no contiene la marca anterior', () => {
  assert.match(strings, /<string name="app_name">Citas y Reservas<\/string>/);
  assert.match(manifest, /android:label="@string\/app_name"/);
  assert.doesNotMatch(portal, /crearTexto\([^\n]*"UNAMAD"/);
});

test('el catálogo no ofrece ni envía un filtro de distrito, pero conserva distrito como dirección del negocio', () => {
  assert.doesNotMatch(catalog, /district\s*=\s*rField|SEARCH_DISTRICT|district\.text|distrito \(opcional\)/i);
  assert.doesNotMatch(catalogViewModel, /district/i);
  assert.doesNotMatch(catalogState, /district/i);
  assert.doesNotMatch(catalogRepository, /district|distrito/i);
  assert.doesNotMatch(apiClient.slice(apiClient.indexOf('fun explorarNegociosPagina'), apiClient.indexOf('fun obtenerNegocio')), /district|distrito/i);
  assert.match(catalog, /business\.optString\("district"\)/);
});

test('Inicio vuelve al portal y Atrás aplica una política clara por sección', () => {
  assert.match(catalog, /private fun irAlPortal\(\)/);
  assert.match(catalog, /Intent\.FLAG_ACTIVITY_CLEAR_TOP or Intent\.FLAG_ACTIVITY_SINGLE_TOP/);
  assert.match(catalog, /currentSection = "BUSINESS"/);
  assert.match(catalog, /currentSection == "BUSINESS" -> cargarNegocios\(reset = true\)/);
  assert.match(catalog, /else -> irAlPortal\(\)/);
});

test('el catálogo reutiliza la ilustración local del flujo principal', () => {
  assert.match(catalog, /R\.drawable\.hero_reservas/);
});


test('la búsqueda del catálogo también encuentra servicios publicados activos', () => {
  const route = server.slice(server.indexOf("path === '/api/v1/businesses'"), server.indexOf("path === '/api/v1/me'"));
  assert.match(route, /EXISTS \(SELECT 1 FROM listings l WHERE l\.business_id=b\.id AND l\.active=true/);
  assert.match(route, /l\.title ILIKE/);
  assert.match(route, /l\.description ILIKE/);
});
