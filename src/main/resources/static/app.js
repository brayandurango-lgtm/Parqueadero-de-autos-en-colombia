const API = '/api/v1';
const TOKEN_KEY = 'autos-colombia-token';
const $ = (selector) => document.querySelector(selector);
const toast = $('#toast');
let currentUser = null;
let toastTimer;

function notify(message, isError = false) {
  toast.textContent = message;
  toast.classList.toggle('error', isError);
  toast.classList.add('visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('visible'), 3800);
}

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body) headers.set('Content-Type', 'application/json');
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const response = await fetch(`${API}${path}`, { ...options, headers });
  if (response.status === 401) {
    logout(false);
    throw new Error('Sesión vencida o no autorizada. Inicia sesión nuevamente.');
  }
  if (!response.ok) {
    let message = `La solicitud no se pudo completar (${response.status}).`;
    try {
      const body = await response.json();
      message = body.message || body.error || message;
    } catch { /* Algunos errores no contienen JSON. */ }
    throw new Error(message);
  }
  if (response.status === 204) return null;
  return response;
}

async function getJson(path) {
  const response = await request(path);
  return response.json();
}

function logout(showMessage = true) {
  sessionStorage.removeItem(TOKEN_KEY);
  currentUser = null;
  $('#app-view').hidden = true;
  $('#login-view').hidden = false;
  if (showMessage) notify('Sesión cerrada.');
}

function showApplication(user) {
  currentUser = user;
  $('#login-view').hidden = true;
  $('#app-view').hidden = false;
  const fullName = [user.nombre, user.apellido].filter(Boolean).join(' ') || user.correo || 'Usuario';
  $('#user-name').textContent = fullName;
  $('#user-role').textContent = user.tipo || 'Personal';
  $('#user-initials').textContent = fullName.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase();
  document.querySelectorAll('[data-admin-only]').forEach((control) => { control.hidden = !esAdministrador(); });
  navigate('dashboard');
  refreshDashboard();
}

function navigate(view) {
  if (view === 'usuarios' && !esAdministrador()) {
    notify('Acceso reservado para administradores.', true);
    view = 'dashboard';
  }
  document.querySelectorAll('.view-section').forEach((section) => { section.hidden = section.id !== `${view}-view`; });
  document.querySelectorAll('.nav-link').forEach((button) => button.classList.toggle('active', button.dataset.view === view));
  const titles = { dashboard: 'Resumen', entrada: 'Registrar entrada', salida: 'Registrar salida', novedades: 'Novedades', clientes: 'Clientes', celdas: 'Celdas', usuarios: 'Personal' };
  $('#page-title').textContent = titles[view] || 'Autos Colombia';
  if (view === 'dashboard') refreshDashboard();
  if (view === 'entrada') restoreEntryData();
  if (view === 'clientes') loadClients();
  if (view === 'celdas') loadCells();
  if (view === 'usuarios') loadUsers();
}

function formatDate(value) {
  if (!value) return '—';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? '—' : new Intl.DateTimeFormat('es-CO', { dateStyle: 'short', timeStyle: 'short' }).format(parsed);
}

function escapeHtml(value = '') {
  return String(value).replace(/[&<>"']/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[character]);
}

async function refreshDashboard() {
  if (!sessionStorage.getItem(TOKEN_KEY)) return;
  try {
    const [dashboard, activeRecords] = await Promise.all([
      getJson('/reportes/dashboard'),
      getJson('/registros/activos')
    ]);
    const occupied = Number(dashboard.celdasOcupadas || 0);
    const total = Number(dashboard.celdasTotal || 0);
    const activas = Number(dashboard.celdasActivas ?? total);
    const mantenimiento = Number(dashboard.celdasMantenimiento || 0);
    const inactivas = Math.max(0, total - activas);
    // «Disponibles» la decide el backend: una celda inactiva, en mantenimiento o con vehículo adentro no
    // está libre para operar, aunque el resto del inventario diga otra cosa.
    const free = dashboard.celdasDisponibles === undefined
      ? Math.max(0, total - occupied)
      : Number(dashboard.celdasDisponibles);
    const percent = Number(dashboard.porcentajeOcupacion || 0);
    $('#metric-total').textContent = total;
    $('#metric-free').textContent = free;
    $('#metric-occupied').textContent = occupied;
    $('#metric-percent').textContent = `${percent.toFixed(1)}%`;
    $('#occupancy-large').textContent = `${occupied} / ${total}`;
    $('#occupancy-progress').style.width = `${Math.max(0, Math.min(100, percent))}%`;
    $('#occupancy-caption').textContent = `${free} espacios disponibles`
      + (mantenimiento ? ` · ${mantenimiento} en mantenimiento` : '')
      + (inactivas ? ` · ${inactivas} inactivas` : '');
    const rows = Array.isArray(dashboard.ultimosIngresos) && dashboard.ultimosIngresos.length
      ? dashboard.ultimosIngresos
      : activeRecords.slice(0, 8);
    $('#recent-rows').innerHTML = rows.length ? rows.slice(0, 8).map((record) => `<tr>
      <td><span class="plate-badge">${escapeHtml(record.placa)}</span></td>
      <td>${escapeHtml([record.marca, record.modelo].filter(Boolean).join(' '))}</td>
      <td>${escapeHtml(record.numeroCelda)}</td>
      <td>${escapeHtml(formatDate(record.fechaHoraEntrada))}</td>
      <td><span class="status-badge">${escapeHtml(record.estado || 'ACTIVO')}</span></td>
    </tr>`).join('') : '<tr><td colspan="5" class="empty-state">Aún no hay vehículos registrados.</td></tr>';
  } catch (error) {
    notify(error.message, true);
  }
}

$('#login-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const submit = event.currentTarget.querySelector('button[type="submit"]');
  submit.disabled = true;
  $('#login-error').textContent = '';
  try {
    const response = await fetch(`${API}/auth/login`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ correo: $('#email').value.trim(), clave: $('#password').value })
    });
    if (!response.ok) {
      let message = 'No fue posible iniciar sesión. Revisa tus credenciales.';
      try { const body = await response.json(); message = body.message || message; } catch { /* Sin cuerpo de error. */ }
      throw new Error(message);
    }
    const result = await response.json();
    sessionStorage.setItem(TOKEN_KEY, result.token);
    const user = await getJson('/auth/me');
    showApplication(user);
  } catch (error) {
    $('#login-error').textContent = error.message;
  } finally {
    submit.disabled = false;
  }
});

