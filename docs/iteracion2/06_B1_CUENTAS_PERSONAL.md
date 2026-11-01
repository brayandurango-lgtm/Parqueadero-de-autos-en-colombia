# 06 — Bloque B1: cuentas del personal y permisos

Bitácora de la fase autorizada como «AUTORIZACIÓN — BLOQUE B1: CUENTAS DEL PERSONAL Y PERMISOS».
Ejecutada el **viernes 30 de octubre de 2026** en `C:\Users\Administrador\Downloads\Parqueadero de autos
en colombia`, sobre `HEAD = e701c75`. **Cero `git add`, cero commits, cero push.**

Todo lo afirmado aquí está respaldado por una corrida hecha en esta sesión, con el comando literal y su
salida. Lo que no se ejecutó está marcado como pendiente.

---

## 1. Qué se implementó

| # | Función | Dónde |
|---|---------|-------|
| 1 | La contraseña inicial del personal llega por la solicitud y es obligatoria; se retiró la contraseña fija compartida del controlador | `UsuarioController`, `UsuarioService.crear` |
| 2 | DTO dedicado de creación; la contraseña no existe en el DTO de edición ni en ninguna respuesta | `presentation/dto/UsuarioCrearDTO.java` (nuevo) |
| 3 | Política de contraseña: mínimo 15 caracteres y **tope real de 72 bytes UTF-8**, rechazado en vez de truncado | `ContrasenaDentroDelLimiteBCrypt` + validador (nuevos) |
| 4 | Toda petición protegida consulta `activo`: un token emitido antes de desactivar la cuenta deja de autenticar con `401` | `AuthService.validarToken` |
| 5 | Formato de error `ErrorResponse` también en el `401` del filtro (antes el cuerpo venía vacío) | `SecurityConfig.escribirRespuestaNoAutorizada` |
| 6 | Endpoint dedicado `PATCH /api/v1/usuarios/{id}/activo` con estado obligatorio | `UsuarioController.cambiarEstado`, `CambiarEstadoUsuarioDTO` (nuevo) |
| 7 | Regla del último administrador activo: no se desactiva ni se degrada; se aplica por igual a `PATCH`, `PUT` y `DELETE` | `UsuarioService.verificarCoberturaDeAdministradores` |
| 8 | Bloqueo pesimista sobre el conjunto de administradores activos antes de decidir (sin cambios de esquema) | `UsuarioRepository.findByTipoAndActivo` con `@Lock(PESSIMISTIC_WRITE)` |
| 9 | Cuerpo ilegible o ausente devuelve `400` (antes `500`) sin repetir el contenido del payload | `GlobalExceptionHandler.handleHttpMessageNotReadableException` |

No se tocó: clientes, celdas, entrada/salida, pagos, `V1__schema_inicial.sql`, `pom.xml`, workflows,
frontend. La regla de Pico y Placa sigue siendo la de Bogotá D.C. (limitación preexistente, no se cambió).

---

## 2. El límite real de BCrypt: medido, no supuesto

El encargo pedía **«verificar el límite real de entrada de la implementación BCrypt utilizada»** y
**«rechazar explícitamente cualquier entrada que exceda ese límite: nunca truncarla silenciosamente»**.

Sondeo ejecutado con `mvn -B -o test -Dtest=BcryptLimiteSondeoTest` (21:43:49, 2026-10-30). Librería
resuelta en el proyecto: `spring-security-crypto-6.2.4.jar` (Spring Boot 3.2.5). Salida literal:

```
SONDEO ascii-71 chars=71 bytes=71      -> encode OK, matchesSelf=true
SONDEO ascii-72 chars=72 bytes=72      -> encode OK, matchesSelf=true
SONDEO ascii-73 chars=73 bytes=73      -> encode OK, matchesSelf=true
SONDEO multibyte-agudo-36 chars=36 bytes=72 -> encode OK, matchesSelf=true
SONDEO multibyte-agudo-37 chars=37 bytes=74 -> encode OK, matchesSelf=true
SONDEO multibyte-emoji-20 chars=40 bytes=80 -> encode OK, matchesSelf=true
SONDEO multibyte-emoji-21 chars=42 bytes=84 -> encode OK, matchesSelf=true
COLISION chars-distintos-mismo-prefijo72 matches=true
```

Lectura de esa salida:

1. **La librería no lanza excepción** con entradas de 73, 74, 80 u 84 bytes. Confiar en que BCrypt avise
   sería dejar el truncado en manos del algoritmo.
2. **Sí trunca en silencio**: dos contraseñas de 82 caracteres que comparten los primeros 72 bytes generan
   un hash que valida a ambas. Aceptar esas entradas equivaldría a degradar la credencial sin avisar.
