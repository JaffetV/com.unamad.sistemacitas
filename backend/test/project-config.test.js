import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const projectRoot = new URL('../../', import.meta.url);
const read = (path) => readFile(new URL(path, projectRoot), 'utf8');
const [versions, wrapper, gradleProperties, appGradle, catalogViewModel, catalogRepository, activities] = await Promise.all([
  read('gradle/libs.versions.toml'),
  read('gradle/wrapper/gradle-wrapper.properties'),
  read('gradle.properties'),
  read('app/build.gradle.kts'),
  read('app/src/main/java/com/example/comunamadsistemacitas/presentation/catalog/MarketplaceCatalogViewModel.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/data/repository/MarketplaceCatalogRepository.kt'),
  read('app/src/main/java/com/example/comunamadsistemacitas/view/MarketplaceActivities.kt')
]);
const catalog = await read('app/src/main/java/com/example/comunamadsistemacitas/view/MarketplaceRemotoActivity.kt');

test('Android usa una combinación fijada de AGP, Kotlin y Gradle', () => {
  assert.match(versions, /agp\s*=\s*"8\.11\.1"/);
  assert.match(versions, /kotlin\s*=\s*"2\.2\.21"/);
  assert.ok(wrapper.includes('distributionUrl=https\\://services.gradle.org/distributions/gradle-8.13-bin.zip'));
  assert.match(gradleProperties, /android\.useAndroidX=true/);
  assert.match(appGradle, /lifecycle\.viewmodel\.ktx/);
  assert.match(appGradle, /http:\/\/10\.0\.2\.2:8080/);
  assert.match(appGradle, /Para compilar release/);
  assert.match(appGradle, /startsWith\("https:\/\/"/);
});

test('el catálogo Android sigue MVVM con UiState StateFlow y Repository', () => {
  assert.match(catalogViewModel, /AndroidViewModel/);
  assert.match(catalogViewModel, /StateFlow<MarketplaceCatalogUiState>/);
  assert.match(catalogViewModel, /viewModelScope\.launch/);
  assert.match(catalogRepository, /withContext\(Dispatchers\.IO\)/);
  assert.match(catalog, /observarEstadoCatalogo\(\)/);
});

test('pantallas antiguas son puentes y no vuelven a crear reservas locales', () => {
  for (const [name, next] of [
    ['RegistroNegocioActivity', 'RegistroNegocioRemotoActivity'],
    ['RegistroClienteActivity', 'AccesoRemotoActivity'],
    ['MarketplaceActivity', 'MarketplaceRemotoActivity'],
    ['PanelNegocioActivity', 'PanelNegocioRemotoActivity']
  ]) {
    const start = activities.indexOf(`class ${name}`);
    assert.notEqual(start, -1, `${name} debe seguir disponible como Activity de compatibilidad`);
    const tail = activities.slice(start, activities.indexOf('\n}', start) + 2);
    assert.match(tail, new RegExp(`${next}::class\\.java`));
    assert.doesNotMatch(tail, /MarketplaceRepository|CitaRepository|SQLiteDatabase|insertarCita/);
  }
});

test('el catálogo limita la cantidad de tarjetas renderizadas por búsqueda', () => {
  assert.match(catalogViewModel, /PAGE_SIZE\s*=\s*24/);
  assert.match(catalogViewModel, /MAX_RENDERED\s*=\s*120/);
  assert.match(catalogViewModel, /reachedDisplayLimit/);
});