$('#logout-button').addEventListener('click', async () => {
  try { await request('/auth/logout', { method: 'POST' }); } catch { /* Aun con red caída, se elimina el JWT local. */ }
  logout();
});

document.querySelectorAll('[data-view]').forEach((button) => button.addEventListener('click', () => navigate(button.dataset.view)));
document.querySelectorAll('[data-go]').forEach((button) => button.addEventListener('click', () => navigate(button.dataset.go)));

let savedEntryData = null;

function saveCurrentEntryData() {
  savedEntryData = {
    placa: $('#entry-plate').value,
    tipo: $('#entry-type').value,
    marca: $('#entry-make').value,
    modelo: $('#entry-model').value,
    color: $('#entry-color').value,
    propietarioNombre: $('#owner-name').value,
    propietarioDocumento: $('#owner-document').value,
    propietarioTelefono: $('#owner-phone').value,
    clienteId: $('#entry-client-id').value,
    clienteName: $('#entry-client-name').textContent
  };
}

function restoreEntryData() {
  if (!savedEntryData) return;
  $('#entry-plate').value = savedEntryData.placa || '';
  $('#entry-type').value = savedEntryData.tipo || 'CARRO';
  $('#entry-make').value = savedEntryData.marca || '';
  $('#entry-model').value = savedEntryData.modelo || '';
  $('#entry-color').value = savedEntryData.color || '';
  $('#owner-name').value = savedEntryData.propietarioNombre || '';
  $('#owner-document').value = savedEntryData.propietarioDocumento || '';
  $('#owner-phone').value = savedEntryData.propietarioTelefono || '';
  if (savedEntryData.clienteId) {
    $('#entry-client-id').value = savedEntryData.clienteId;
    $('#entry-client-name').textContent = savedEntryData.clienteName || '';
    $('#entry-client-selected').hidden = false;
  }
  savedEntryData = null;
}

async function searchClientForEntry() {
  const query = $('#entry-client-search').value.trim();
  const errorEl = $('#entry-client-search-error');
  const infoEl = $('#entry-client-info');
  errorEl.textContent = '';
  infoEl.textContent = '';
  if (!query) {
    errorEl.textContent = 'Ingresa un documento o nombre para buscar.';
    return;
  }
  try {
    const page = await getJson(`/clientes?criterio=${encodeURIComponent(query)}&page=0&size=5`);
    const list = Array.isArray(page.content) ? page.content : [];
    if (!list.length) {
      errorEl.textContent = 'No se encontró ningún cliente con ese criterio. Puedes registrarlo con «＋ Crear cliente».';
      return;
    }
    const client = list[0];
    if (client.activo === false) {
      errorEl.textContent = 'El cliente encontrado está inactivo y no admite nuevos ingresos.';
      return;
    }
    $('#entry-client-id').value = String(client.id);
    $('#entry-client-name').textContent = `${client.tipoDocumento} ${client.documento} - ${client.nombreCompleto}`;
    $('#entry-client-selected').hidden = false;
    infoEl.textContent = 'Cliente seleccionado y listo para vincular.';
    if (!$('#owner-name').value) $('#owner-name').value = client.nombreCompleto;
    if (!$('#owner-document').value) $('#owner-document').value = client.documento;
    if (!$('#owner-phone').value) $('#owner-phone').value = client.telefono;
  } catch (err) {
    errorEl.textContent = err.message;
  }
}

function clearClientSelection() {
  $('#entry-client-id').value = '';
  $('#entry-client-selected').hidden = true;
  $('#entry-client-name').textContent = '';
  $('#entry-client-info').textContent = '';
  $('#entry-client-search-error').textContent = '';
  $('#entry-client-search').value = '';
}

async function checkPlateForEntry() {
  const plate = $('#entry-plate').value.trim().toUpperCase();
  if (!plate || plate.length < 5) return;
  try {
    const vehiculo = await getJson(`/vehiculos/${encodeURIComponent(plate)}`);
    if (vehiculo && vehiculo.clienteId) {
      $('#entry-client-id').value = String(vehiculo.clienteId);
      const clienteNombre = [vehiculo.propietarioNombre, vehiculo.propietarioDocumento].filter(Boolean).join(' - ');
      $('#entry-client-name').textContent = clienteNombre || `Cliente ID: ${vehiculo.clienteId}`;
      $('#entry-client-selected').hidden = false;
      $('#entry-client-info').textContent = 'Vehículo previamente vinculado a este titular.';
      if (vehiculo.marca) $('#entry-make').value = vehiculo.marca;
      if (vehiculo.modelo) $('#entry-model').value = vehiculo.modelo;
      if (vehiculo.color) $('#entry-color').value = vehiculo.color;
      if (vehiculo.tipo) $('#entry-type').value = vehiculo.tipo;
    }
  } catch {
    // Si no existe el vehículo, continúa normalmente
  }
}