3. **El límite son 72 bytes UTF-8, no 72 caracteres**: 36 «á» (72 bytes) caben; 37 «á» (74 bytes) no; un
   pictograma emoji ocupa 4 bytes y 2 unidades UTF-16.

Consecuencia en el diseño: la validación vive en la capa de solicitud, **antes** de llegar al codificador,
para que el exceso se rechace con `400` y no se guarde nunca un hash truncado. El comportamiento medido
quedó fijado en el `LimiteRealDeBcryptTest` (3 pruebas), de modo que una actualización de la librería que
cambie esto rompa la suite en lugar de pasar desapercibida. El sondeo temporal se eliminó.

### Política resultante

| Regla | Implementación | Mensaje al cliente |
|-------|----------------|--------------------|
| Obligatoria | `@NotBlank` | «La clave es obligatoria» |
| Mínimo **15 caracteres** | `@Size(min = 15)` | «La clave debe tener al menos 15 caracteres» |
| Máximo **72 bytes UTF-8** (entre 15 y 72 caracteres según el contenido) | `@ContrasenaDentroDelLimiteBCrypt` | «La clave supera el límite de 72 bytes que admite el algoritmo de hash y no se trunca» |
| Sin composición obligatoria (mayúsculas, dígitos, símbolos) | no existe ninguna regla de contenido | — |
| Espacios y Unicode permitidos; **sin `trim` y sin normalización** | se guarda `dto.getClave()` tal cual | — |
| Algoritmo | BCrypt con `PasswordEncoder` existente; sin cambios y sin dependencias nuevas | — |

Rango efectivo en caracteres: 15 como mínimo y 72 como máximo, pero el techo baja si el contenido usa
caracteros multibyte — con puro emoji el rango válido es 15 a 18 (60 a 72 bytes). Nota técnica: `@Size`
cuenta unidades UTF-16, así que un pictograma cuenta 2; el límite de bytes lo mide el validador propio.

---

## 3. Endpoints y ejemplos

Base: `/api/v1/usuarios`. Requieren rol `ADMINISTRADOR` con token JWT en `Authorization: Bearer <token>`.
Los valores de ejemplo son **ficticios y de prueba**: no son credenciales reales de ningún entorno.

### 3.1 Crear cuenta — `POST /api/v1/usuarios`

```json
{
  "nombre": "Ana",
  "apellido": "Pérez",
  "documento": "12345678",
  "correo": "ana.perez@autoscolombia.test",
  "telefono": "3001112233",
  "tipo": "OPERADOR",
  "clave": "Clave inicial de ejemplo, quince+"
}
```

`201 Created`:

```json
{
  "id": 12,
  "nombre": "Ana",
  "apellido": "Pérez",
  "documento": "12345678",
  "correo": "ana.perez@autoscolombia.test",
  "telefono": "3001112233",
  "tipo": "OPERADOR",
  "activo": true
}
```

La respuesta **no** contiene `clave` ni `claveHash`. Sin `clave`, o con una clave que viole la política:
`400` con el mapa de campos y **sin registrar la cuenta**.

### 3.2 Cambiar el estado — `PATCH /api/v1/usuarios/{id}/activo`

```json
{ "activo": false }
```

`200 OK` con el perfil proyectado (`"activo": false`). `{"activo": null}`, `{}` o ausencia de cuerpo:
`400`. Id desconocido: `404`. Último administrador activo: `409`.

### 3.3 Editar — `PUT /api/v1/usuarios/{id}`

Cuerpo = `UsuarioDTO` (igual que antes del bloque, **sin campo de contraseña**). Los cambios de `tipo` o
`activo` que llegan por esta ruta pasan por las mismas reglas del §5; no es una vía alternativa.

### 3.4 Desactivación lógica — `DELETE /api/v1/usuarios/{id}`

Sigue devolviendo `204` y equivale a `activo = false`. **No existe borrado físico de cuentas** (decisión 7
del encargo): `UsuarioRepository.delete*` no se invoca en ningún camino de la API.

### 3.5 Errores

| Situación | Código | Cuerpo |
|-----------|--------|--------|
| Sin token, token roto o **cuenta desactivada** | `401` | `ErrorResponse` con `message: "No autorizado"` (único mensaje, sin distinguir la causa) |
| Cuenta activa sin rol `ADMINISTRADOR` | `403` | `ErrorResponse` con `message: "No tiene permisos para realizar esta operación"` |
| Política de contraseña, campo obligatorio, cuerpo ilegible | `400` | `ValidationErrorResponse` (con mapa de campos) o `ErrorResponse` para el cuerpo ilegible |
| Correo/documento duplicado, regla del último administrador | `409` | `ErrorResponse` |
| Id inexistente | `404` | `ErrorResponse` |

