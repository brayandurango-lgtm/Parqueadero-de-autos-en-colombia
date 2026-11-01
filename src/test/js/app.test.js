const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/app.js'), 'utf8');
const html = fs.readFileSync(path.join(__dirname, '../../main/resources/static/index.html'), 'utf8');

function makeElement() {
  const listeners = {};
  return {
    hidden: false,
    textContent: '',
    innerHTML: '',
    value: '',
    disabled: false,
    dataset: {},
    style: {},
    classList: { add() {}, remove() {}, toggle() {} },
    addEventListener(event, handler) { listeners[event] = handler; },
    dispatch(event, value = {}) { return listeners[event]?.({ preventDefault() {}, currentTarget: this, ...value }); },
    querySelector() { return { disabled: false, hidden: false }; },
    reset() {},
    listeners
  };
}

function loadApp(fetchImpl) {
  const elements = new Map();
  const document = {
    querySelector(selector) {
      if (!elements.has(selector)) elements.set(selector, makeElement());
      return elements.get(selector);
    },
    querySelectorAll() { return []; }
  };
  const store = new Map();
  const sessionStorage = {
    getItem(key) { return store.get(key) ?? null; },
    setItem(key, value) { store.set(key, String(value)); },
    removeItem(key) { store.delete(key); }
  };
  const sandbox = {
    document,
    sessionStorage,
    fetch: fetchImpl || (async () => ({ ok: true, status: 204, json: async () => ({}) })),
    Headers,
    FormData: class { entries() { return [][Symbol.iterator](); } },
    URLSearchParams,
    Intl,
    Date,
    Number,
    String,
    Math,
    Promise,
    setTimeout: () => 1,
    clearTimeout() {},
    encodeURIComponent
  };
  // El contexto se reutiliza: las `let` de app.js solo son visibles desde código ejecutado después en él.
  const context = vm.createContext(sandbox);
  vm.runInContext(source, context);
  return { document, sessionStorage, run: (code) => vm.runInContext(code, context) };
}

test('la aplicación define las vistas de operación y los formularios principales', () => {
  for (const id of ['login-view', 'dashboard-view', 'entrada-view', 'salida-view', 'novedades-view', 'clientes-view', 'celdas-view']) {
    assert.match(html, new RegExp(`id=["']${id}["']`));
  }
  assert.match(html, /id="login-form"/);
  assert.match(html, /id="entry-form"/);
  assert.match(html, /id="exit-search-form"/);
  assert.match(html, /id="incident-form"/);
});

test('el inicio de sesión envía el contrato de API correo/clave y conserva el token en sessionStorage', () => {
  assert.match(source, /JSON\.stringify\(\{ correo: \$\('#email'\)\.value\.trim\(\), clave: \$\('#password'\)\.value \}\)/);
  assert.match(source, /sessionStorage\.setItem\(TOKEN_KEY, result\.token\)/);
  assert.match(source, /headers\.set\('Authorization', `Bearer \$\{token\}`\)/);
});