$('#entry-client-search-button').addEventListener('click', searchClientForEntry);
$('#entry-client-clear').addEventListener('click', clearClientSelection);
$('#entry-plate').addEventListener('blur', checkPlateForEntry);
$('#entry-client-new-button').addEventListener('click', () => {
  saveCurrentEntryData();
  navigate('clientes');
  openClientForm(null);
  notify('Completa la creación del cliente. Tus datos de entrada se conservarán al volver.');
});

$('#entry-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const submit = form.querySelector('button[type="submit"]');
  const payload = Object.fromEntries(new FormData(form).entries());
  payload.placa = payload.placa.trim().toUpperCase();
  if (payload.clienteId) {
    payload.clienteId = Number(payload.clienteId);
  } else {
    delete payload.clienteId;
  }
  submit.disabled = true;
  $('#entry-result').hidden = true;
  try {
    const response = await request('/registros/entrada', { method: 'POST', body: JSON.stringify(payload) });
    const record = await response.json();
    let ticketMarkup = '';
    try {
      const tickets = await getJson(`/comprobantes?registroId=${encodeURIComponent(record.id)}`);
      const ticket = tickets.find((item) => item.tipo === 'ENTRADA') || tickets[0];
      if (ticket?.qrBase64) {
        const imageSource = ticket.qrBase64.startsWith('data:') ? ticket.qrBase64 : `data:image/png;base64,${ticket.qrBase64}`;
        ticketMarkup = `<br>Comprobante: ${escapeHtml(ticket.codigo)}<img src="${escapeHtml(imageSource)}" alt="Código QR de entrada">`;
      }
    } catch { /* La entrada quedó confirmada aunque no se pueda cargar el QR. */ }
    const result = $('#entry-result');
    result.innerHTML = `<strong>Entrada registrada correctamente.</strong><br>Placa: ${escapeHtml(record.placa)} · Celda: ${escapeHtml(record.numeroCelda)} · Estado: ${escapeHtml(record.estado)}${ticketMarkup}`;
    result.hidden = false;
    form.reset();
    clearClientSelection();
    notify('Entrada registrada.');
    refreshDashboard();
  } catch (error) {
    notify(error.message, true);
  } finally {
    submit.disabled = false;
  }
});

$('#exit-search-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const plate = $('#exit-plate').value.trim().toUpperCase();
  const target = $('#exit-result');
  target.hidden = true;
  try {
    const record = await getJson(`/registros/${encodeURIComponent(plate)}`);
    target.dataset.plate = plate;
    target.dataset.record = String(record.id);
    target.innerHTML = `<h3>Vehículo en el parqueadero</h3><div class="exit-summary">
      <div><span>Placa</span><strong>${escapeHtml(record.placa)}</strong></div>
      <div><span>Vehículo</span><strong>${escapeHtml([record.marca, record.modelo].filter(Boolean).join(' '))}</strong></div>
      <div><span>Celda asignada</span><strong>${escapeHtml(record.numeroCelda)}</strong></div>
      <div><span>Hora de entrada</span><strong>${escapeHtml(formatDate(record.fechaHoraEntrada))}</strong></div>
    </div><label class="field"><span class="field-label">Observaciones de salida</span><textarea id="exit-observations" rows="3" placeholder="Sin novedades"></textarea></label>
      <div class="form-actions"><button id="confirm-exit" class="button primary" type="button">Confirmar salida y liberar celda</button></div><div id="exit-confirmation" class="result-box" hidden></div>`;
    target.hidden = false;
    $('#confirm-exit').addEventListener('click', confirmExit);
  } catch (error) {
    notify(error.message, true);
  }
});

async function confirmExit() {
  const button = $('#confirm-exit');
  button.disabled = true;
  const plate = $('#exit-result').dataset.plate;
  try {
    const response = await request(`/registros/salida/${encodeURIComponent(plate)}`, {
      method: 'PUT', body: JSON.stringify({ observaciones: $('#exit-observations').value.trim() })
    });
    const record = await response.json();
    const confirmation = $('#exit-confirmation');
    confirmation.innerHTML = `<strong>Salida registrada.</strong><br>Estancia: ${escapeHtml(record.duracionMinutos ?? '—')} minutos · Estado: ${escapeHtml(record.estado)}`;
    confirmation.hidden = false;
    button.hidden = true;
    notify('Salida registrada y celda liberada.');
    refreshDashboard();
  } catch (error) {
    notify(error.message, true);
    button.disabled = false;
  }
}

$('#incident-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const payload = {
    registroId: Number($('#incident-record').value),
    tipo: $('#incident-type').value,
    descripcion: $('#incident-description').value.trim(),
    evidenciaUrl: $('#incident-evidence').value.trim() || null
  };
  try {
    await request('/novedades', { method: 'POST', body: JSON.stringify(payload) });
    event.currentTarget.reset();
    notify('Novedad guardada correctamente.');
  } catch (error) {
    notify(error.message, true);
  }
});

// ---------------------------------------------------------------- clientes (bloque B2)

const clientState = { page: 0, size: 10, criterio: '', activo: '', editingId: null, selectedId: null, confirmId: null, busy: false };

function esAdministrador() {
  return currentUser && currentUser.tipo === 'ADMINISTRADOR';
}

function clientesQuery() {
  const params = new URLSearchParams({ page: String(clientState.page), size: String(clientState.size) });
  if (clientState.criterio) params.set('criterio', clientState.criterio);
  if (clientState.activo) params.set('activo', clientState.activo);
  return params.toString();
}

function setClientsStatus(message) {
  $('#clients-status').textContent = message || '';
}