---

## 4. Contratos: qué cambió y por qué era necesario

| Cambio | Antes | Después | Motivo |
|--------|-------|---------|--------|
| `POST /usuarios` exige `clave` | Se aceptaba la creación sin una contraseña inicial propia | `400` si falta o incumple la política; cada cuenta recibe una clave inicial individual | Eliminar contraseñas compartidas |
| `POST /usuarios` responde el perfil persistido | Se devolvía el DTO de entrada, que podía incluir la clave | Respuesta proyectada por `UsuarioDTO`, sin clave ni hash | Evitar exponer credenciales |
| `UsuarioService.crear` | La clave viajaba como parámetro independiente | Recibe `UsuarioCrearDTO`; el valor solo se utiliza para generar el hash | Reducir exposición accidental en logs |
| Cuerpo ausente o ilegible | Error genérico `500` | `400` con mensaje fijo, sin reflejar el payload | No devolver datos sensibles |
| `401` del filtro | Respuesta sin formato consistente | JSON `ErrorResponse` | Mantener el contrato de errores |
| Métodos CORS | No incluían `PATCH` | `PATCH` permitido | Habilitar cambio de estado desde el navegador |

Las pruebas existentes conservaron sus objetivos; se adaptaron a que la respuesta ya no devuelve el DTO de entrada. La contraseña inicial no aparece en respuestas ni se conserva en el campo del formulario tras el envío.

---

## 5. Último administrador activo

### 5.1 Regla

`verificarCoberturaDeAdministradores(usuario, tipoNuevo, activoNuevo)` solo interviene cuando la operación
**retira** un administrador que estaba activo (desactivarlo o cambiarlo de rol). En ese caso exige que exista
al menos **otro** administrador activo distinto; si no, `BusinessRuleException` → `409`, y se lanza **antes**
de mutar la entidad, de modo que no hay escritura parcial posible (las pruebas lo comprueban recargando la
fila).

| Operación sobre el último administrador activo | Resultado |
|---|---|
| `PATCH {"activo": false}` | `409`, sigue activa |
| `PUT` con `tipo: OPERADOR` | `409`, sigue `ADMINISTRADOR` |
| `PUT` con `activo: false` | `409` (ruta canalizada) |
| `DELETE` | `409` (borrado lógico = desactivación) |
| `PUT` cambiando nombre/telefono con `tipo: ADMINISTRADOR`, `activo: true` | `200`, y **no** consulta el conjunto de administradores |
| Desactivar un `OPERADOR` con historial | `200`; sus datos y las FK de `registro` siguen apuntando a la cuenta |
| Repetir el estado ya vigente | `200` sin `save`, sin tocar `claveHash` ni `fecha_registro` |

Un administrador **ya inactivo** sí puede degradarse si existe otro activo: la regla protege la cobertura de
cuentas activas, no la fila. Con `activo = false` no puede iniciar sesión ni usar tokens, así que esa ruta no
genera un bloqueo inalcanzable.

### 5.2 Concurrencia: lo que se hizo y hasta dónde llega

- La decisión se toma sobre `findByTipoAndActivo(ADMINISTRADOR, true)` anotado con
  `@Lock(LockModeType.PESSIMISTIC_WRITE)`, dentro de los métodos `@Transactional`. Hibernate traduce eso a
  `SELECT ... FOR UPDATE` sobre las filas del conjunto, **sin añadir columnas ni tablas** (no se tocó `V1`).
- Los dos caminos que alteran rol/estado (`cambiarEstado` y `actualizar`) usan el mismo guard, así que
  comparten el bloqueo. `crear` no lo necesita: insertar no reduce la cobertura.
- Lectura esperada en PostgreSQL: la segunda transacción espera el `FOR UPDATE` y, al retomarse, vuelve a
  leer el conjunto con el cambio ya comprometido, por lo que una de las dos operaciones concurrentes debe
  terminar en `409`.
- **Límites honestos:**
  1. **H2 no constituye evidencia de esa garantía en PostgreSQL.** Las 33 pruebas de integración corren sobre
     H2 en memoria (`MODE=PostgreSQL`, `ddl-auto: create-drop`, Flyway apagado) y de forma **secuencial**: no
     hay dos transacciones compitiendo en ninguna prueba. Lo verificado es la semántica funcional, no el
     comportamiento bajo concurrencia.
  2. El bloqueo cubre filas existentes; un `INSERT` concurrente de un nuevo administrador podría no verse, lo
     cual solo **aumenta** la cobertura, no la reduce.
  3. La lectura de `findAll()` en `crear` (ineficiencia preexistente de B0, registrada y no corregida) sigue
     ahí.
  4. La comprobación de unicidad de correo/documento y esta regla compiten con la base real: si `V1` se ejecuta
     en PostgreSQL, los `UNIQUE` siguen siendo la última barrera.
  5. Una escritura directa en base de datos (fuera de la API) no respeta el bloqueo.

