import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { loadRemoteActivities } from '../test-support/remote-activities-source.js';

const root = new URL('../../app/src/main/java/com/example/comunamadsistemacitas/', import.meta.url);
const read = (path) => readFile(new URL(path, root), 'utf8');
const [regViewModel, regState, regEvents, panelViewModel, panelState, panelEvents, businessRepository, businessDataSource] = await Promise.all([
  read('presentation/business/BusinessRegistrationViewModel.kt'),
  read('presentation/business/BusinessRegistrationUiState.kt'),
  read('presentation/business/BusinessRegistrationEvent.kt'),
  read('presentation/business/BusinessPanelViewModel.kt'),
  read('presentation/business/BusinessPanelUiState.kt'),
  read('presentation/business/BusinessPanelEvent.kt'),
  read('data/repository/BusinessRepository.kt'),
  read('data/datasource/remote/BusinessRemoteDataSource.kt'),
]);
const remoteActivity = await loadRemoteActivities(read);

test('alta de negocios usa BusinessRegistrationViewModel, StateFlow y Repository', () => {
  assert.match(regViewModel, /AndroidViewModel/);
  assert.match(regViewModel, /viewModelScope\.launch/);
  assert.match(regViewModel, /StateFlow|asStateFlow/);
  assert.match(regViewModel, /cargarCategorias/);
  assert.match(regViewModel, /registrarNegocio/);
  assert.match(regState, /isRegistering/);
  assert.match(regEvents, /Registered/);
  assert.match(remoteActivity, /registrationViewModel\.cargarCategorias/);
  assert.match(remoteActivity, /registrationViewModel\.registrarNegocio/);
});

test('panel administrativo de negocio usa BusinessPanelViewModel, StateFlow y Events', () => {
  assert.match(panelViewModel, /AndroidViewModel/);
  assert.match(panelViewModel, /StateFlow|asStateFlow/);
  assert.match(panelViewModel, /cargarPanel/);
  assert.match(panelViewModel, /seleccionarNegocio/);
  assert.match(panelViewModel, /agregarTrabajador/);
  assert.match(panelViewModel, /agregarRecurso/);
  assert.match(panelViewModel, /cambiarEstadoReserva/);
  assert.match(panelViewModel, /publicar/);
  assert.match(panelViewModel, /configurarHorarios/);
  assert.match(panelState, /isLoadingPanel/);
  assert.match(panelEvents, /OperationSuccess/);
  assert.match(remoteActivity, /panelViewModel\.cargarPanel/);
  assert.match(remoteActivity, /panelViewModel\.seleccionarNegocio/);
  assert.match(businessRepository, /withContext\(Dispatchers\.IO\)/);
  assert.match(businessDataSource, /api\.registrarNegocio/);
  assert.match(businessDataSource, /api\.obtenerMisNegocios/);
});