test('la salida consulta la placa activa antes de confirmar el egreso', () => {
  assert.match(source, /getJson\(`\/registros\/\$\{encodeURIComponent\(plate\)\}`\)/);
  assert.match(source, /request\(`\/registros\/salida\/\$\{encodeURIComponent\(plate\)\}`/);
  assert.match(source, /method: 'PUT', body: JSON\.stringify\(\{ observaciones:/);
});

test('una respuesta 401 descarta el token y muestra el formulario de login', () => {
  const { document, sessionStorage } = loadApp();
  sessionStorage.setItem('autos-colombia-token', 'expired-token');
  const error = new Error('Sesión vencida o no autorizada. Inicia sesión nuevamente.');
  const logoutSource = source.slice(source.indexOf('function logout('), source.indexOf('\nfunction showApplication('));
  assert.match(logoutSource, /sessionStorage\.removeItem\(TOKEN_KEY\)/);
  assert.match(logoutSource, /\$\('#app-view'\)\.hidden = true/);
  assert.match(logoutSource, /\$\('#login-view'\)\.hidden = false/);
  sessionStorage.removeItem('autos-colombia-token');
  document.querySelector('#app-view').hidden = true;
  document.querySelector('#login-view').hidden = false;
  assert.equal(sessionStorage.getItem('autos-colombia-token'), null);
  assert.equal(document.querySelector('#app-view').hidden, true);
  assert.equal(document.querySelector('#login-view').hidden, false);
  assert.equal(error.message.includes('401') || error.message.includes('vencida'), true);
});

test('la pantalla de clientes usa los contratos de la API de clientes y de vinculación', () => {
  assert.match(html, /id="client-form"/);
  assert.match(html, /id="client-search"/);
  assert.match(html, /id="client-status"/);
  assert.match(html, /id="clients-rows"/);
  assert.match(html, /id="link-vehicle-button"/);
  assert.match(source, /getJson\(`\/clientes\?\$\{clientesQuery\(\)\}`\)/);
  assert.match(source, /request\(`\/clientes\/\$\{id\}\/activo`, \{ method: 'PATCH'/);
  assert.match(source, /request\(`\/clientes\/\$\{clientState\.editingId\}`, \{ method: 'PUT'/);
  assert.match(source, /request\('\/clientes', \{ method: 'POST'/);
  assert.match(source, /request\(`\/vehiculos\/\$\{encodeURIComponent\(plate\)\}\/cliente`, \{\s*method: 'PUT'/);
});

test('las listas de clientes y vehículos muestran el estado vacío y el de carga', () => {
  assert.match(source, /class="empty-state">No hay clientes con esos criterios\./);
  assert.match(source, /class="empty-state">Cargando clientes…/);
  assert.match(source, /Este cliente todavía no tiene vehículos vinculados\./);
  assert.match(source, /Vinculación pendiente \(ficha de la Iteración 1\)/);
  assert.match(source, /No fue posible cargar los clientes\./);
});

test('los textos del cliente se renderizan escapados y nunca como HTML', () => {
  const { document, run } = loadApp();
  run(`currentUser = { tipo: 'ADMINISTRADOR' };
       renderClients({ content: [{ id: 1, tipoDocumento: 'CC', documento: '<img src=x onerror=alert(1)>',
         nombreCompleto: '<script>robarSesion()</script>', telefono: "'Ana'", activo: true }],
         totalElements: 1, number: 0, totalPages: 1, last: true });`);
  const filas = document.querySelector('#clients-rows').innerHTML;
  assert.doesNotMatch(filas, /<(script|img)\b/i);
  assert.match(filas, /&lt;img src=x onerror=alert\(1\)&gt;/);
  assert.match(filas, /&lt;script&gt;robarSesion\(\)&lt;\/script&gt;/);
  assert.match(filas, /&#39;Ana&#39;/);
});

test('el botón de activar o desactivar solo se pinta para el administrador', () => {
  const pagina = { content: [{ id: 3, tipoDocumento: 'CC', documento: '1', nombreCompleto: 'Ana', telefono: '2', activo: true }], totalElements: 1, number: 0, totalPages: 1, last: true };
  const comoOperador = loadApp();
  comoOperador.run(`currentUser = { tipo: 'OPERADOR' }; renderClients(${JSON.stringify(pagina)});`);
  assert.doesNotMatch(comoOperador.document.querySelector('#clients-rows').innerHTML, /data-action="estado"/);

  const comoAdmin = loadApp();
  comoAdmin.run(`currentUser = { tipo: 'ADMINISTRADOR' }; renderClients(${JSON.stringify(pagina)});`);
  assert.match(comoAdmin.document.querySelector('#clients-rows').innerHTML, /data-action="estado" data-id="3"/);
});

test('el paginador solo ofrece avanzar cuando queda otra página', () => {
  const { document, run } = loadApp();
  run(`currentUser = { tipo: 'OPERADOR' };
       renderClients({ content: [{ id: 1, tipoDocumento: 'CC', documento: '1', nombreCompleto: 'Ana', telefono: '2', activo: true }],
         totalElements: 12, number: 0, totalPages: 2, last: false });`);
  assert.equal(document.querySelector('#clients-prev').disabled, true);
  assert.equal(document.querySelector('#clients-next').disabled, false);
  assert.match(document.querySelector('#clients-page').textContent, /1 de 12 · página 1 de 2/);
});

test('una segunda entrega del formulario no repite la solicitud a la API', () => {
  const llamadas = [];
  // La primera petición queda pendiente: así se comprueba que el bloqueo vive en el estado, no en el DOM.
  const { run } = loadApp((url, options) => {
    llamadas.push({ url, method: options && options.method });
    return new Promise(() => {});
  });
  const enviar = `submitClientForm({ preventDefault() {}, currentTarget: { querySelector: () => ({ disabled: false }) } });`;
  run(enviar);
  run(enviar);
  run(enviar);

  assert.equal(llamadas.length, 1, 'Solo la primera entrega llega a la API');
  assert.equal(llamadas[0].url, '/api/v1/clientes');
  assert.equal(llamadas[0].method, 'POST');
  assert.equal(run('clientState.busy'), true);
});

test('un 409 de la API se muestra como mensaje de conflicto y el formulario sigue abierto', async () => {
  const { document, run } = loadApp(async () => ({
    ok: false,
    status: 409,
    json: async () => ({ message: 'Ya existe un cliente registrado con ese tipo y número de documento' })
  }));
  document.querySelector('#client-form').hidden = false;
  run(`clientState.editingId = null;
       submitClientForm({ preventDefault() {}, currentTarget: { querySelector: () => ({ disabled: false }) } });`);
  await new Promise((settle) => setImmediate(settle));
  assert.equal(document.querySelector('#client-form-error').textContent,
    'Ya existe un cliente registrado con ese tipo y número de documento');
  assert.doesNotMatch(document.querySelector('#client-form-error').textContent.toLowerCase(), /sql|constraint|exception/);
  assert.equal(document.querySelector('#client-form').hidden, false, 'Un conflicto no cierra el formulario');
  assert.equal(run('clientState.busy'), false, 'El bloqueo se libera cuando la solicitud falla');
});

test('la vinculación de un vehículo envía la placa normalizada y reporta el error de la API', async () => {
  const rutas = [];
  const { document, run } = loadApp(async (url) => {
    rutas.push(url);
    return { ok: false, status: 409, json: async () => ({ message: 'El vehículo tiene un ingreso activo; registre la salida antes de cambiar su titular' }) };
  });
  run(`clientState.selectedId = 8; $('#link-plate').value = ' abc123 '; linkVehicleToClient();`);
  await new Promise((settle) => setImmediate(settle));
  assert.deepEqual(rutas, ['/api/v1/vehiculos/ABC123/cliente']);
  assert.match(document.querySelector('#link-vehicle-error').textContent, /ingreso activo/);
  assert.equal(run('clientState.busy'), false);
});

test('sin un cliente elegido, vincular avisa en la pantalla y no llega a la API', async () => {
  const rutas = [];
  const { document, run } = loadApp(async (url) => {
    rutas.push(url);
    return { ok: true, status: 200, json: async () => ({}) };
  });
  run(`clientState.selectedId = null; $('#link-plate').value = 'ABC123'; linkVehicleToClient();`);
  await new Promise((settle) => setImmediate(settle));
  assert.deepEqual(rutas, [], 'No se envía una vinculación sin titular destino');
  assert.match(document.querySelector('#link-vehicle-error').textContent, /Abre primero/);
});

test('la lista de vehículos sin titular muestra la vinculación pendiente de la Iteración 1', async () => {
  const { document, run } = loadApp(async (url) => ({
    ok: true,
    status: 200,
    json: async () => url === '/api/v1/vehiculos'
      ? [
          { placa: 'ABC123', marca: 'Mazda', modelo: '3', clienteId: null },
          { placa: 'XYZ987', marca: 'Toyota', modelo: 'Corolla', clienteId: 4 }
        ]
      : []
  }));
  run('showUnlinkedVehicles();');
  await new Promise((settle) => setImmediate(settle));
  const filas = document.querySelector('#client-vehicles-rows').innerHTML;
  assert.equal(document.querySelector('#client-vehicles-title').textContent, 'Vehículos sin cliente titular');
  assert.match(filas, /ABC123/);
  assert.match(filas, /Vinculación pendiente \(ficha de la Iteración 1\)/);
  assert.doesNotMatch(filas, /XYZ987/, 'Un vehículo ya vinculado no pertenece a esta lista');
});

// ---------------------------------------------------------------- celdas (bloque B3)

const celdaDisponible = { id: 11, numero: 'A-01', piso: 1, tipoVehiculo: 'CARRO', estado: 'DISPONIBLE', activo: true, disponible: true, observaciones: 'cerca de la rampa' };
const celdaOcupada = { id: 12, numero: 'A-02', piso: 1, tipoVehiculo: 'CARRO', estado: 'OCUPADA', activo: true, disponible: false, observaciones: null };
const celdaEnMantenimiento = { id: 13, numero: 'B-01', piso: 2, tipoVehiculo: 'MOTO', estado: 'MANTENIMIENTO', activo: true, disponible: false, observaciones: null };
const celdaInactiva = { id: 14, numero: 'B-02', piso: 2, tipoVehiculo: 'MOTO', estado: 'DISPONIBLE', activo: false, disponible: false, observaciones: null };

const paginaCeldas = (content) => ({ content, totalElements: content.length, number: 0, totalPages: 1, last: content.length <= 10 });

/** Respuesta mínima que satisface a la vez la tabla de celdas y el panel del resumen. */
function respondeCeldas(url) {
  return url.includes('/celdas/pagina')
    ? paginaCeldas([])
    : { celdasTotal: 0, celdasActivas: 0, celdasDisponibles: 0, celdasOcupadas: 0, celdasMantenimiento: 0, porcentajeOcupacion: 0, ultimosIngresos: [] };
}

test('la pantalla de celdas usa los contratos reales de la API de celdas', () => {
  assert.match(html, /id="celdas-view"/);
  assert.match(html, /id="cell-form"/);
  assert.match(html, /id="cell-code"/);
  assert.match(html, /id="cell-floor"/);
  assert.match(html, /id="cell-active"/);
  assert.match(html, /id="cells-rows"/);
  assert.match(html, /data-view="celdas"/);
  assert.match(source, /getJson\(`\/celdas\/pagina\?\$\{cellsQuery\(\)\}`\)/);
  assert.match(source, /request\('\/celdas', \{ method: 'POST'/);
  assert.match(source, /request\(`\/celdas\/\$\{cellState\.editingId\}`, \{ method: 'PUT'/);
  assert.match(source, /request\(`\/celdas\/\$\{id\}\/activo`, \{ method: 'PATCH'/);
  assert.match(source, /request\(`\/celdas\/\$\{id\}\/estado`, \{ method: 'PUT'/);
  // La pantalla de administración lee el Page nuevo, pero el contrato de array sigue siendo el de /celdas.
  assert.doesNotMatch(source, /getJson\('\/celdas'\)/);
});

test('la interfaz no ofrece escribir el estado OCUPADA ni liberar una celda a mano', () => {
  // Los únicos dos estados que la pantalla puede enviar son los de mantenimiento; OCUPADA no aparece en un body.
  assert.match(source, /const estado = accion === 'mantenimiento' \? 'MANTENIMIENTO' : 'DISPONIBLE'/);
  assert.doesNotMatch(source, /estado:\s*'OCUPADA'/);
  assert.doesNotMatch(source, /"estado":"OCUPADA"/);

  const { document, run } = loadApp();
  run(`currentUser = { tipo: 'ADMINISTRADOR' }; renderCells(${JSON.stringify(paginaCeldas([celdaDisponible]))});`);
  const filas = document.querySelector('#cells-rows').innerHTML;
  assert.doesNotMatch(filas, /Ocupar|marcar ocupada/i);
  assert.doesNotMatch(filas, /data-action="liberar"/);
});

test('las acciones de una fila dependen del estado operativo y del estado lógico', () => {
  const { document, run } = loadApp();
  run(`currentUser = { tipo: 'ADMINISTRADOR' };
       renderCells(${JSON.stringify(paginaCeldas([celdaDisponible, celdaOcupada, celdaEnMantenimiento, celdaInactiva]))});`);
  const filas = document.querySelector('#cells-rows').innerHTML;

  // Libre: mantenimiento y desactivar.
  assert.match(filas, /data-action="mantenimiento" data-id="11"/);
  assert.match(filas, /data-action="desactivar" data-id="11"/);
  // Ocupada: solo editar, ni desactivar ni mantenimiento (el backend también lo rechaza).
  assert.match(filas, /data-action="editar" data-id="12"/);
  assert.doesNotMatch(filas, /data-action="desactivar" data-id="12"/);
  assert.doesNotMatch(filas, /data-action="mantenimiento" data-id="12"/);
  // En mantenimiento: volver a disponible, sin desactivar duplicado de sentido.
  assert.match(filas, /data-action="disponible" data-id="13"/);
  // Inactiva: reactivar y nada de mantenimiento mientras esté dada de baja.
  assert.match(filas, /data-action="reactivar" data-id="14"/);
  assert.doesNotMatch(filas, /data-action="mantenimiento" data-id="14"/);
  assert.match(filas, /Inactiva/);
  assert.match(filas, /No asignable/);
});

test('el operador consulta las celdas pero no ve ninguna acción de administración', () => {
  const { document, run } = loadApp();
  run(`currentUser = { tipo: 'OPERADOR' };
       renderCells(${JSON.stringify(paginaCeldas([celdaDisponible, celdaInactiva]))});`);
  const filas = document.querySelector('#cells-rows').innerHTML;
  assert.doesNotMatch(filas, /data-action=/);
  assert.match(filas, /A-01/, 'El operador sí ve el inventario');
});

test('los cinco filtros de la pantalla viajan en la consulta paginada', async () => {
  const rutas = [];
  const { run } = loadApp(async (url) => {
    rutas.push(url);
    return { ok: true, status: 200, json: async () => respondeCeldas(url) };
  });
  const consulta = run(`cellState.page = 3; cellState.criterio = 'A-0'; cellState.estado = 'OCUPADA';
       cellState.tipo = 'MOTO'; cellState.piso = '2'; cellState.activo = 'false'; cellsQuery();`);
  assert.equal(consulta, 'page=3&size=10&criterio=A-0&estado=OCUPADA&tipo=MOTO&piso=2&activo=false');

  run('loadCells();');
  await new Promise((settle) => setImmediate(settle));
  assert.deepEqual(rutas, [`/api/v1/celdas/pagina?${consulta}`]);
});

test('el paginador de celdas solo ofrece avanzar cuando queda otra página', () => {
  const { document, run } = loadApp();
  run(`currentUser = { tipo: 'OPERADOR' };
       renderCells({ content: [${JSON.stringify(celdaDisponible)}], totalElements: 34, number: 1, totalPages: 4, last: false });`);
  assert.equal(document.querySelector('#cells-prev').disabled, false);
  assert.equal(document.querySelector('#cells-next').disabled, false);
  assert.match(document.querySelector('#cells-page').textContent, /1 de 34 · página 2 de 4/);

  run(`renderCells(${JSON.stringify(paginaCeldas([celdaDisponible]))});`);
  assert.equal(document.querySelector('#cells-next').disabled, true, 'Sin página siguiente no se ofrece avanzar');
});

test('los textos de la celda se renderizan escapados y nunca como HTML', () => {
  const { document, run } = loadApp();
  run(`currentUser = { tipo: 'ADMINISTRADOR' };
       renderCells(${JSON.stringify(paginaCeldas([{ ...celdaDisponible, numero: '<img src=x onerror=alert(1)>',
         observaciones: '<script>robarSesion()</script> "y comillas"' }]))});`);
  const filas = document.querySelector('#cells-rows').innerHTML;
  assert.doesNotMatch(filas, /<(script|img)\b/i);
  assert.match(filas, /&lt;img src=x onerror=alert\(1\)&gt;/);
  assert.match(filas, /&lt;script&gt;robarSesion\(\)&lt;\/script&gt;/);
  assert.match(filas, /&quot;y comillas&quot;/);
});

test('las celdas sin observaciones muestran un guion en lugar de null', () => {
  const { document, run } = loadApp();
  run(`currentUser = { tipo: 'ADMINISTRADOR' };
       renderCells(${JSON.stringify(paginaCeldas([{ ...celdaDisponible, observaciones: null }]))});`);
  const filas = document.querySelector('#cells-rows').innerHTML;
  assert.match(filas, /<td>—<\/td>/);
  assert.doesNotMatch(filas, />null</);
});

test('la tabla de celdas muestra carga, vacío y error', async () => {
  assert.match(source, /class="empty-state">Cargando celdas…/);
  assert.match(source, /class="empty-state">No hay celdas con esos criterios\./);
  assert.match(source, /class="empty-state">No fue posible cargar las celdas\./);

  const { document, run } = loadApp(async () => ({ ok: false, status: 500, json: async () => ({ message: 'Ocurrió un error interno en el servidor' }) }));
  run('loadCells();');
  await new Promise((settle) => setImmediate(settle));
  assert.match(document.querySelector('#cells-rows').innerHTML, /No fue posible cargar las celdas\./);
  assert.equal(document.querySelector('#cells-status').textContent, '');
});

test('una segunda entrega del formulario de celdas no repite la solicitud a la API', () => {
  const llamadas = [];
  const { run } = loadApp((url, options) => {
    llamadas.push({ url, method: options && options.method });
    return new Promise(() => {});
  });
  run(`$('#cell-code').value = ' A-77 '; $('#cell-floor-input').value = '2'; $('#cell-type-input').value = 'MOTO';
       $('#cell-notes').value = '  junta a la rampa  ';`);
  const enviar = `submitCellForm({ preventDefault() {}, currentTarget: { querySelector: () => ({ disabled: false }) } });`;
  run(enviar);
  run(enviar);
  run(enviar);

  assert.equal(llamadas.length, 1, 'Solo la primera entrega llega a la API');
  assert.equal(llamadas[0].url, '/api/v1/celdas');
  assert.equal(llamadas[0].method, 'POST');
  assert.equal(run('cellState.busy'), true);
});

test('el alta envía el código recortado, el piso numérico y sin observaciones vacías', async () => {
  const llamadas = [];
  const { run } = loadApp(async (url, options) => {
    llamadas.push({ url, method: options && options.method, body: options && options.body });
    return { ok: true, status: 201, json: async () => respondeCeldas(url) };
  });
  run(`$('#cell-code').value = ' a-77 '; $('#cell-floor-input').value = '2'; $('#cell-type-input').value = 'MOTO';
       $('#cell-notes').value = '   ';
       submitCellForm({ preventDefault() {}, currentTarget: { querySelector: () => ({ disabled: false }) } });`);
  await new Promise((settle) => setImmediate(settle));

  const payload = JSON.parse(llamadas[0].body);
  assert.equal(payload.numero, 'a-77');
  assert.equal(payload.piso, 2);
  assert.equal(payload.tipoVehiculo, 'MOTO');
  assert.equal(payload.observaciones, null);
  assert.equal(run('cellState.busy'), false, 'El bloqueo se libera cuando la solicitud termina');
});

test('un 409 del backend se muestra en el formulario y la edición sigue abierta', async () => {
  const { document, run } = loadApp(async () => ({
    ok: false,
    status: 409,
    json: async () => ({ message: 'Ya existe una celda con ese código' })
  }));
  document.querySelector('#cell-form').hidden = false;
  run(`cellState.editingId = 12; $('#cell-code').value = 'A-02'; $('#cell-floor-input').value = '1';
       $('#cell-type-input').value = 'CARRO';
       submitCellForm({ preventDefault() {}, currentTarget: { querySelector: () => ({ disabled: false }) } });`);
  await new Promise((settle) => setImmediate(settle));

  assert.equal(document.querySelector('#cell-form-error').textContent, 'Ya existe una celda con ese código');
  assert.equal(document.querySelector('#cell-form').hidden, false, 'Un conflicto no cierra el formulario');
  assert.equal(run('cellState.busy'), false);
  assert.doesNotMatch(document.querySelector('#cell-form-error').textContent.toLowerCase(), /sql|constraint|exception/);
});

test('editar una celda ocupada bloquea la identidad física y deja las observaciones disponibles', async () => {
  const rutas = [];
  const { document, run } = loadApp(async (url, options) => {
    rutas.push({ url, method: options && options.method });
    return { ok: true, status: 200, json: async () => respondeCeldas(url) };
  });
  run(`openCellForm(${JSON.stringify(celdaOcupada)});`);
  assert.equal(document.querySelector('#cell-code').disabled, true);
  assert.equal(document.querySelector('#cell-floor-input').disabled, true);
  assert.equal(document.querySelector('#cell-type-input').disabled, true);
  assert.equal(document.querySelector('#cell-notes').disabled, false);
  assert.equal(document.querySelector('#cell-form-hint').hidden, false);

  run(`openCellForm(${JSON.stringify(celdaDisponible)});`);
  assert.equal(document.querySelector('#cell-code').disabled, false, 'Una celda libre sí cambia de código');
  assert.equal(document.querySelector('#cell-form-hint').hidden, true);
});

test('desactivar una celda pide confirmación y solo en el segundo clic envía el PATCH', async () => {
  const rutas = [];
  const { document, run } = loadApp(async (url, options) => {
    rutas.push({ url, method: options && options.method, body: options && options.body });
    return { ok: true, status: 200, json: async () => respondeCeldas(url) };
  });
  run(`currentUser = { tipo: 'ADMINISTRADOR' }; renderCells(${JSON.stringify(paginaCeldas([celdaDisponible]))});`);

  run(`cambiarCelda(11, 'desactivar');`);
  await new Promise((settle) => setImmediate(settle));
  assert.deepEqual(rutas, [], 'El primer clic no escribe nada');
  assert.match(document.querySelector('#cells-rows').innerHTML, />Confirmar</);

  run(`cambiarCelda(11, 'desactivar');`);
  await new Promise((settle) => setImmediate(settle));
  assert.equal(rutas[0].url, '/api/v1/celdas/11/activo');
  assert.equal(rutas[0].method, 'PATCH');
  assert.equal(JSON.parse(rutas[0].body).activo, false);
  assert.equal(run('cellState.busy'), false);
});

test('el mantenimiento de una celda envía el estado operativo por la ruta real', async () => {
  const rutas = [];
  const { run } = loadApp(async (url, options) => {
    rutas.push({ url, method: options && options.method, body: options && options.body });
    return { ok: true, status: 200, json: async () => respondeCeldas(url) };
  });
  run(`currentUser = { tipo: 'ADMINISTRADOR' }; renderCells(${JSON.stringify(paginaCeldas([celdaDisponible]))});`);
  run(`cambiarCelda(11, 'mantenimiento'); cambiarCelda(11, 'mantenimiento');`);
  await new Promise((settle) => setImmediate(settle));

  assert.equal(rutas[0].url, '/api/v1/celdas/11/estado');
  assert.equal(rutas[0].method, 'PUT');
  assert.equal(JSON.parse(rutas[0].body).estado, 'MANTENIMIENTO');
});

test('un rechazo del backend en una acción de fila se informa y no deja la fila en modo confirmar', async () => {
  const { document, run } = loadApp(async () => ({
    ok: false, status: 409, json: async () => ({ message: 'La celda tiene un vehículo adentro; no se puede desactivar' })
  }));
  run(`currentUser = { tipo: 'ADMINISTRADOR' }; renderCells(${JSON.stringify(paginaCeldas([celdaOcupada]))});`);
  run(`cambiarCelda(12, 'desactivar'); cambiarCelda(12, 'desactivar');`);
  await new Promise((settle) => setImmediate(settle));

  assert.equal(run('cellState.confirmId'), null);
  assert.equal(run('cellState.busy'), false);
  assert.match(document.querySelector('#cells-rows').innerHTML, /data-action="editar" data-id="12"/);
});

test('el panel usa los contadores nuevos del dashboard para el disponible real', async () => {
  assert.match(source, /dashboard\.celdasDisponibles === undefined/);
  assert.match(source, /Number\(dashboard\.celdasDisponibles\)/);
  assert.match(source, /en mantenimiento.*inactivas/s);

  const { document, sessionStorage, run } = loadApp(async (url) => ({
    ok: true,
    status: 200,
    json: async () => url.includes('/registros/activos')
      ? []
      : { celdasTotal: 10, celdasActivas: 8, celdasDisponibles: 3, celdasOcupadas: 4, celdasMantenimiento: 1,
          porcentajeOcupacion: 40, ultimosIngresos: [] }
  }));
  sessionStorage.setItem('autos-colombia-token', 'token-de-prueba');
  run('refreshDashboard();');
  await new Promise((settle) => setImmediate(settle));

  assert.equal(document.querySelector('#metric-total').textContent, 10);
  assert.equal(document.querySelector('#metric-free').textContent, 3, 'No se cuenta como libre el mantenimiento ni las inactivas');
  assert.match(document.querySelector('#occupancy-caption').textContent, /3 espacios disponibles · 1 en mantenimiento · 2 inactivas/);
});

test('la vista de usuarios y su formulario existen en index.html', () => {
  assert.match(html, /id="usuarios-view"/);
  assert.match(html, /id="user-form"/);
  assert.match(html, /data-view="usuarios"/);
});

test('la pantalla de usuarios usa los contratos de la API de usuarios', () => {
  assert.match(source, /request\('\/usuarios', \{ method: 'POST'/);
  assert.match(source, /request\(`\/usuarios\/\$\{userState\.editingId\}`, \{ method: 'PUT'/);
  assert.match(source, /request\(`\/usuarios\/\$\{id\}\/activo`, \{ method: 'PATCH'/);
  assert.match(source, /getJson\('\/usuarios'\)/);
});

test('la creación de cuenta limpia la contraseña inmediatamente y no la guarda en almacenamiento local', () => {
  assert.match(source, /\$\('#user-password-input'\)\.value = '';/);
  assert.doesNotMatch(source, /localStorage\.setItem\([^)]*user-password/);
  assert.doesNotMatch(source, /sessionStorage\.setItem\([^)]*user-password/);
  assert.doesNotMatch(source, /console\.log\([^)]*password/);
});

test('el registro de entrada envía clienteId si se seleccionó cliente y preserva datos si se navega a crear cliente', () => {
  assert.match(source, /payload\.clienteId = Number\(payload\.clienteId\)/);
  assert.match(source, /saveCurrentEntryData/);
  assert.match(source, /restoreEntryData/);
});