**No se declara terminada la garantía de concurrencia**: queda como riesgo pendiente de verificación en
PostgreSQL (§8).

---

## 6. Token después de desactivar y de reactivar

El encargo pidió no vender esto como revocación permanente y documentar qué pasa con un token anterior al
reactivar. Literal de `AuthService.validarToken`, que es lo único que se agregó:

```java
if (!Boolean.TRUE.equals(usuario.getActivo())) {
    throw new AuthenticationException("La cuenta de usuario está inactiva");
}
```

- Se consulta `activo` **en cada petición protegida** (la cadena real es: token → `JwtAuthenticationFilter` →
  `AuthService.validarToken` → `findByCorreo` → estado → `@PreAuthorize`).
- Cuenta desactivada: `validarToken` lanza, el filtro **no** propaga la excepción (la captura y continúa sin
  autenticar) → `ExceptionTranslationFilter` → *entry point* → **401** con cuerpo `ErrorResponse`. No hay 500.
- El mensaje de 401 es el mismo «No autorizado» que para un token ausente o roto: la API no revela por qué
  falló la credencial.
- **Si la cuenta se reactiva, ese mismo token vuelve a funcionar** hasta su expiración (`expiration-ms`
  8 horas), porque solo se consulta el estado y **no** se añadió ninguna infraestructura de revocación
  (lista negra, `jti`, versión de token). La prueba
  `jwtRealDeUnaCuentaDesactivadaRecibe401SinEfectosYAlReactivarVuelveAFuncionar` ejecuta exactamente ese
  ciclo y aserta el `200` de reintento tras reactivar. Por eso no se afirma «token revocado».
- El `login` de una cuenta inactiva seguía y sigue rechazado; ahora con `409` comprobado por prueba.

---

## 7. Pruebas: resultados reales

### 7.1 Rojo (contra el código anterior al bloque)

Comando: `mvn -B -o test -Dtest=CuentasPersonalIntegrationTest` · 21:53:17 → 21:54:18 · salida:

```
[ERROR] Tests run: 31, Failures: 25, Errors: 0, Skipped: 0 ... CuentasPersonalIntegrationTest
[INFO] BUILD FAILURE
```

Fallos literales que reproducen los defectos (los demás son el mismo patrón por ruta):

```
crear_sinLaPropiedadClave_devuelve400YNoPersisteLaCuenta     Status expected:<400> but was:<201>
crear_claveVacia_devuelve400YNoPersisteLaCuenta              Status expected:<400> but was:<201>
crear_claveDeSoloEspacios_devuelve400YNoPersisteLaCuenta     Status expected:<400> but was:<201>
crear_claveDeCatorceCaracteres_...                           Status expected:<400> but was:<201>
crear_claveQueExcedeLos72Bytes_devuelve400SinTruncarNiPersistir  Status expected:<400> but was:<201>
crear_claveMultibyteQueExcedeLos72Bytes_devuelve400SinTruncar    Status expected:<400> but was:<201>
crear_conClaveValida_guardaUnHashVerificable...              JSON path "$.activo" expected:<true> but was:<null>
elBorradoLogicoTampocoDesactivaAlUltimoAdministrador         Status expected:<409> but was:<204>
degradarAlUltimoAdministradorActivoPorPutDevuelve409...      Status expected:<409> but was:<200>
elPutNoEsUnaRutaAlternativaParaDesactivarAlUltimoAdministrador Status expected:<409> but was:<200>
cambiarEstado_conUsuarioInexistenteDevuelve404               Status expected:<404> but was:<500>
crear_sinCuerpoHttp_devuelve400                              Status expected:<400> but was:<500>
crear_cuerpoMalformado_devuelve400YSinFiltrarElContenido     Status expected:<400> but was:<500>
sinTokenRecibe401ConElFormatoDeErrorDeSeguridad              No value at JSON path "$.status"
```

**Hallazgo accidental, con evidencia en esta corrida:** una **ruta inexistente** devuelve `500`, no `404`
(`cambiarEstado_*` daba `500` porque `PATCH .../activo` no existía y el manejador genérico de `Exception`
atrapa el `NoResourceFoundException` de Spring Boot 3.2). Es un defecto preexistente de la capa de error,
**no corregido en B1** porque está fuera del alcance autorizado; se registra aquí con su prueba como
evidencia. Seis de las 31 pruebas ya pasaban antes del bloque (403 en `GET`, `401` con token inválido, `404`
de `PUT`/`DELETE`, `200` al editar datos del admin, y los dos límites que por azar no violaban nada).