async function loadClients() {
  const rows = $('#clients-rows');
  rows.innerHTML = '<tr><td colspan="5" class="empty-state">Cargando clientes…</td></tr>';
  setClientsStatus('Consultando clientes…');
  try {
    const page = await getJson(`/clientes?${clientesQuery()}`);
    renderClients(page);
  } catch (error) {
    rows.innerHTML = '<tr><td colspan="5" class="empty-state">No fue posible cargar los clientes.</td></tr>';
    setClientsStatus('');
    notify(error.message, true);
  }
}

function renderClients(page) {
  const content = Array.isArray(page.content) ? page.content : [];
  const rows = $('#clients-rows');
  const soloAdmin = esAdministrador();
  setClientsStatus('');
  if (!content.length) {
    rows.innerHTML = '<tr><td colspan="5" class="empty-state">No hay clientes con esos criterios.</td></tr>';
  } else {
    rows.innerHTML = content.map((client) => {
      const activo = client.activo !== false;
      const confirmando = clientState.confirmId === client.id;
      const etiqueta = confirmando ? 'Confirmar' : (activo ? 'Desactivar' : 'Reactivar');
      const botonEstado = soloAdmin
        ? `<button class="button ${confirmando ? 'primary' : 'danger'}" type="button" data-action="estado" data-id="${client.id}">${etiqueta}</button>`
        : '';
      return `<tr>
        <td><span class="plate-badge">${escapeHtml(`${client.tipoDocumento || ''} ${client.documento || ''}`.trim())}</span></td>
        <td>${escapeHtml(client.nombreCompleto)}</td>
        <td>${escapeHtml(client.telefono)}</td>
        <td><span class="status-chip ${activo ? 'active' : 'inactive'}">${activo ? 'Activo' : 'Inactivo'}</span></td>
        <td class="row-actions">
          <button class="button subtle" type="button" data-action="editar" data-id="${client.id}">Editar</button>
          <button class="button subtle" type="button" data-action="vehiculos" data-id="${client.id}">Vehículos</button>
          ${botonEstado}
        </td>
      </tr>`;
    }).join('');
  }
  const total = Number(page.totalElements ?? content.length);
  const ultima = Number(page.number ?? clientState.page) + 1;
  const totalPages = Number(page.totalPages ?? 1);
  $('#clients-page').textContent = `${content.length} de ${total} · página ${ultima} de ${Math.max(1, totalPages)}`;
  $('#clients-prev').disabled = ultima <= 1;
  $('#clients-next').disabled = page.last !== false;
}

function openClientForm(client) {
  clientState.editingId = client ? client.id : null;
  clientState.confirmId = null;
  $('#client-form-title').textContent = client ? 'Editar ficha del cliente' : 'Nueva ficha de cliente';
  $('#client-doctype').value = client ? client.tipoDocumento : 'CC';
  $('#client-document').value = client ? client.documento : '';
  $('#client-name').value = client ? client.nombreCompleto : '';
  $('#client-phone').value = client ? client.telefono : '';
  $('#client-form-error').textContent = '';
  $('#client-form').hidden = false;
}

function closeClientForm() {
  clientState.editingId = null;
  $('#client-form').hidden = true;
  $('#client-form-error').textContent = '';
}

async function submitClientForm(event) {
  event.preventDefault();
  if (clientState.busy) return;
  const submit = event.currentTarget.querySelector('button[type="submit"]');
  const payload = {
    tipoDocumento: $('#client-doctype').value,
    documento: $('#client-document').value.trim(),
    nombreCompleto: $('#client-name').value.trim(),
    telefono: $('#client-phone').value.trim()
  };
  $('#client-form-error').textContent = '';
  clientState.busy = true;
  submit.disabled = true;
  try {
    if (clientState.editingId) {
      await request(`/clientes/${clientState.editingId}`, { method: 'PUT', body: JSON.stringify(payload) });
      notify('Cliente actualizado.');
    } else {
      await request('/clientes', { method: 'POST', body: JSON.stringify(payload) });
      notify('Cliente registrado.');
    }
    closeClientForm();
    loadClients();
  } catch (error) {
    $('#client-form-error').textContent = error.message;
  } finally {
    clientState.busy = false;
    submit.disabled = false;
  }
}

async function cambiarEstadoCliente(id) {
  if (clientState.confirmId !== id) {
    clientState.confirmId = id;
    notify('Confirma para desactivar o reactivar al cliente.');
    const page = await getJson(`/clientes?${clientesQuery()}`);
    renderClients(page);
    return;
  }
  if (clientState.busy) return;
  clientState.busy = true;
  clientState.confirmId = null;
  try {
    const actual = await getJson(`/clientes/${id}`);
    await request(`/clientes/${id}/activo`, { method: 'PATCH', body: JSON.stringify({ activo: actual.activo === false }) });
    notify('Estado del cliente actualizado.');
    await loadClients();
  } catch (error) {
    notify(error.message, true);
  } finally {
    clientState.busy = false;
  }
}

function pintarVehiculos(vehicles, titular) {
  const rows = $('#client-vehicles-rows');
  if (!vehicles.length) {
    rows.innerHTML = `<tr><td colspan="3" class="empty-state">${
      titular ? 'Este cliente todavía no tiene vehículos vinculados.' : 'Todos los vehículos tienen titular vinculado.'
    }</td></tr>`;
    return;
  }
  rows.innerHTML = vehicles.map((vehicle) => `<tr>
    <td><span class="plate-badge">${escapeHtml(vehicle.placa)}</span></td>
    <td>${escapeHtml([vehicle.marca, vehicle.modelo].filter(Boolean).join(' '))}</td>
    <td>${titular ? `Titular: ${escapeHtml(titular)}` : 'Vinculación pendiente (ficha de la Iteración 1)'}</td>
  </tr>`).join('');
}

