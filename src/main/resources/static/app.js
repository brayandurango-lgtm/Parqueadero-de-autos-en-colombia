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
  navigate('dashboard');
  refreshDashboard();
}

function navigate(view) {
  document.querySelectorAll('.view-section').forEach((section) => { section.hidden = section.id !== `${view}-view`; });
  document.querySelectorAll('.nav-link').forEach((button) => button.classList.toggle('active', button.dataset.view === view));
  const titles = { dashboard: 'Resumen', entrada: 'Registrar entrada', salida: 'Registrar salida', novedades: 'Novedades' };
  $('#page-title').textContent = titles[view] || 'Autos Colombia';
  if (view === 'dashboard') refreshDashboard();
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
    const free = Math.max(0, total - occupied);
    const percent = Number(dashboard.porcentajeOcupacion || 0);
    $('#metric-total').textContent = total;
    $('#metric-free').textContent = free;
    $('#metric-occupied').textContent = occupied;
    $('#metric-percent').textContent = `${percent.toFixed(1)}%`;
    $('#occupancy-large').textContent = `${occupied} / ${total}`;
    $('#occupancy-progress').style.width = `${Math.max(0, Math.min(100, percent))}%`;
    $('#occupancy-caption').textContent = `${free} espacios disponibles`;
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

$('#entry-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const submit = form.querySelector('button[type="submit"]');
  const payload = Object.fromEntries(new FormData(form).entries());
  payload.placa = payload.placa.trim().toUpperCase();
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

if (sessionStorage.getItem(TOKEN_KEY)) {
  getJson('/auth/me').then(showApplication).catch((error) => notify(error.message, true));
}
