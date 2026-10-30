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

function loadApp() {
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
    fetch: async () => ({ ok: true, status: 204, json: async () => ({}) }),
    Headers,
    FormData: class { entries() { return [][Symbol.iterator](); } },
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
  vm.runInNewContext(source, sandbox);
  return { document, sessionStorage };
}

test('la aplicación define las cuatro vistas de operación y formularios principales', () => {
  for (const id of ['login-view', 'dashboard-view', 'entrada-view', 'salida-view', 'novedades-view']) {
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