async function showClientVehicles(id) {
  clientState.selectedId = id;
  clientState.confirmId = null;
  const panel = $('#client-vehicles-panel');
  const rows = $('#client-vehicles-rows');
  $('#link-vehicle-error').textContent = '';
  panel.hidden = false;
  rows.innerHTML = '<tr><td colspan="3" class="empty-state">Cargando vehículos…</td></tr>';
  try {
    const [client, vehicles] = await Promise.all([
      getJson(`/clientes/${id}`),
      getJson(`/clientes/${id}/vehiculos`)
    ]);
    $('#client-vehicles-title').textContent = `Vehículos de ${client.nombreCompleto}`;
    pintarVehiculos(vehicles, client.nombreCompleto);
  } catch (error) {
    rows.innerHTML = '<tr><td colspan="3" class="empty-state">No fue posible cargar los vehículos.</td></tr>';
    notify(error.message, true);
  }
}

/**
 * Lista de la Iteración 1 que aún no tiene titular: `GET /vehiculos` no filtra por cliente, así que el
 * filtro `clienteId` nulo se hace aquí. Sin esta vista el aviso de «vinculación pendiente» no tendría
 * dónde aparecer, porque los vehículos de un cliente siempre tienen cliente.
 */
async function showUnlinkedVehicles() {
  clientState.selectedId = null;
  const panel = $('#client-vehicles-panel');
  const rows = $('#client-vehicles-rows');
  $('#link-vehicle-error').textContent = '';
  panel.hidden = false;
  rows.innerHTML = '<tr><td colspan="3" class="empty-state">Cargando vehículos…</td></tr>';
  try {
    const vehicles = (await getJson('/vehiculos')).filter((vehicle) => !vehicle.clienteId);
    $('#client-vehicles-title').textContent = 'Vehículos sin cliente titular';
    pintarVehiculos(vehicles, null);
  } catch (error) {
    rows.innerHTML = '<tr><td colspan="3" class="empty-state">No fue posible cargar los vehículos.</td></tr>';
    notify(error.message, true);
  }
}

async function linkVehicleToClient() {
  const button = $('#link-vehicle-button');
  if (clientState.busy) return;
  if (!clientState.selectedId) {
    $('#link-vehicle-error').textContent = 'Abre primero «Vehículos» del cliente que será el titular.';
    return;
  }
  const plate = $('#link-plate').value.trim().toUpperCase();
  $('#link-plate').value = plate;
  $('#link-vehicle-error').textContent = '';
  if (!plate) {
    $('#link-vehicle-error').textContent = 'Escribe la placa del vehículo que quieres vincular.';
    return;
  }
  clientState.busy = true;
  button.disabled = true;
  try {
    await request(`/vehiculos/${encodeURIComponent(plate)}/cliente`, {
      method: 'PUT', body: JSON.stringify({ clienteId: clientState.selectedId })
    });
    notify('Titular vinculado.');
    $('#link-plate').value = '';
    await showClientVehicles(clientState.selectedId);
    await loadClients();
  } catch (error) {
    $('#link-vehicle-error').textContent = error.message;
  } finally {
    clientState.busy = false;
    button.disabled = false;
  }
}

$('#client-search-button').addEventListener('click', () => {
  clientState.criterio = $('#client-search').value.trim();
  clientState.activo = $('#client-status').value;
  clientState.page = 0;
  clientState.confirmId = null;
  loadClients();
});

$('#clients-prev').addEventListener('click', () => {
  clientState.page = Math.max(0, clientState.page - 1);
  loadClients();
});

$('#clients-next').addEventListener('click', () => {
  clientState.page += 1;
  loadClients();
});

$('#client-new-button').addEventListener('click', () => openClientForm(null));
$('#client-unlinked-button').addEventListener('click', showUnlinkedVehicles);
$('#client-form-cancel').addEventListener('click', closeClientForm);
$('#client-form').addEventListener('submit', submitClientForm);
$('#link-vehicle-button').addEventListener('click', linkVehicleToClient);

$('#clients-rows').addEventListener('click', async (event) => {
  const button = event.target.closest('button[data-action]');
  if (!button) return;
  const id = Number(button.dataset.id);
  if (button.dataset.action === 'editar') {
    try {
      openClientForm(await getJson(`/clientes/${id}`));
    } catch (error) {
      notify(error.message, true);
    }
    return;
  }
  if (button.dataset.action === 'vehiculos') {
    await showClientVehicles(id);
    return;
  }
  if (button.dataset.action === 'estado') {
    await cambiarEstadoCliente(id);
  }
});

// ---------------------------------------------------------------- celdas (bloque B3)

const cellState = {
  page: 0, size: 10, criterio: '', estado: '', tipo: '', piso: '', activo: '',
  editingId: null, confirmId: null, confirmAccion: null, lastPage: null, busy: false
};

const ETIQUETA_ESTADO_CELDA = { DISPONIBLE: 'Disponible', OCUPADA: 'Ocupada', MANTENIMIENTO: 'Mantenimiento' };
const CLASE_ESTADO_CELDA = { DISPONIBLE: 'active', OCUPADA: 'occupied', MANTENIMIENTO: 'maintenance' };

function cellsQuery() {
  const params = new URLSearchParams({ page: String(cellState.page), size: String(cellState.size) });
  if (cellState.criterio) params.set('criterio', cellState.criterio);
  if (cellState.estado) params.set('estado', cellState.estado);
  if (cellState.tipo) params.set('tipo', cellState.tipo);
  if (cellState.piso) params.set('piso', cellState.piso);
  if (cellState.activo) params.set('activo', cellState.activo);
  return params.toString();
}

function setCellsStatus(message) {
  $('#cells-status').textContent = message || '';
}