### 7.2 Verde dirigido

Comando: `mvn -B -o test -Dtest='CuentasPersonalIntegrationTest,UsuarioServiceTest,UsuarioControllerTest,AuthServiceTest,ValidacionContrasenaDeCuentaTest,LimiteRealDeBcryptTest,UsuarioIntegridadMockMvcTest'`
· 22:18:17 → 22:19:20 · **No usó `-Dmaven.test.failure.ignore`.**

```
[INFO] Tests run: 33, Failures: 0 -- CuentasPersonalIntegrationTest
[INFO] Tests run: 26, Failures: 0 -- UsuarioServiceTest
[INFO] Tests run: 11, Failures: 0 -- ValidacionContrasenaDeCuentaTest
[INFO] Tests run:  8, Failures: 0 -- UsuarioIntegridadMockMvcTest
[INFO] Tests run:  8, Failures: 0 -- AuthServiceTest
[INFO] Tests run:  5, Failures: 0 -- UsuarioControllerTest
[INFO] Tests run:  3, Failures: 0 -- LimiteRealDeBcryptTest
[INFO] Tests run: 94, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Dos correcciones propias durante esta fase, ambas en pruebas y no en producción:
`conOtroAdministradorActivo...` degradaba a un admin que había quedado como único activo (la regla hace
bien en devolver `409`), y `operadorRecibe403...` usaba el token del administrador en el `POST`. Se
separó en tres pruebas (`...LaDesactivacionSePermite`, `...LaDegradacionSePermite`,
`unaVezQueQuedaUnSoloAdministradorActivoEseTampocoSePuedeDegradar`) y se usó el token del operador.
También se corrigió una aserción mía sobre `String.length()` de un pictograma (40 unidades UTF-16, 20
code points, 80 bytes).

### 7.3 Regresión de cierre

| Comando | Resultado |
|---------|-----------|
| `mvn -B clean verify` (22:21:51 → 22:23:17, 2026-10-30) | `Tests run: 220, Failures: 0, Errors: 0, Skipped: 0` · `BUILD SUCCESS` · `Total time: 01:22 min` · **código de salida 0** |
| `node --test src/test/js/app.test.js` (misma sesión) | `tests 4 · pass 4 · fail 0 · duration_ms 170.8612` · Node v26.7.0 |

Las cuatro líneas literales que el cierre volcó en consola, transcritas aquí para que la bitácora no dependa
del archivo temporal que se generó con `mvn -B clean verify > target_verificado.log` y ya no existe:

```
[INFO] Tests run: 220, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  01:22 min
[INFO] Finished at: 2026-10-30T22:23:17-05:00
```

220 = 161 de la línea de B0 + 59 nuevas: `CuentasPersonalIntegrationTest` 33, `ValidacionContrasenaDeCuentaTest`
11, `UsuarioServiceTest` +10, `LimiteRealDeBcryptTest` 3, `UsuarioControllerTest` +1, `AuthServiceTest` +1.
Las 161 anteriores siguen ejecutándose; ninguna se eliminó ni se desactivó.

### 7.4 Cobertura de esta corrida (`target/site/jacoco/`, leído después de `clean verify`)

Global (77 clases analizadas): **líneas 977/1069 (91.4 %)**, instrucciones 5836/10268 (56.8 %),
ramas 187/1016 (18.4 %), métodos 602/828 (72.7 %).

Por código tocado en B1:

| Clase | Líneas | Ramas | Métodos |
|-------|--------|-------|---------|
| `UsuarioService` | 74/74 (100 %) | 35/36 | 15/15 |
| `SecurityConfig` | 32/32 | 0/0 | 9/9 |
| `GlobalExceptionHandler` | 87/87 | 12/12 | 15/15 |
| `UsuarioController` | 10/10 | 0/0 | 6/6 |
| `ContrasenaDentroDelLimiteBCryptValidator` | 4/4 | 4/4 | 2/2 |
| `UsuarioCrearDTO` | 12/12 | 0/70 | 20/23 |
| `CambiarEstadoUsuarioDTO` | 5/5 | 0/14 | 5/9 |
| `AuthService` | 48/57 | 11/16 | 7/7 |

La **única rama sin cubrir de `UsuarioService`** está en `obtenerPorId` (líneas 187-188), método preexistente
que B1 no modificó y del que solo se prueba el camino de «no encontrado». Los centenares de ramas
descubiertas en los DTO (`0/70`, `0/14`) son generados por Lombok (`equals`/`hashCode`/`builder`), medición
ya documentada en fases anteriores: en B0, 709 de 746 ramas perdidas provenían de código generado. El
descenso porcentual de ramas (17.8 % → 18.4 % global, con más denominador) no indica un retroceso: las ramas
nuevas de negocio (guard, validador, manejador 400) sí quedaron cubiertas.

### 7.5 Contra los 13 casos obligatorios

| # | Caso exigido | Prueba | Estado |
|---|--------------|--------|--------|
| 1 | Clave válida → hash guardado y verificable; ni clave ni hash en la respuesta | `crear_conClaveValida_guardaUnHashVerificableYNoDevuelveClaveNiHash` + `elToStringDeLaSolicitudDeCreacionNoRevelaLaClave` + `elUnicoDtoQueTransportaLaClaveEsElDeCreacion` | ✅ |
| 2 | Clave ausente/vacia/solo espacios/corta → `400` sin cuenta persistida | 4 pruebas `crear_*` + 3 de `ValidacionContrasenaDeCuentaTest` | ✅ |
| 3 | Excede el límite real → rechazo controlado, sin truncar, **con entrada multibyte** | `crear_claveQueExcedeLos72Bytes...`, `crear_claveMultibyteQueExcedeLos72Bytes_devuelve400SinTruncar`, `caracteresDeCuatroBytesSeMidenTambienEnBytes`, `LimiteRealDeBcryptTest` | ✅ |
| 4 | Editar datos sin clave está permitido y conserva el hash previo | `actualizar_sinClave_conservaElHashPrevio` | ✅ |
| 5 | `ADMINISTRADOR` puede listar/crear/editar/activar-desactivar | `administradorPuedeListarCrearEditarYCambiarElEstado` | ✅ |
| 6 | `OPERADOR` recibe `403` | `operadorRecibe403EnTodaLaGestionDeCuentas` (GET, POST, PUT y PATCH; y sin efectos) | ✅ |
| 7 | Petición sin autenticar → `401` | `sinTokenRecibe401ConElFormatoDeErrorDeSeguridad`, `tokenInvalidoRecibe401` | ✅ |
| 8 | **JWT real**: login activo → endpoint protegido → desactivar por la ruta autorizada → reutilizar el token → `401` y sin efectos | `jwtRealDeUnaCuentaDesactivadaRecibe401SinEfectosYAlReactivarVuelveAFuncionar` (con token obtenido en `/auth/login`, sin `@WithMockUser`) | ✅ |
| 9 | El último administrador no se desactiva ni se degrada, sin modificaciones parciales | `desactivarAlUltimoAdministradorActivoDevuelve409SinModificacionParcial`, `degradarAlUltimoAdministradorActivoPorPut...` + 3 unitarias | ✅ |
| 10 | Con otro administrador activo la operación válida sí se permite | `conOtroAdministradorActivoLaDesactivacionSePermite`, `...LaDegradacionSePermite` | ✅ |
| 11 | La regla no se salta por `PUT` (ni por `DELETE`) | `elPutNoEsUnaRutaAlternativaParaDesactivarAlUltimoAdministrador`, `elBorradoLogicoTampocoDesactivaAlUltimoAdministrador` | ✅ |
| 12 | `PATCH` sin estado o con `null` → `400` | `cambiarEstado_conActivoNuloDevuelve400`, `cambiarEstado_conCuerpoSinLaPropiedadDevuelve400`, `crear_sinCuerpoHttp_devuelve400` | ✅ |
| 13 | Usuario inexistente → `404` | `cambiarEstado_conUsuarioInexistenteDevuelve404`, `rutasDeEdicionConUsuarioInexistenteDevuelven404` | ✅ |

Ninguno se sustituyó solo por `@WithMockUser`: de hecho no se usó `@WithMockUser` en ninguna prueba de B1;
los 13 casos se resolvieron con JWT reales o con pruebas unitarias de servicio/validador.

### 7.6 Nada de contraseñas en la salida

Como evidencia de la regla «no la registres en logs, auditoría, excepciones ni `toString`». Los cuatro
chequeos se ejecutaron el 2026-10-30 sobre `target/surefire-reports/` (25 archivos) y sobre el registro
completo de la corrida de cierre; **ninguno imprime el valor buscado**, solo conteos.

| # | Chequeo | Comando | Resultado |
|---|---|---|---|
| 1 | Ningún literal de contraseña de las pruebas aparece en los reportes | `grep -R -F -f target/claves_prueba.txt target/surefire-reports/ \| wc -l` | **0 coincidencias** |
| 2 | El mismo conjunto de literales tampoco aparece en el registro de consola de `mvn -B clean verify` | `grep -F -f target/claves_prueba.txt <log> \| wc -l` | **0 coincidencias** |
| 3 | Hibernate no vuelca los valores ligados (solo la forma del `INSERT`, con `?`) | `grep -c 'binding parameter'` sobre el log y sobre los reportes | **0** en ambos |
| 4 | Las únicas 4 líneas del log que mencionan la palabra `clave` son dos `insert into usuario (…, clave_hash, …) values (?,?,…)` que acompañan a un error de unicidad `23505-224` (prueba de integridad de B0) y el marcador `Binary file … matches` de `grep`; ninguna contiene el secreto | `grep -a -i 'insert into usuario'` | placeholders, sin valores |

Cómo se construyó `target/claves_prueba.txt` (15 candidatos): extracción automática de los literales entre
comillas de las líneas que mencionan `clave` en los 7 archivos de prueba tocados por B1
(`CuentasPersonalIntegrationTest`, `ValidacionContrasenaDeCuentaTest`, `LimiteRealDeBcryptTest`,
`UsuarioServiceTest`, `UsuarioControllerTest`, `AuthServiceTest`, `UsuarioIntegridadIntegrationTest`),
descartando los vacíos. **Alcance y límite del chequeo:** es una búsqueda de subcadena, por lo que cubre los
literales escritos tal cual, pero no las claves ensambladas en tiempo de ejecución (`repeat()`, concatenación
o `random`); para esas la garantía real no es el `grep` sino el contrato verificado por prueba: el DTO de
salida no tiene campo `clave` ni `claveHash` (caso 1 de §7.5) y `UsuarioCrearDTO.toString()` excluye la clave.
`GlobalExceptionHandler.handleHttpMessageNotReadableException` descarta `ex.getMessage()` (Spring incluye
un fragmento del payload) y `ValidacionContrasenaDeCuentaTest` aserta que el mensaje del validador no
repite la contraseña.

- `AuditoriaService` solo persiste lo que el llamante le pasa y `UsuarioService` no lo invoca; no se añadió
  ninguna escritura de auditoría en B1.
- Queda sin verificar el caso `toString` de `LoginRequest.clave` (preexistente, fuera del alcance de B1: es
  la clave de login, no la de una cuenta nueva). Se registra como pendiente menor.

---

## 8. Pendientes explícitos

**PostgreSQL (ninguna verificación hecha; el encargo prohíbe conectar):**

1. La traducción real de `SELECT ... FOR UPDATE` sobre el conjunto de administradores activos y su
   comportamiento con dos transacciones concurrentes (§5.2). Nada de esto es evidencia en PostgreSQL.
2. Que la app arranque contra `V1__schema_inicial.sql` con `ddl-auto: validate` y Flyway: no se ejecutó la
   aplicación en esta sesión.
3. Los `409` de restricción declarada en `V1` (`uk_usuario_documento`, `uk_usuario_correo`) — mismo hueco ya
   documentado en B0 §7: H2 no reporta esos nombres.
4. El `login` de la cuenta sembrada por `V1` y su hash de fábrica: no se tocó ni se rotó.

**Funcionalidad que B1 NO implementa (exclusiones de la decisión 5, registradas como no hechas):**

- Recuperación de contraseña por correo.
- Restablecimiento administrativo de la contraseña (un administrador **no** puede fijar una clave nueva a
  otra cuenta; solo puede desactivar/reactivar y editar datos).
- Cambio obligatorio de contraseña en el primer acceso.
- MFA / segundo factor.
- Revocación persistente de tokens (lista negra, `jti`, versión de credencial).
- Sin `V2__...sql`: no se creó ninguna migración, así que no hay nueva entidad ni columna; el esquema sigue
  siendo el de `V1`.

**Frontend:** `src/main/resources/static/` no llama a `/api/v1/usuarios` en absoluto (verificado con `grep`),
por lo que el cambio de contrato del `POST` no rompe la UI actual. La **pantalla de gestión de personal no
existe**: HU-I2-01, HU-I2-02 y HU-I2-03 quedan sin parte de interfaz, que corresponde al bloque B5.

**Defecto preexistente descubierto y no corregido:** ruta inexistente → `500` en lugar de `404` (§7.1).

---

## 9. Matriz de permisos (verificada con JWT reales)

| Operación | Sin token | Token inválido o cuenta inactiva | `OPERADOR` | `ADMINISTRADOR` |
|-----------|-----------|----------------------------------|------------|-----------------|
| `GET /api/v1/usuarios` | `401` | `401` | `403` | `200` |
| `POST /api/v1/usuarios` | `401` | `401` | `403` | `201` / `400` / `409` |
| `PUT /api/v1/usuarios/{id}` | `401` | `401` | `403` | `200` / `404` / `409` |
| `PATCH /api/v1/usuarios/{id}/activo` | `401` | `401` | `403` | `200` / `400` / `404` / `409` |
| `DELETE /api/v1/usuarios/{id}` | `401` | `401` | `403` | `204` / `404` / `409` |
| `POST /api/v1/auth/login` | público | público | público | público |

La seguridad a nivel de método **sí estaba habilitada**: `@EnableMethodSecurity` en `SecurityConfig.java:29`
y `@PreAuthorize("hasRole('ADMINISTRADOR')")` a nivel de clase; B1 lo comprobó por HTTP (antes no había
prueba que lo ejercitara, ver `04_...md` §6.1). El `403` de una cuenta activa sin permiso se conservó. Los
permisos de entrada/salida y de los demás módulos no se alteraron; la verificación de que un `OPERADOR`
registra entradas sigue cubriendo las 5 pruebas de `RegistroControllerIntegrationTest`.

---

## 10. Decisiones del encargo y cumplimiento

| Decisión | Cumplido | Cómo |
|----------|----------|------|
| 1. `Usuario` sigue representando solo personal (`ADMINISTRADOR`, `OPERADOR`) | ✅ | Ningún cambio de modelo; el cliente sigue excluido |
| 2. Solo `ADMINISTRADOR` administra estas cuentas | ✅ | `@PreAuthorize` de clase + prueba `403` real |
| 3. Mínimo 15 caracteres, sin reglas de composición, espacios y Unicode permitidos, sin `trim`, BCrypt intacto, límite real medido y exceso rechazado | ✅ | §2; prueba de fidelidad `crear_claveConEspaciosYUnicode_seConservaExactaYPermiteIniciarSesion` |
| 4. Editar datos nunca cambia la contraseña | ✅ | `actualizar` no toca `claveHash` (prueba unitaria y de integración) |
| 5. Recuperación, reset administrativo, cambio forzado y MFA quedan fuera | ✅ | Exclusiones registradas en §8, sin presentarlas como implementadas |
| 6. Endpoint dedicado `PATCH /api/v1/usuarios/{id}/activo` con `{"activo": true|false}`, sin aceptar ausente o nulo | ✅ | §3.2 |
| 7. Sin borrado físico de cuentas | ✅ | §3.4 |

Restricciones del encargo: sin conexión a PostgreSQL, sin migraciones, sin tocar `V1`, sin frontend nuevo,
sin cambios de workflow, sin herramientas de sistema, sin dependencias nuevas, sin `git add`/commit/push, sin
tocar la carpeta de la Iteración 1, sin exponer credenciales. **Sin uso de `-Dmaven.test.failure.ignore`.**

---

## 11. Archivos

Nuevos (producción, 140 líneas en 4 archivos): `presentation/dto/UsuarioCrearDTO.java` (59),
`presentation/dto/ContrasenaDentroDelLimiteBCrypt.java` (37),
`presentation/dto/ContrasenaDentroDelLimiteBCryptValidator.java` (23),
`presentation/dto/CambiarEstadoUsuarioDTO.java` (21).

Modificados (producción): `UsuarioService.java`, `UsuarioController.java`, `AuthService.java`,
`UsuarioRepository.java`, `SecurityConfig.java`, `GlobalExceptionHandler.java`. El `diff` acumulado contra
`HEAD` sigue incluyendo B0 (`+248 / -36` en 6 archivos); B1 añade 4 archivos completos y deja el resto de
líneas de `UsuarioService`, `SecurityConfig`, `AuthService`, `UsuarioRepository`, `UsuarioController` y el
nuevo manejador.

Nuevos (prueba, 860 líneas en 3 archivos): `integration/CuentasPersonalIntegrationTest.java` (643, 33 casos),
`presentation/dto/ValidacionContrasenaDeCuentaTest.java` (158, 11 casos),
`service/LimiteRealDeBcryptTest.java` (59, 3 casos).

Modificados (prueba): `UsuarioServiceTest.java` (+10 casos), `AuthServiceTest.java` (+1),
`UsuarioControllerTest.java` (+1 y 2 reescritas por el cambio de contrato).

`target_verificado.log` **no** estaba dentro de `target/`: se escribió en la raíz del repositorio al
redirigir la salida de `mvn -B clean verify` (238 líneas, 31 358 bytes). Es un artefacto mío de esta sesión,
nunca añadió al índice de git, y ya no hace falta: las cuatro líneas de cierre están transcritas en §7.3 y el
resultado de los chequeos de fuga en §7.6. Se eliminó después de esa transcripción. El listado de literales
auxiliar (`target/claves_prueba.txt`) quedó dentro de `target/`, que sí es ignorado por git.
