import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { loadRemoteActivities } from '../test-support/remote-activities-source.js';

const root = new URL('../../app/src/main/java/com/example/comunamadsistemacitas/', import.meta.url);
const read = (path) => readFile(new URL(path, root), 'utf8');
const [authViewModel, authRepository, authDataSource, authState, authEvents, authValidation,
  bookingViewModel, bookingRepository, bookingDataSource, bookingState, designSystem, portal] = await Promise.all([
  read('presentation/auth/AuthViewModel.kt'),
  read('data/repository/AuthRepository.kt'),
  read('data/datasource/remote/AuthRemoteDataSource.kt'),
  read('presentation/auth/AuthUiState.kt'),
  read('presentation/auth/AuthEvent.kt'),
  read('domain/validation/AuthValidation.kt'),
  read('presentation/booking/BookingViewModel.kt'),
  read('data/repository/BookingRepository.kt'),
  read('data/datasource/remote/BookingRemoteDataSource.kt'),
  read('presentation/booking/BookingUiState.kt'),
  read('view/UnamadDesignSystem.kt'),
  read('view/MarketplaceActivities.kt')
]);
const remoteActivity = await loadRemoteActivities(read);

test('autenticación usa ViewModel, StateFlow, Repository y data source remoto', () => {
  assert.match(authViewModel, /AndroidViewModel/);
  assert.match(authViewModel, /viewModelScope\.launch/);
  assert.match(authViewModel, /StateFlow|asStateFlow/);
  assert.match(authViewModel, /AuthValidation\.validateLogin/);
  assert.match(authViewModel, /AuthValidation\.validateCustomer/);
  assert.match(authRepository, /withContext\(Dispatchers\.IO\)/);
  assert.match(authDataSource, /api\.iniciarSesion/);
  assert.match(authDataSource, /api\.registrarCliente/);
  assert.match(authState, /isLoading/);
  assert.match(authEvents, /Authenticated/);
  assert.match(authValidation, /EMAIL_REGEX|emailRegex/);
  assert.match(remoteActivity, /authViewModel\.login/);
  assert.match(remoteActivity, /authViewModel\.registerCustomer/);
  assert.doesNotMatch(remoteActivity, /api\.ejecutarAsync\(\{ api\.(iniciarSesion|registrarCliente)/);
});

test('reserva usa BookingViewModel y conserva resultados a través de eventos buffered', () => {
  assert.match(bookingViewModel, /AndroidViewModel/);
  assert.match(bookingViewModel, /StateFlow|asStateFlow/);
  assert.match(bookingViewModel, /Channel\.BUFFERED/);
  assert.match(bookingViewModel, /consultarDisponibilidad/);
  assert.match(bookingViewModel, /crearReserva/);
  assert.match(bookingViewModel, /requestVersion\.get\(\) != version/);
  assert.match(bookingRepository, /Dispatchers\.IO/);
  assert.match(bookingDataSource, /obtenerDisponibilidad/);
  assert.match(bookingDataSource, /crearReserva/);
  assert.match(bookingState, /selectedSlot/);
  assert.match(remoteActivity, /bookingViewModel\.consultarDisponibilidad/);
  assert.match(remoteActivity, /bookingViewModel\.crearReserva/);
  assert.doesNotMatch(remoteActivity, /api\.ejecutarAsync\(\{ api\.(obtenerDisponibilidad|crearReserva)/);
});

test('navegación inferior común tiene cuatro destinos y se actualiza al cambiar de pestaña', () => {
  assert.match(designSystem, /agregarNavegacionPrincipal/);
  assert.match(designSystem, /Triple\("HOME", "Inicio"/);
  assert.match(designSystem, /Triple\("BOOK", "Reservar"/);
  assert.match(designSystem, /Triple\("RESERVATIONS", "Mis citas"/);
  assert.match(designSystem, /Triple\("BUSINESS", "Negocio"/);
  assert.match(designSystem, /seleccionarDestinoNavegacion/);
  assert.match(portal, /agregarNavegacionPrincipal\(root, "HOME"\)/);
  assert.match(remoteActivity, /agregarNavegacionPrincipal\(root, "BOOK"\)/);
  assert.match(remoteActivity, /agregarNavegacionPrincipal\(root, "BUSINESS"\)/);
});

test('el diálogo de reserva guarda su contexto, fecha y selección durante rotación', () => {
  assert.match(remoteActivity, /onSaveInstanceState\(outState: Bundle\)/);
  assert.match(remoteActivity, /STATE_BOOKING_LISTING/);
  assert.match(remoteActivity, /STATE_BOOKING_DATE/);
  assert.match(remoteActivity, /STATE_BOOKING_QUANTITY/);
  assert.match(remoteActivity, /STATE_BOOKING_NOTE/);
  assert.match(remoteActivity, /activeBookingCountField\?\.text/);
  assert.match(remoteActivity, /activeBookingNoteField\?\.text/);
  assert.match(remoteActivity, /retainedBooking/);
  assert.match(remoteActivity, /refrescarEstadoUi\(\)/);
  assert.match(bookingViewModel, /fun refrescarEstadoUi/);
});

test('autenticación evita enrutamiento duplicado durante la recreación de Activity', () => {
  assert.match(remoteActivity, /accountRoutingStarted/);
  assert.match(remoteActivity, /if \(accountRoutingStarted \|\| isFinishing \|\| isDestroyed\) return/);
});

test('pruebas instrumentadas incluyen login, registro, reserva completa y recuperación de red', async () => {
  const androidTestRoot = new URL('../../app/src/androidTest/java/com/example/comunamadsistemacitas/', import.meta.url);
  const [authTest, bookingTest] = await Promise.all([
    readFile(new URL('AuthViewModelInstrumentedTest.kt', androidTestRoot), 'utf8'),
    readFile(new URL('BookingViewModelInstrumentedTest.kt', androidTestRoot), 'utf8')
  ]);
  assert.match(authTest, /loginValidoEmiteSesion/);
  assert.match(authTest, /registroDeClienteEmiteEvento/);
  assert.match(authTest, /falloDeRedDevuelveEstadoDeErrorYPermiteReintento/);
  assert.match(bookingTest, /flujoCompletoConsultaSeleccionaYConfirmaUnaReserva/);
  assert.match(bookingTest, /disponibilidadSeRecuperaCuandoVuelveLaConexion/);
  assert.match(bookingTest, /dobleConfirmacionSoloEjecutaUnaCreacion/);
});