async function loadCells() {
  const rows = $('#cells-rows');
  rows.innerHTML = '<tr><td colspan="8" class="empty-state">Cargando celdas…</td></tr>';
  setCellsStatus('Consultando celdas…');
  try {
    const page = await getJson(`/celdas/pagina?${cellsQuery()}`);
    renderCells(page);
  } catch (error) {
    rows.innerHTML = '<tr><td colspan="8" class="empty-state">No fue posible cargar las celdas.</td></tr>';
    setCellsStatus('');
    notify(error.message, true);
  }
}

/**
 * Acciones que se pintan en la fila. No hay botón que escriba OCUPADA ni que libere una celda: la ocupación
 * llega con la entrada y se revoca con la salida o la cancelación, así que aquí solo se ofrecen las
 * transiciones que el backend también admite.
 */
function accionesDeCelda(cell) {
  const activa = cell.activo !== false;
  const acciones = [{ accion: 'editar', etiqueta: 'Editar', clase: 'subtle' }];
  if (activa && cell.estado === 'DISPONIBLE') acciones.push({ accion: 'mantenimiento', etiqueta: 'Mantenimiento', clase: 'subtle' });
  if (activa && cell.estado === 'MANTENIMIENTO') acciones.push({ accion: 'disponible', etiqueta: 'Volver disponible', clase: 'subtle' });
  if (activa && cell.estado !== 'OCUPADA') acciones.push({ accion: 'desactivar', etiqueta: 'Desactivar', clase: 'danger' });
  if (!activa) acciones.push({ accion: 'reactivar', etiqueta: 'Reactivar', clase: 'primary' });
  return acciones;
}

function renderCells(page) {
  const content = Array.isArray(page.content) ? page.content : [];
  const rows = $('#cells-rows');
  const soloAdmin = esAdministrador();
  cellState.lastPage = page;
  setCellsStatus('');
  if (!content.length) {
    rows.innerHTML = '<tr><td colspan="8" class="empty-state">No hay celdas con esos criterios.</td></tr>';
  } else {
    rows.innerHTML = content.map((cell) => {
      const activa = cell.activo !== false;
      const asignable = cell.disponible === true;
      const botones = soloAdmin ? accionesDeCelda(cell).map((item) => {
        const confirmando = cellState.confirmId === cell.id && cellState.confirmAccion === item.accion;
        return `<button class="button ${confirmando ? 'primary' : item.clase}" type="button" data-action="${item.accion}" data-id="${cell.id}">${confirmando ? 'Confirmar' : item.etiqueta}</button>`;
      }).join('') : '';
      return `<tr>
        <td><span class="plate-badge">${escapeHtml(cell.numero)}</span></td>
        <td>${escapeHtml(cell.piso)}</td>
        <td>${escapeHtml(cell.tipoVehiculo)}</td>
        <td><span class="status-chip ${CLASE_ESTADO_CELDA[cell.estado] || ''}">${escapeHtml(ETIQUETA_ESTADO_CELDA[cell.estado] || cell.estado)}</span></td>
        <td><span class="status-chip ${activa ? 'active' : 'inactive'}">${activa ? 'Activa' : 'Inactiva'}</span></td>
        <td><span class="status-chip${asignable ? ' active' : ''}">${asignable ? 'Asignable' : 'No asignable'}</span></td>
        <td>${escapeHtml(cell.observaciones ?? '—')}</td>
        <td class="row-actions">${botones}</td>
      </tr>`;
    }).join('');
  }
  const total = Number(page.totalElements ?? content.length);
  const ultima = Number(page.number ?? cellState.page) + 1;
  const totalPages = Number(page.totalPages ?? 1);
  $('#cells-page').textContent = `${content.length} de ${total} · página ${ultima} de ${Math.max(1, totalPages)}`;
  $('#cells-prev').disabled = ultima <= 1;
  $('#cells-next').disabled = page.last !== false;
}

function openCellForm(cell) {
  cellState.editingId = cell ? cell.id : null;
  cellState.confirmId = null;
  cellState.confirmAccion = null;
  const ocupada = Boolean(cell) && cell.estado === 'OCUPADA';
  $('#cell-form-title').textContent = cell ? `Editar celda ${cell.numero}` : 'Nueva celda';
  $('#cell-code').value = cell ? cell.numero : '';
  $('#cell-floor-input').value = cell ? cell.piso : '';
  $('#cell-type-input').value = cell ? cell.tipoVehiculo : 'CARRO';
  $('#cell-notes').value = cell && cell.observaciones ? cell.observaciones : '';
  // Con un vehículo adentro la identidad física se conserva: el formulario la muestra bloqueada y el
  // backend la vuelve a exigir igual que estaba.
  $('#cell-code').disabled = ocupada;
  $('#cell-floor-input').disabled = ocupada;
  $('#cell-type-input').disabled = ocupada;
  $('#cell-form-hint').hidden = !ocupada;
  $('#cell-form-error').textContent = '';
  $('#cell-form').hidden = false;
}

function closeCellForm() {
  cellState.editingId = null;
  $('#cell-code').disabled = false;
  $('#cell-floor-input').disabled = false;
  $('#cell-type-input').disabled = false;
  $('#cell-form-hint').hidden = true;
  $('#cell-form').hidden = true;
  $('#cell-form-error').textContent = '';
}

async function submitCellForm(event) {
  event.preventDefault();
  if (cellState.busy) return;
  const submit = event.currentTarget.querySelector('button[type="submit"]');
  const payload = {
    numero: $('#cell-code').value.trim(),
    piso: Number($('#cell-floor-input').value),
    tipoVehiculo: $('#cell-type-input').value,
    observaciones: $('#cell-notes').value.trim() || null
  };
  $('#cell-form-error').textContent = '';
  cellState.busy = true;
  submit.disabled = true;
  try {
    if (cellState.editingId) {
      await request(`/celdas/${cellState.editingId}`, { method: 'PUT', body: JSON.stringify(payload) });
      notify('Celda actualizada.');
    } else {
      await request('/celdas', { method: 'POST', body: JSON.stringify(payload) });
      notify('Celda registrada.');
    }
    closeCellForm();
    await loadCells();
    refreshDashboard();
  } catch (error) {
    $('#cell-form-error').textContent = error.message;
  } finally {
    cellState.busy = false;
    submit.disabled = false;
  }
}

/**
 * Desactivar, reactivar y las dos transiciones de mantenimiento piden confirmación en dos clics: la primera
 * pinta «Confirmar» y la segunda envía la solicitud. Así se evitan cambios involuntarios sobre el inventario
 * sin usar cuadros de diálogo nativos.
 */
async function cambiarCelda(id, accion) {
  if (cellState.confirmId !== id || cellState.confirmAccion !== accion) {
    cellState.confirmId = id;
    cellState.confirmAccion = accion;
    notify('Pulsa de nuevo para confirmar el cambio en esta celda.');
    if (cellState.lastPage) renderCells(cellState.lastPage);
    return;
  }
  if (cellState.busy) return;
  cellState.busy = true;
  cellState.confirmId = null;
  cellState.confirmAccion = null;
  try {
    if (accion === 'desactivar' || accion === 'reactivar') {
      await request(`/celdas/${id}/activo`, { method: 'PATCH', body: JSON.stringify({ activo: accion === 'reactivar' }) });
      notify(accion === 'reactivar' ? 'Celda reactivada.' : 'Celda desactivada.');
    } else {
      const estado = accion === 'mantenimiento' ? 'MANTENIMIENTO' : 'DISPONIBLE';
      await request(`/celdas/${id}/estado`, { method: 'PUT', body: JSON.stringify({ estado }) });
      notify(accion === 'mantenimiento' ? 'Celda en mantenimiento.' : 'Celda disponible de nuevo.');
    }
    await loadCells();
    refreshDashboard();
  } catch (error) {
    notify(error.message, true);
  } finally {
    cellState.busy = false;
  }
}

$('#cell-search-button').addEventListener('click', () => {
  cellState.criterio = $('#cell-search').value.trim();
  cellState.estado = $('#cell-state').value;
  cellState.tipo = $('#cell-type').value;
  cellState.piso = $('#cell-floor').value.trim();
  cellState.activo = $('#cell-active').value;
  cellState.page = 0;
  cellState.confirmId = null;
  cellState.confirmAccion = null;
  loadCells();
});

$('#cells-prev').addEventListener('click', () => {
  cellState.page = Math.max(0, cellState.page - 1);
  loadCells();
});

$('#cells-next').addEventListener('click', () => {
  cellState.page += 1;
  loadCells();
});

$('#cell-new-button').addEventListener('click', () => openCellForm(null));
$('#cell-form-cancel').addEventListener('click', closeCellForm);
$('#cell-form').addEventListener('submit', submitCellForm);

$('#cells-rows').addEventListener('click', async (event) => {
  const button = event.target.closest('button[data-action]');
  if (!button) return;
  const id = Number(button.dataset.id);
  if (button.dataset.action === 'editar') {
    try {
      openCellForm(await getJson(`/celdas/${id}`));
    } catch (error) {
      notify(error.message, true);
    }
    return;
  }
  await cambiarCelda(id, button.dataset.action);
});

/* ==========================================================================
   Gestión de Cuentas del Personal (Solo Administradores)
   ========================================================================== */

const userState = {
  list: [],
  criterio: '',
  tipo: '',
  activo: '',
  editingId: null,
  confirmId: null,
  busy: false
};

function setUsersStatus(message) {
  $('#users-status').textContent = message || '';
}

async function loadUsers() {
  if (!esAdministrador()) return;
  const rows = $('#users-rows');
  rows.innerHTML = '<tr><td colspan="7" class="empty-state">Cargando cuentas…</td></tr>';
  setUsersStatus('Consultando personal…');
  try {
    const list = await getJson('/usuarios');
    userState.list = Array.isArray(list) ? list : [];
    renderUsers();
  } catch (error) {
    rows.innerHTML = '<tr><td colspan="7" class="empty-state">No fue posible cargar las cuentas del personal.</td></tr>';
    setUsersStatus('');
    notify(error.message, true);
  }
}

function renderUsers() {
  const rows = $('#users-rows');
  setUsersStatus('');
  let filtered = userState.list;
  if (userState.criterio) {
    const c = userState.criterio.toLowerCase();
    filtered = filtered.filter((u) =>
      (u.nombre && u.nombre.toLowerCase().includes(c)) ||
      (u.apellido && u.apellido.toLowerCase().includes(c)) ||
      (u.documento && u.documento.toLowerCase().includes(c)) ||
      (u.correo && u.correo.toLowerCase().includes(c))
    );
  }
  if (userState.tipo) {
    filtered = filtered.filter((u) => u.tipo === userState.tipo);
  }
  if (userState.activo !== '') {
    const act = userState.activo === 'true';
    filtered = filtered.filter((u) => (u.activo !== false) === act);
  }

  if (!filtered.length) {
    rows.innerHTML = '<tr><td colspan="7" class="empty-state">No hay cuentas que coincidan con la búsqueda.</td></tr>';
    return;
  }

  rows.innerHTML = filtered.map((user) => {
    const activo = user.activo !== false;
    const confirmando = userState.confirmId === user.id;
    const etiquetaEstado = confirmando ? 'Confirmar' : (activo ? 'Desactivar' : 'Activar');
    const claseEstado = confirmando ? 'primary' : (activo ? 'danger' : 'subtle');
    return `<tr>
      <td>${escapeHtml([user.nombre, user.apellido].filter(Boolean).join(' '))}</td>
      <td><span class="plate-badge">${escapeHtml(user.documento)}</span></td>
      <td>${escapeHtml(user.correo)}</td>
      <td>${escapeHtml(user.telefono)}</td>
      <td><span class="status-chip ${user.tipo === 'ADMINISTRADOR' ? 'active' : ''}">${escapeHtml(user.tipo)}</span></td>
      <td><span class="status-chip ${activo ? 'active' : 'inactive'}">${activo ? 'Activo' : 'Inactivo'}</span></td>
      <td class="row-actions">
        <button class="button subtle" type="button" data-action="editar" data-id="${user.id}">Editar</button>
        <button class="button ${claseEstado}" type="button" data-action="estado" data-id="${user.id}">${etiquetaEstado}</button>
      </td>
    </tr>`;
  }).join('');
}

function openUserForm(user) {
  userState.editingId = user ? user.id : null;
  userState.confirmId = null;
  $('#user-form-title').textContent = user ? 'Editar cuenta del personal' : 'Nueva cuenta del personal';
  $('#user-name-input').value = user ? user.nombre : '';
  $('#user-lastname-input').value = user ? user.apellido : '';
  $('#user-doc-input').value = user ? user.documento : '';
  $('#user-email-input').value = user ? user.correo : '';
  $('#user-phone-input').value = user ? user.telefono : '';
  $('#user-type-input').value = user ? user.tipo : 'OPERADOR';

  const passwordField = $('#user-password-field');
  const passwordInput = $('#user-password-input');
  passwordInput.value = '';
  if (user) {
    passwordField.hidden = true;
    passwordInput.disabled = true;
    $('#user-form-hint').hidden = false;
  } else {
    passwordField.hidden = false;
    passwordInput.disabled = false;
    $('#user-form-hint').hidden = true;
  }
  $('#user-form-error').textContent = '';
  $('#user-form').hidden = false;
}

function closeUserForm() {
  userState.editingId = null;
  $('#user-password-input').value = '';
  $('#user-form').hidden = true;
  $('#user-form-error').textContent = '';
}

async function submitUserForm(event) {
  event.preventDefault();
  if (userState.busy) return;
  const submit = event.currentTarget.querySelector('button[type="submit"]');
  const password = $('#user-password-input').value;

  if (!userState.editingId && (!password || password.length < 15)) {
    $('#user-form-error').textContent = 'La clave debe tener al menos 15 caracteres.';
    return;
  }

  const payload = {
    nombre: $('#user-name-input').value.trim(),
    apellido: $('#user-lastname-input').value.trim(),
    documento: $('#user-doc-input').value.trim(),
    correo: $('#user-email-input').value.trim(),
    telefono: $('#user-phone-input').value.trim(),
    tipo: $('#user-type-input').value
  };

  if (!userState.editingId) {
    payload.clave = password;
  }

  // Limpiar el campo de contraseña inmediatamente del DOM para proteger la credencial
  $('#user-password-input').value = '';

  $('#user-form-error').textContent = '';
  userState.busy = true;
  submit.disabled = true;
  try {
    if (userState.editingId) {
      await request(`/usuarios/${userState.editingId}`, { method: 'PUT', body: JSON.stringify(payload) });
      notify('Cuenta actualizada.');
    } else {
      await request('/usuarios', { method: 'POST', body: JSON.stringify(payload) });
      notify('Cuenta registrada.');
    }
    closeUserForm();
    await loadUsers();
  } catch (error) {
    $('#user-form-error').textContent = error.message;
    notify(error.message, true);
  } finally {
    userState.busy = false;
    submit.disabled = false;
    $('#user-password-input').value = '';
  }
}

async function cambiarEstadoUsuario(id) {
  if (userState.confirmId !== id) {
    userState.confirmId = id;
    notify('Pulsa de nuevo para confirmar el cambio de estado.');
    renderUsers();
    return;
  }
  if (userState.busy) return;
  userState.busy = true;
  userState.confirmId = null;
  try {
    const user = userState.list.find((u) => u.id === id);
    const nuevoEstado = user ? !user.activo : false;
    await request(`/usuarios/${id}/activo`, { method: 'PATCH', body: JSON.stringify({ activo: nuevoEstado }) });
    notify(nuevoEstado ? 'Cuenta activada.' : 'Cuenta desactivada.');
    await loadUsers();
  } catch (error) {
    notify(error.message, true);
    renderUsers();
  } finally {
    userState.busy = false;
  }
}

$('#user-search-button').addEventListener('click', () => {
  userState.criterio = $('#user-search').value.trim();
  userState.tipo = $('#user-role-filter').value;
  userState.activo = $('#user-active-filter').value;
  userState.confirmId = null;
  renderUsers();
});

$('#user-new-button').addEventListener('click', () => openUserForm(null));
$('#user-form-cancel').addEventListener('click', closeUserForm);
$('#user-form').addEventListener('submit', submitUserForm);

$('#users-rows').addEventListener('click', async (event) => {
  const button = event.target.closest('button[data-action]');
  if (!button) return;
  const id = Number(button.dataset.id);
  if (button.dataset.action === 'editar') {
    const user = userState.list.find((u) => u.id === id);
    if (user) {
      openUserForm(user);
    } else {
      try {
        const fresh = await getJson(`/usuarios/${id}`);
        openUserForm(fresh);
      } catch (err) {
        notify(err.message, true);
      }
    }
    return;
  }
  if (button.dataset.action === 'estado') {
    await cambiarEstadoUsuario(id);
  }
});

if (sessionStorage.getItem(TOKEN_KEY)) {
  getJson('/auth/me').then(showApplication).catch((error) => notify(error.message, true));
}
