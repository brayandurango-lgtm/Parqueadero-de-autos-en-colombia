# 04 — Especificación de implementación de la Iteración 2

**Proyecto:** Sistema de información del parqueadero Autos Colombia
**Fase:** Cierre de preparación y especificación para implementar usuarios (personal) y celdas
**Carpeta:** `C:\Users\Administrador\Downloads\Parqueadero de autos en colombia`
**Fecha:** 2026-10-30 · **última actualización:** 2026-10-31, al cerrar el bloque B3

> **Naturaleza de este documento.** Es un insumo técnico compartido para la coordinación del equipo. El
> alcance descrito es **una propuesta del equipo para resolver el enunciado de la actividad**; no es una
> instrucción textual del profesor ni una aprobación de él. Los entregables formales de requerimientos,
> historias de usuario, casos de uso y mockups siguen siendo los de Isabella: este documento **no los
> sustituye**.

> **Estado de este documento.** Ya no es solo especificación. Los bloques **B0** (bitácora `05`), **B1**
> (`06`), **B2** (`07`) y **B3** (`08`) están implementados y probados sobre H2. Lo demás —cliente dentro del
> flujo de entrada, la vista de personal, PostgreSQL y los diagramas de StarUML— sigue siendo propuesta. La
> pantalla de celdas **existe pero no se revisó en navegador** (§17). Las secciones §1, §9.3, §10.2 y §12
> conservan redacción de cuando nada estaba hecho; donde importa, se corrigió o se marcó el estado real. La
> lista de lo que este documento **no** afirma está en §17.

Todo lo que se afirma como **estado actual** proviene de leer el código de esta carpeta; se indica la
ubicación. Lo que se plantea como meta aparece marcado como **objetivo propuesto**.

---

## 1. Alcance y exclusiones

### Dentro del alcance

| Bloque | Contenido |
|---|---|
| **A. Clientes del parqueadero** | Entidad separada de las cuentas del personal, sin contraseña ni rol de acceso; registro, consulta, actualización y activación/desactivación; documento único; relación con vehículos; conservación de histórico; compatibilidad con los vehículos ya existentes. |
| **B. Personal del sistema** | Conservar `ADMINISTRADOR` y `OPERADOR`; solo el administrador administra cuentas; contraseña inicial obligatoria y no compartida; validaciones de duplicados correctas; una cuenta desactivada no puede seguir usando operaciones protegidas; regla para no dejar el sistema sin administrador activo. |
| **C. Celdas** | Alta/edición/desactivación/reactivación/mantenimiento por el administrador; consulta y uso por el operador; identificación única; baja lógica; bloqueo de desactivación si hay ocupación activa; desactivación permitida con historial cerrado; no asignar celdas inactivas ni en mantenimiento; `OCUPADA` solo como consecuencia de una entrada válida; la salida libera solo la celda correspondiente; evitar doble ocupación concurrente. |

### Fuera del alcance (explícito)

Pagos, cobros y tarifas · reservas o asignación fija por mensualidad · nuevas reglas de pico y placa ·
rediseño total del frontend · cambio de framework · publicación de imágenes o despliegue.

Si aparece código de pagos en el repositorio, se conserva tal cual y no se amplía.

### Estado actual relevante (verificado leyendo el código)

*Esta lista es el retrato de la línea base al iniciar la Iteración 2; se conserva sin corregir para poder
comparar. El estado de cada requisito después de **B0, B1 y B2** está en §4, en §9 y en el inventario de
§14. En particular, dos afirmaciones de abajo ya no son ciertas: sí existe entidad `Cliente` (B2) y
`validarToken` sí consulta `activo` (B1).*

- Persistencia real: **7 tablas** (`usuario`, `vehiculo`, `celda`, `registro`, `novedad`, `comprobante`,
  `auditoria_log`) creadas por `V1__schema_inicial.sql`, con `ddl-auto: validate` en el perfil por defecto.
  Cualquier entidad nueva exige una migración `V2`, o la aplicación **no arranca**.
- `EstadoCelda` tiene `DISPONIBLE`, `OCUPADA`, `MANTENIMIENTO`; **no** existe estado de baja.
- `Vehiculo` guarda los datos del propietario **denormalizados** en tres columnas
  (`propietario_documento`, `propietario_nombre`, `propietario_telefono`, `Vehiculo.java:56-66`). No hay
  ninguna entidad `Cliente`.
- `@PreAuthorize` solo aparece en dos lugares de toda la aplicación: `CeldaController.cambiarEstado`
  (ADMINISTRADOR) y la clase `UsuarioController` (ADMINISTRADOR). El resto de endpoints está solo
  autenticado (`SecurityConfig.java:47`, `anyRequest().authenticated()`).
- Login **sí** rechaza cuentas inactivas (`AuthService.java:58`); **pero** `validarToken`
  (`AuthService.java:94-110`) no consulta `activo`, así que un token ya emitido sigue siendo aceptado hasta
  su expiración, configurada en **8 horas** (`application.yml:29`, `expiration-ms: 28800000`).
- `UsuarioController.java:39` crea personal con una contraseña fija compartida.
- `UsuarioService.actualizar` (`:66-70`) verifica duplicado de **correo** pero **no** de **documento**, que
  sí tiene restricción única en base (`uk_usuario_documento`); `GlobalExceptionHandler` no tiene handler de
  `DataIntegrityViolationException` (solo 7 handlers, `GlobalExceptionHandler.java:22-95`), así que el
  duplicado de documento termina en error genérico.
- `CeldaService.asignarCeldaDisponible` (`:31-42`) hace lectura-then-escritura sin bloqueo ni constraint:
  dos entradas concurrentes pueden tomar la misma celda.
- `CeldaService.liberarCelda` (`:48-54`) fija `DISPONIBLE` **sin importar el estado anterior**: una celda en
  `MANTENIMIENTO` queda disponible si se le dirige una salida.
- `CeldaService.cambiarEstado` (`:86-90`) acepta cualquier estado sin validación: un administrador puede
  poner una celda en `OCUPADA` manualmente sin ningún registro detrás.
- Autenticación: JWT stateless (jjwt), BCrypt, filtro propio; el bloqueo por intentos fallidos vive en un
  `Map` en memoria de `AuthService` (`registrarIntentoFallido`, `estaBloqueado`), no persiste y es
  por-instancia.

---

## 2. Glosario

| Término | Definición operativa en este proyecto |
|---|---|
| **Cliente** | Persona natural o jurídica whose vehicle parks in the facility. Identificada por su **documento**. Es dato de negocio: **no** tiene contraseña, **no** tiene rol, **no** inicia sesión, **no** recibe token. |
| **Cuenta del personal** | Registro de la tabla `usuario` con credenciales y rol (`ADMINISTRADOR` u `OPERADOR`). Es la única entidad que se autentica y autoriza. En adelante «cuenta» = cuenta del personal; «cliente» nunca es una cuenta. |
| **Vehículo** | Automotor identificado por **placa única** con formato `ABC123` o `AB123C` (`Vehiculo.java:30`). Pertenece a un cliente (relación N:1 propuesta) y entra/sale del parqueadero. |
| **Celda** | Espacio físico de estacionamiento, identificado por `numero` único (≤10 caracteres) y tipificado por `piso` y `tipoVehiculo`. Tiene dos dimensiones separadas: **estado de ocupación** (`DISPONIBLE`/`OCUPADA`/`MANTENIMIENTO`) y **vigencia administrativa** (activa o dada de baja lógica). |
| **Ocupación** | Situación de una celda mientras existe un `registro` con `estado = ACTIVO` asociado a ella. La ocupación la produce **una entrada válida**, no una edición. Termina con la salida (o cancelación) de ese registro. |

Distinguir las dos dimensiones de la celda es la decisión de modelado central de la Iteración 2: hoy solo
existe `estado`, y usarlo para la baja mezclaría «está ocupada» con «ya no existe para el negocio».

---

## 3. Matriz de permisos

Leyenda: ✅ permitido · ❌ rechazado (`403` por rol, `401` sin credencial válida) · el anónimo siempre
`401`, salvo `POST /auth/login`, los recursos estáticos y swagger/actuator que `SecurityConfig` declara
públicos.

| Operación | ADMINISTRADOR | OPERADOR | Anónimo | Cuenta desactivada con token previo |
|---|---|---|---|---|
| Iniciar sesión | ✅ (si está activa) | ✅ (si está activa) | ✅ endpoint público | ❌ `login` ya lo rechaza (`AuthService.java:58`) |
| Ver dashboard y reportes | ✅ | ✅ (hoy; ver nota) | ❌ `401` | ❌ `401` desde B1 |
| Consultar celdas | ✅ | ✅ | ❌ `401` | ❌ `401` desde B1 |
| Cambiar estado de celda (mantenimiento) | ✅ (solo `DISPONIBLE ↔ MANTENIMIENTO` desde B3; `OCUPADA` se rechaza con `409`) | ❌ `403` | ❌ `401` | ❌ `401` desde B1 |
| Crear / editar / desactivar / reactivar celda | ✅ **existe desde B3** (`@PreAuthorize`) | ❌ `403` — verificado en `case11` | ❌ `401` | ❌ `401` |
| Registrar entrada | ✅ | ✅ | ❌ `401` | ❌ `401` desde B1 |
| Registrar salida / cancelar registro | ✅ | ✅ (salida) · cancelación por rol, ver D5 | ❌ `401` | ❌ `401` desde B1 |
| Registrar y consultar novedades | ✅ | ✅ | ❌ `401` | ❌ `401` desde B1 |
| Consultar comprobantes / PDF | ✅ | ✅ | ❌ `401` | ❌ `401` desde B1 |
| Administrar cuentas del personal (listar, crear, editar, desactivar) | ✅ (clase ya restringida) | ❌ `403` (verificado con JWT real en B1) | ❌ `401` | ❌ `401` desde B1 |
| **Gestionar clientes** (listar, buscar, crear, editar) | ✅ | ✅ — **vigente desde B2**, verificado con JWT real | ❌ `401` | ❌ `401` |
| **Desactivar o reactivar un cliente** | ✅ | ❌ `403` — **vigente desde B2** (D4 resuelta) | ❌ `401` | ❌ `401` |
| **Vincular un cliente activo a un vehículo sin titular** | ✅ | ✅ — **vigente desde B2** | ❌ `401` | ❌ `401` |
| **Cambiar el titular de un vehículo ya vinculado** | ✅ y solo sin ingreso activo | ❌ `403` — **vigente desde B2** | ❌ `401` | ❌ `401` |

**La brecha estructural que abrió esta sección quedó cerrada en B1.** `AuthService.validarToken` ahora
consulta `activo` en cada petición protegida, así que un JWT emitido antes de desactivar la cuenta devuelve
`401` con el formato `ErrorResponse` — es la columna derecha completa de esta matriz. **Límite conservado
de `06_B1_CUENTAS_PERSONAL.md` §6:** no es revocación permanente; reactivar la cuenta revive el token hasta
su expiración natural (8 h).

**Nota sobre granularidad.** Hoy dashboard, reportes, novedades y comprobantes son accesibles para ambos
roles. La matriz propuesta los mantiene iguales salvo lo que el equipo cambie explícitamente; endurecerlos
sin decisión de negocio rompería al operador.

---

## 4. Requisitos funcionales

Identificadores estables: `RF-<dominio>-<nn>`. No reutilizar ni renumerar: son las anclas de trazabilidad
con historias, pruebas y diagramas.

### Autenticación y cuentas del personal (B)

| ID | Requisito | Estado actual | Criterio de verificación |
|---|---|---|---|
| RF-ACC-01 | Una cuenta con `activo = false` **no** puede usar ninguna operación protegida, ni siquiera con un token emitido antes de la desactivación. | ✅ **cumplido en B1** (`AuthService.validarToken` consulta `activo` en toda petición protegida y la entrada anónima responde `401` con el formato `ErrorResponse`). Límite: esto **no** es revocación permanente; reactivar la cuenta habilita de nuevo el JWT previo. Ver `06_B1_CUENTAS_PERSONAL.md` §6. | Prueba de integración con JWT real (sin `@WithMockUser`): token emitido en `activo=true`, cuenta desactivada por `PATCH /api/v1/usuarios/{id}/activo`, la petición posterior devuelve `401` y no deja efectos; al reactivar, el mismo token vuelve a funcionar. |
| RF-ACC-02 | Solo `ADMINISTRADOR` administra cuentas del personal. | ✅ ya cumple (clase `UsuarioController`) y **B1 lo verificó con JWT real** en las cuatro operaciones, incluido el `PATCH` de estado. | `403` al llamar como `OPERADOR` con su token, y comprobación de que no se creó ninguna cuenta como efecto colateral. |
| RF-ACC-03 | Al crear una cuenta se exige **contraseña inicial obligatoria**, propia de esa cuenta; no existe contraseña fija compartida. | ✅ **cumplido en B1**: la contraseña fija compartida desapareció del controlador; `UsuarioCrearDTO` exige `@NotBlank`, mínimo 15 caracteres y máximo 72 **bytes UTF-8** medidos en la librería. | `400` si falta, es corta o excede el límite de BCrypt (sin truncamiento); la cuenta creada autentica con la contraseña entregada y `passwordEncoder.matches` falla con cualquier otra. |
| RF-ACC-04 | La contraseña inicial **no** queda expuesta en el repositorio, en logs ni en respuestas de API. | ✅ **cumplido en B1** para la ruta de cuentas: DTO de salida separado sin `clave`/`claveHash`, `@ToString.Exclude` en la entrada, `HttpMessageNotReadableException` sin reflejar el payload. **Pendiente fuera de B1:** `LoginRequest.toString()` aún expone la contraseña de login. | Búsqueda de las cadenas literalmente usadas en las pruebas sobre los reportes de Surefire y el registro de la corrida (0 coincidencias); el `POST` no devuelve ni la clave ni el hash. |
| RF-ACC-05 | `correo` y `documento` se validan como únicos **en creación y en actualización**, devolviendo `409`. | ✅ **cumplido en B0** (`UsuarioService.actualizar` ahora consulta `findByDocumento` y rechaza el documento ajeno; `GlobalExceptionHandler` responde `409` también al conflicto de base). Limitación: la rama de restricción conocida no se dispara en H2, ver `05_B0_...md` §6. | Intento de actualizar una cuenta con el documento de otra: `409`, y la fila queda intacta. |
| RF-ACC-06 | El sistema **no** puede quedar sin ningún `ADMINISTRADOR` activo: se rechaza desactivar o degradar de rol a la última cuenta administrativa activa. | ✅ **cumplido en B1** para el caso secuencial: `UsuarioService.verificarCoberturaDeAdministradores` corre antes de cualquier mutación y es la única puerta usada por `PATCH`, `PUT` y `DELETE`. **No está garantizado frente a operaciones simultáneas**: se añadió `@Lock(PESSIMISTIC_WRITE)` sin cambiar el esquema, pero H2 no demuestra el `SELECT ... FOR UPDATE` en PostgreSQL. Ver `06_B1_CUENTAS_PERSONAL.md` §5. | Con 1 solo ADMIN activo: desactivarlo → `409` y el `PUT`/`DELETE` tampoco lo permiten. Con 2: el primero puede salir. La concurrencia real queda como riesgo pendiente, no como garantía. |
| RF-ACC-07 | `OPERADOR` no puede crearse ni editarse a sí mismo privilegios; el rol solo lo fija un administrador. | ✅ por RF-ACC-02 | Verificado en la misma matriz. |
| RF-ACC-08 | Una cuenta desactivada puede **reactivarse** por un administrador. | ✅ **cumplido en B1**: endpoint dedicado `PATCH /api/v1/usuarios/{id}/activo` con cuerpo `{"activo": true\|false}`; un estado ausente o nulo se rechaza con `400`. | Desactivar y reactivar con el mismo token de administrador devuelve `200` en ambos pasos y el JWT revivido vuelve a ser aceptado. |

### Clientes (A)

| ID | Requisito | Estado actual | Criterio de verificación |
|---|---|---|---|
| RF-CLI-01 | Existe una entidad `cliente` separada de `usuario`, con su propia tabla y API. | 🟡 **implementado en B2**. `Cliente` + `TipoDocumento` + `ClienteRepository` + `ClienteService` + `ClienteController` sobre `/api/v1/clientes`, y la tabla está escrita en `V2__clientes_y_vinculo_vehiculos.sql`. **No verificado en PostgreSQL**: el perfil `test` tiene Flyway apagado, así que V2 nunca fue interpretado por el motor objetivo y la aplicación no se arrancó. Ver `07_B2_CLIENTES_POSTGRESQL.md` §1, §3.2, §9.2. | Prueba de integración sobre H2 (26 casos en `ClientesVehiculosIntegrationTest`). Queda pendiente el `SELECT` y el arranque con la migración aplicada sobre PostgreSQL. |
| RF-CLI-02 | El cliente se identifica por **documento único**; no admite campos de credenciales ni rol. | 🟡 **implementado en B2**, con una precisión: la identidad es la **pareja** `tipo_documento + documento` (dos personas con el mismo número y distinto tipo son clientes distintos). Unicidad en servicio **y** en `uk_cliente_tipo_documento`, traducida a `409` por nombre de restricción. `ClienteCrearDTO`/`ClienteDTO` no tienen `clave` ni `tipo` de autoridad. | `case03`, `case04`, `case07` y `ClienteServiceTest` (409 sin escribir ni auditar, identidad ajena rechazada). La rama de PostgreSQL sobre la restricción real sigue pendiente. |
| RF-CLI-03 | Un cliente **no puede autenticarse**: no se le emite token ni se le asigna autoridad. | 🟡 **implementado en B2** por construcción: `Cliente` no tiene columna de credencial ni rol, no hay `UserDetails` para clientes y la ruta de login solo consulta `usuario`. No existe un «intento de login con el correo de un cliente» porque el cliente **no tiene correo** (decisión D1, ver §15). | Prueba de unidad: el login no puede resolver un cliente; verificación cruzada de que `cliente` no aporta autoridad en `SecurityConfig`. |
| RF-CLI-04 | CRUD de clientes: listar (con filtros), crear, actualizar, consultar por documento/id. | 🟡 **implementado en B2**: `GET` paginado con `criterio` y `activo`, `GET /{id}`, `GET /{id}/vehiculos`, `POST`, `PUT /{id}`, `PATCH /{id}/activo`. Búsqueda por contención de documento o nombre, orden fijo `id ASC`, tope de página 100. | `case01`, `case02`, `case05`, `case06`, `case08`, `case11`, `case17` (contratos de respuesta) + pantalla `#clientes-view`. |
| RF-CLI-05 | Activar/desactivar cliente (baja lógica). Un cliente inactivo **no** se ofrece como opción nueva, pero sus vehículos y registros históricos siguen legibles. | 🟡 **implementado en B2**: `activo` con `DEFAULT TRUE`, `PATCH` solo `ADMINISTRADOR`, idempotencia sin escritura ni auditoría; desactivar conserva vehículos e historial, permite la salida y rechaza vinculaciones e ingresos nuevos (`409`). | `case10`, `case12`, `case15`, `case16` y `desactivar_esBajaLogicaConservaLosDatosYAuditaSinDocumentosNiTelefonos`. |
| RF-CLI-06 | Un vehículo se asocia a un cliente; la relación es N:1 (un cliente, varios vehículos). | 🟡 **implementado en B2**: `vehiculo.cliente_id` nullable con `fk_vehiculo_cliente`, relación Many-To-One con `cliente` como fuente actual vía `VehiculoService.toDTO` (única proyección). Vinculación en `PUT /api/v1/vehiculos/{placa}/cliente`. | `case11`, `case13`, `case14`, `case17`. El `ON DELETE RESTRICT` se comprobó en H2 (`[23503-224]`), no en PostgreSQL. |
| RF-CLI-07 | Compatibilidad con la Iteración 1: los vehículos ya registrados **no** se pierden ni quedan huérfanos; la entrada/salida sigue funcionando para vehículos sin cliente vinculado. | 🟡 **cumplido y probado sobre H2 en B2**. `cliente_id` nulo sigue operando: `clienteId` responde `null`, los campos `propietario_*` responden el texto histórico, y la interfaz muestra «Vinculación pendiente (ficha de la Iteración 1)». **No se generaron clientes desde `propietario_*`** (habría creado titulares falsos). La regresión completa quedó en **259 pruebas sin fallos** (no 139: la cifra creció con B0, B1 y B2). | `case14_vehiculoAntiguoSinClienteSigueOperandoYMuestraVinculacionPendiente` + `mvn -B -o clean verify` del 2026-10-31 12:12:26. |
| RF-CLI-08 | Al dar de alta un vehículo en la entrada, el personal puede crear o elegir el cliente del propietario en el mismo flujo, sin romper el contrato actual de `RegistroEntradaDTO`. | ⚠️ **sin implementar** — fuera del alcance autorizado de B2. `RegistroEntradaDTO` conserva `propietario*` como campos sueltos y la vinculación se hace como operación aparte (`PUT /vehiculos/{placa}/cliente`). Queda para la fase de entrada/salida. | Ver D2 (§15). |
| RF-CLI-09 | No se borran físicamente clientes con registros o vehículos asociados. | 🟡 **implementado en B2**: no existe endpoint de borrado de clientes; la vía admitida es la desactivación; y `fk_vehiculo_cliente` es `ON DELETE RESTRICT`, así que la base rechaza el borrado de un cliente con vehículos. | `case17_laBaseRechazaBorrarUnClienteConVehiculosVinculados` (H2). Comprobación equivalente en PostgreSQL pendiente. |

### Celdas (C)

| ID | Requisito | Estado actual | Criterio de verificación |
|---|---|---|---|
| RF-CEL-01 | `numero` de celda es único. | ✅ `uk_celda_numero` + validación en `V1`; **B3** añade `idx_celda_numero_lower` (solo rendimiento) y normaliza con `CeldaService.normalizarCodigo` | `409` al duplicar — `case02` (H2) |
| RF-CEL-02 | Alta y edición de celda (piso, tipo, observaciones) reservadas a `ADMINISTRADOR`. | 🟡 **implementado en B3**: `POST /celdas` y `PUT /celdas/{id}` con `@PreAuthorize('ADMINISTRADOR')` | `403` para `OPERADOR`, `201`/`200` para `ADMIN` — `case01`, `case04`, `case11` (H2) |
| RF-CEL-03 | **Baja lógica**: la celda deja de estar disponible para el negocio sin desaparecer de la base ni del histórico. | 🟡 **implementado en B3**: columna `celda.activo` (V3, **sin ejecutar**) sin cuarto valor en `EstadoCelda`; `PATCH /celdas/{id}/activo` | La fila se conserva; `activo = false` — `case06` (H2) |
| RF-CEL-04 | No se puede desactivar una celda con **ocupación activa** (`estado = OCUPADA` o con un `registro ACTIVO`). | 🟡 **implementado en B3**: `estaOcupada` mira las dos señales y rechaza | `409` con mensaje específico; la celda sigue `OCUPADA` — `case07` (H2) |
| RF-CEL-05 | Sí se puede desactivar una celda con **historial cerrado** (solo registros `FINALIZADO`/cancelados). | 🟡 **implementado en B3**: desactivar solo escribe `activo`, nunca `estado` | `200` y el histórico sigue consultable — `case06` (H2) |
| RF-CEL-06 | Reactivación de celda: vuelve al pool, **nunca** directamente a `OCUPADA`; queda en `DISPONIBLE` o en `MANTENIMIENTO` según decisión del administrador. | 🟡 **implementado en B3**: reactivar no quita un mantenimiento pendiente | `estado ≠ OCUPADA` y sin registro `ACTIVO` — `case08` (H2) |
| RF-CEL-07 | El algoritmo de asignación **no** considera celdas inactivas ni en `MANTENIMIENTO`. | 🟡 **implementado en B3**: `listarAsignables*` filtra `activo = true`, `DISPONIBLE` y sin registro `ACTIVO`; el servicio revalida tras el bloqueo | `case14`, `case14b` (H2) |
| RF-CEL-08 | `OCUPADA` solo se produce por una **entrada válida** que crea un `registro ACTIVO`. | 🟡 **implementado en B3**: `cambiarEstado` rechaza el destino `OCUPADA` antes de leer la base | `case10` (rechazo con `409`, no `400`/`422`) y `case13` (H2) |
| RF-CEL-09 | La salida libera **únicamente** la celda del registro que cierra, y solo si estaba `OCUPADA`; no toca otras celdas ni reactiva una de mantenimiento. | 🟡 **implementado en B3**: `liberarCelda(celdaId, registroPropioId)`; la inconsistencia se rechaza, no se repara | `case15`, `case17`, `case18b` (H2) |
| RF-CEL-10 | **No hay doble ocupación concurrente**: dos entradas simultáneas nunca comparten celda; a lo sumo una gana y la otra reintenta o falla con mensaje claro. | 🟡 **mecanismo Java implementado en B3**: `@Lock(PESSIMISTIC_WRITE)` con revalidación, orden de bloqueo vehículo → celda y reintento acotado a 5 candidatos | `dosIngresosConcurrentesPorUnaUnicaCeldaDejanUnSoloRegistroActivo` (H2). **En base de datos sigue sin probar**: H2 no tiene índices únicos parciales (§11.4) |
| RF-CEL-11 | Toda regla de ocupación es coherente entre API y base de datos: la restricción existe también a nivel de BD, no solo en Java. | 🟡 **escrita en V3, no ejecutada**: `uk_registro_celda_activo`, `uk_registro_vehiculo_activo` y `chk_celda_inactiva_no_ocupada` | Ver RF-NF-05 y la nota de H2/PostgreSQL en `08` §12.3 |
| RF-CEL-12 | `OPERADOR` puede consultar celdas y su disponibilidad. | ✅ | `case12`, `case19b` y `CeldaControllerTest` — verdes tras B3 |

### Transversales

| ID | Requisito |
|---|---|
| RF-TRX-01 | Pico y placa se conserva sin cambios de regla; solo se vuelve determinista mediante el `Clock` ya inyectado. |
| RF-TRX-02 | La auditoría de acciones de personal (quién creó/desactivó/reactivó) queda registrada en `auditoria_log`. |
| RF-TRX-03 | Todo error de negocio responde con el formato ya usado por `GlobalExceptionHandler` (código + mensaje legible), sin StackTrace expuesto. **Parcial desde B0:** los conflictos de integridad ya entran en ese formato; los demás casos siguen como estaban. |

---

## 5. Requisitos no funcionales

Una métrica de pruebas **no es un requisito**: es la evidencia de que un requisito se cumplió en un momento
dado. Cada fila separa las tres cosas.

* **Requisito / objetivo verificable** → lo que hay que cumplir y cómo se comprueba.
* **Evidencia observada** → dato real ya medido en este repo, o `sin medir`.
* **Fecha y alcance de la medición** → corrida concreta; una cifra sin fecha ni alcance no sirve.

| ID | Requisito u objetivo verificable | Evidencia observada | Fecha y alcance de la medición |
|---|---|---|---|
| RF-NF-01 | Determinismo temporal: la suite no depende del día de ejecución en la regla de pico y placa (inversión del `Clock` ya en `RegistroControllerIntegrationTest`). | `mvn -B clean verify` en viernes: 220/220 sin fallos | 2026-10-30 22:21→22:23, perfil `test` sobre H2, cierre de B1. Corridas anteriores de referencia: 139/139 (15:47 y 16:21, fases `01`/`03`) y 161/161 (21:23, cierre de B0). |
| RF-NF-02 | La suite completa pasa sin fallos, sin errores y sin pruebas omitidas, y sin `-Dmaven.test.failure.ignore`. | **314 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS; 1:32 min; exit 0.** Frontend: `node --test src/test/js/app.test.js` → **30/30** (243 ms), repetido al cerrar la bitácora `08` | 2026-10-31, perfil `test` sobre H2, cierre de **B3**. Referencias anteriores: 220/220 (B1, 2026-10-30 22:21), 259 Java + 14 Node (B2), 139/139 (B0). No es una cota de rendimiento, es el resultado de esas corridas. |
| RF-NF-03 | La cobertura se reporta por separado y se interpreta como cobertura de prueba, **no** como calidad ni como ausencia de errores. | Líneas 977/1069 (91,4 %) · instrucciones 5836/10268 (56,8 %) · ramas 187/1016 (18,4 %) · métodos 602/828 (72,7 %) · clases 77/77 con al menos una línea cubierta | Reporte JaCoCo 0.8.11 de la corrida 22:21 (B1). El porcentaje de ramas sigue dominado por código generado por Lombok (medido en `01`: 709 de 746 ramas perdidas). Sin umbral exigido por `pom.xml`. |
| RF-NF-04 | Toda clase nueva de servicio y controller de la Iteración 2 tiene pruebas propias; regla de baja y asignación con al menos un caso por rama de decisión. | **cumplido en cuanto a existencia de pruebas**: `UsuarioService`/`UsuarioController` (B1), `ClienteService`/`ClienteController`/`VehiculoService` (B2) y **`CeldaService` (28 unitarias), `CeldaController` (10) y `CeldasIntegridadIntegrationTest` (33)** en B3. La cobertura numérica sigue sin medirse desde B1. | 2026-10-30 22:21 es la última corrida de JaCoCo (B1). Tras B3 queda **sin volver a medir**. |
| RF-NF-05 | La unicidad de la ocupación activa existe también como restricción de PostgreSQL, no solo en Java. | **escrito en `V3` (B3), sin medir**: `uk_registro_celda_activo`, `uk_registro_vehiculo_activo` y `chk_celda_inactiva_no_ocupada`. Un índice parcial (`... WHERE estado = 'ACTIVO'`) funciona en PostgreSQL pero **H2 no lo soporta**, y el perfil `test` **desactiva Flyway**, así que ni `V2` ni `V3` se interpretaron. | Sin verificar. Requiere entorno PostgreSQL real (D9) + prueba explícita contra él. Ver `08` §12.3. |
| RF-NF-06 | Las migraciones de la Iteración 2 solo añaden; nunca reescriben `V1`. Con `ddl-auto: validate`, cualquier campo nuevo de entidad sin columna rompe el arranque. | **cumplido en cuanto a redacción**: `V1` sigue byte a byte sin cambios; `V2` (B2) y `V3` (B3) son estrictamente aditivas y conservan los nombres de restricción existentes. **`validate` nunca corrió contra PostgreSQL**: la app no se arrancó. | Verificable solo al aplicar las migraciones y arrancar contra la base real (pendiente de autorización). |
| RF-NF-07 | La selección de celda con bloqueo no degrada perceptiblemente la entrada. | **sin medir** (objetivo propuesto, sin cifra acordada) | No hay entorno de ejecución contra PostgreSQL real; no se mide aquí. |
| RF-NF-08 | Sin secretos reales versionados: las credenciales se resuelven por variables de entorno. | `application.yml`, `application-dev.yml` y `docker-compose.yml` usan variables; no hay `.env`, claves privadas ni dumps versionados. `application-test.yml` y `JwtUtilTest` contienen solo valores de prueba. | Revisión de la fase `03` (2026-10-30). No es una métrica de la suite; es una revisión de contenido del repositorio. |
| RF-NF-09 | La verificación continua no publica imágenes ni despliega. | `ci.yml` reescrito a solo verificación (Maven + Node, `contents: read`, sin GHCR). **Medición parcial:** la sintaxis se revisó con herramientas locales; no se ejecutó en GitHub Actions. | 2026-10-30 (fase `03`). El resultado remoto queda pendiente del siguiente push. |
| RF-NF-10 | Los datos personales de clientes no salen del entorno en volcados, exports ni documentos de clase. | **sin medir** (objetivo propuesto). La tabla `cliente` aún no existe. | Aplica desde B2. Falta la política de retención (D8). |
| RF-NF-11 | Sin dependencias nuevas en `pom.xml`. | Sin cambios en `pom.xml` (verificado con `git diff`) | 2026-10-30, corrida 21:23. |
| RF-NF-12 | Se conserva el stack: Java 17 / Spring Boot 3.2.5 / PostgreSQL / frontend vanilla JS. | Decisión tomada del equipo, no una medición | Iteración 2 completa. |

> Ninguna cifra de esta tabla procede del README ni de `target/` de una corrida anterior: son de la corrida
> registrada en la columna de fecha. Las cifras de la línea base están en `00_LINEA_BASE.md` y `01_...md`.

---

## 6. Historias de usuario técnicas de referencia

**Para revisión y coordinación con Isabella.** Están escritas desde el lado técnico y **no** sustituyen sus
historias de negocio ni sus casos de uso; el número aquí es interno de esta bitácora (HU-I2-xx) y debe
mapearse a los identificadores formales cuando ella los asigne.

#### HU-I2-01 — Bloquear el acceso de una cuenta desactivada
*Como administrador, quiero que al desactivar una cuenta el acceso se corte de inmediato, para que una
baja de personal no deje puertas abiertas.*
- **Criterios:** tras desactivar, cualquier petición con el token que ya tenía devuelve `401`; el login
  también se rechaza; la desactivación queda en `auditoria_log`; reactivar restaura el acceso (con login
  nuevo).
- **Cubre:** RF-ACC-01, RF-ACC-08. **Depende de:** nada. **Riesgo:** tocar el filtro de autenticación afecta
  a los 139 tests actuales → hay que dejarlos verdes.
- **Estado tras B1: NO cerrada.** Cumplen el `401` con el token ya emitido (prueba con JWT real) y el rechazo
  de login inactivo (preexistente). **No cumple el criterio de `auditoria_log`**: B1 no añadió escritura de
  auditoría en la desactivación, quedó pendiente. Y el criterio dice «reactivar restaura el acceso **con login
  nuevo**», mientras que lo verificado es que el mismo JWT anterior vuelve a ser aceptado al reactivar
  (`06_B1_CUENTAS_PERSONAL.md` §6): hay que alinear el criterio o endurecer el mecanismo, con decisión de
  negocio. Falta además la pantalla de administración de cuentas.

#### HU-I2-02 — Crear personal con contraseña inicial propia
*Como administrador, quiero definir la contraseña inicial de una cuenta del personal, para no compartir una
clave única entre todos.*
- **Criterios:** `POST /api/v1/usuarios` sin `claveInicial` → `400`; con clave bajo la política acordada →
  `400` con mensaje que indique el mínimo; la contraseña **nunca** aparece en la respuesta ni en el log; la
  cuenta autentica con esa clave; no queda ninguna contraseña literal en el código.
- **Cubre:** RF-ACC-03, RF-ACC-04, RF-ACC-05.
- **Estado tras B1: NO cerrada.** Cumplen el `400` por clave ausente/corta/excedida, el `400` con mensaje de
  mínimo, la autenticación con la clave entregada, la ausencia de clave y de hash en la respuesta y que no
  queda ningún literal en el código. Dos diferencias a reconciliar con Isabella: el campo se llama **`clave`**,
  no `claveInicial` como escribe el criterio; y el mínimo acordado fue **15 caracteres** (más el tope de 72
  bytes UTF-8), no el «mínimo 8» que proponía D10. Falta la pantalla de alta.

#### HU-I2-03 — Proteger el último administrador
*Como equipo, necesitamos que el sistema no pueda quedar sin administrador activo.*
- **Criterios:** desactivar o degradar al último `ADMINISTRADOR` activo → `409` con motivo claro; con dos
  administradores activos la operación sí procede; la regla cuenta **activos**, no totales.
- **Cubre:** RF-ACC-06.
- **Estado tras B1: NO cerrada.** Cumplen el `409` al desactivar o degradar al último administrador activo, el
  motivo claro, el `409` también por `PUT` y por `DELETE`, y que la regla cuente **activos**. Queda la interfaz
  y queda la garantía para dos operaciones simultáneas: el bloqueo pesimista se escribió pero solo se ejercitó
  sobre H2, así que en PostgreSQL sigue sin verificar (`06_B1_CUENTAS_PERSONAL.md` §5).

#### HU-I2-04 — Registrar clientes y asociarles vehículos
*Como operador, quiero registrar el cliente dueño de un vehículo, para que el histórico de placas apunte a
una persona y no a texto suelto.*
- **Criterios:** alta con documento único, sin campos de credenciales; documento repetido → `409`; un cliente
  puede tener varios vehículos; un vehículo existente de la Iteración 1 puede vincularse sin perder su
  histórico; el cliente **no** puede iniciar sesión.
- **Cubre:** RF-CLI-01, RF-CLI-02, RF-CLI-03, RF-CLI-06, RF-CLI-07. **No cubre con criterios propios:**
  RF-CLI-04 (listar con filtros y consultar por documento), RF-CLI-05 (baja lógica del cliente), RF-CLI-08
  (flujo de entrada), RF-CLI-09 (no borrado físico) → falta historia o falta criterio explícito.

#### HU-I2-05 — Dar de baja lógica una celda sin romper el histórico
*Como administrador, quiero desactivar una celda que ya no presta el servicio (obras, local alquilado) sin
perder sus ocupaciones pasadas.*
- **Criterios:** desactivar celda con registro activo → `409`; desactivar celda con solo registros cerrados →
  `200`; la celda desactivada no se asigna en entradas nuevas; sus registros y comprobantes históricos se
  siguen consultando; reactivarla la deja `DISPONIBLE` o `MANTENIMIENTO`, nunca `OCUPADA`.
- **Cubre:** RF-CEL-03, RF-CEL-04, RF-CEL-05, RF-CEL-06, RF-CEL-07. **No cubre:** RF-CEL-02 (alta y edición
  reservadas a `ADMINISTRADOR`) — necesita su propia historia o un criterio de permisos explícito.

#### HU-I2-06 — Ocupación y liberación coherentes de celdas
*Como equipo, necesitamos que el estado de una celda refleje la realidad y que dos ingresos simultáneos no
la ocupen dos veces.*
- **Criterios:** el cambio manual a `OCUPADA` se rechaza; una entrada válida produce `OCUPADA` + registro
  `ACTIVO`; la salida libera solo esa celda y no altera una de `MANTENIMIENTO`; dos hilos concurrentes sobre
  la misma celda: una entra y la otra recibe error de negocio, y no quedan dos registros `ACTIVO` en la misma
  celda.
- **Cubre:** RF-CEL-08, RF-CEL-09, RF-CEL-10 y, **solo en parte**, RF-CEL-11: el criterio comprueba la
  conducta desde la API, no la restricción de base de datos que exige RF-NF-05. **No cubre:** RF-CEL-01,
  RF-CEL-12 (no tienen historia).
- **Depende de:** D1 (mecanismo de bloqueo) y D9 (PostgreSQL real para RF-CEL-11).

### 6.1 Trazabilidad requisito → historia

Conteo sobre los 32 requisitos de §4. Una historia que «menciona» un requisito no lo cubre: se cuenta como
cobertura **solo si existe un criterio de aceptación verificable**. Con ese criterio: **19 requisitos tienen
cobertura explícita completa, 1 (RF-CEL-11) tiene cobertura parcial y 12 no tienen ninguna** — 19 + 1 + 12 = 32.

| Requisito sin cobertura explícita | Por qué queda suelto | Qué haría falta |
|---|---|---|
| RF-ACC-02 | **Criterio verificado en B1**: `CuentasPersonalIntegrationTest.operadorRecibe403EnTodaLaGestionDeCuentas` usa el JWT real de un `OPERADOR` contra las cuatro operaciones de cuentas (no `@WithMockUser`). Lo que sigue suelto es la **historia**: ninguna historia de Isabella describe la administración de cuentas, así que no hay trazabilidad funcional que lo reclame. | Redactar la historia de permisos de cuentas para que el criterio verificado tenga historia donde anclarse. |
| RF-ACC-07 | Se apoya en RF-ACC-02, cuyo criterio **ya está verificado en B1** con JWT real: el `OPERADOR` recibe `403` también en el `PATCH` de estado y en el `PUT` de edición de rol. Sigue sin historia propia. | Misma historia de permisos de RF-ACC-02. |
| RF-CLI-04 | HU-I2-04 no tiene criterios de consulta con filtros ni por documento. El **criterio ya existe y está verificado sobre H2 en B2** (`case02`, `case05`, `case08`, `case17` + la pantalla `#clientes-view`); lo suelto sigue siendo la historia. | Criterios de listado/filtrado redactados por Isabella, anclados a los endpoints reales de `07_B2_CLIENTES_POSTGRESQL.md` §4. |
| RF-CLI-05 | Baja lógica del cliente sin historia (solo se habló de la de celdas). El **comportamiento ya está implementado y probado sobre H2 en B2** (`case10`, `case12`, `case15`, `case16`), incluido el efecto sobre ingresos y salidas. | Historia o criterio dentro de HU-I2-04, con la postcondición de que desactivar no borra historial. |
| RF-CLI-08 | Flujo de entrada/salida con cliente: sin criterios escritos **y sin implementar** — quedó fuera del alcance de B2. Hoy la vinculación es una operación aparte (`PUT /vehiculos/{placa}/cliente`), no un paso del formulario de entrada. | Ampliar HU-I2-04 o crear una historia de entrada; decidir D2 antes de tocar `RegistroEntradaDTO`. |
| RF-CLI-09 | Prohibición de borrado físico de clientes con historial: el **mecanismo ya existe y funciona** (no hay endpoint de borrado y `fk_vehiculo_cliente` es `ON DELETE RESTRICT`, comprobado en H2 con `[23503-224]`). Falta la historia y la comprobación en PostgreSQL. | Criterio de rechazo + prueba de persistencia sobre el motor objetivo. |
| RF-CEL-01 | Unicidad de `numero` de celda: ya existe en `V1`, pero ninguna historia lo comprueba. | Prueba de `409` al duplicar numero. |
| RF-CEL-02 | Alta/edición de celda reservada a `ADMINISTRADOR`. | Historia de administración de celdas. |
| RF-CEL-11 | Cobertura **parcial**: la historia prueba la API, no la restricción de BD. | Prueba contra PostgreSQL (RF-NF-05). |
| RF-CEL-12 | Consulta de disponibilidad por `OPERADOR`: hoy pasa solo como efecto del controller, sin prueba de la Iteración 2. | Prueba de permisos de lectura. |
| RF-TRX-01 | Conservar pico y placa sin cambios: no tiene historia propia. | Regresión explícita (hoy la cubren las pruebas de `01`, no una historia). |
| RF-TRX-02 | Auditoría de acciones de personal en `auditoria_log`. | Criterios por acción (crear/desactivar/reactivar). |
| RF-TRX-03 | Formato uniforme de error. | **Avanzado en B0** para conflictos de integridad; sigue sin historia para el resto. |

Las tres cifras de la fila final no son una queja de forma: definen qué hay que escribir antes de poder decir
«todos los requisitos de la Iteración 2 tienen prueba». Los requisitos de esta tabla siguen siendo propuesta
del equipo; la numeración formal de historias y casos de uso la define Isabella con este insumo.

---

## 7. Reglas de negocio y casos de error

### Reglas de negocio

| ID | Regla | Origen |
|---|---|---|
| RN-01 | Pico y placa se evalúa contra el `Clock` inyectado; la regla vigente no cambia (dígitos por día, Bogotá, día completo). | código actual, `PicoYPlacaService.java:38-60` |
| RN-02 | Un vehículo solo puede tener un registro `ACTIVO` a la vez. | ya implementado (`RegistroService`) |
| RN-03 | `estado` de celda y `activo` de celda son dimensiones independientes. | **vigente desde B3** (`activo` en la entidad y en `V3`; `EstadoCelda` intacto; invariante `chk_celda_inactiva_no_ocupada` escrita pero sin ejecutar) |
| RN-04 | Una celda con ocupación activa no puede desactivarse ni pasar a mantenimiento. | **vigente desde B3** — `case07`, `case09` (H2) |
| RN-05 | La asignación considera únicamente `activo = true` **y** `estado = DISPONIBLE` **y** tipo compatible. | **vigente desde B3**; además exige que no quede un registro `ACTIVO` sobre la celda y se revalida tras el bloqueo — `case14`, `case14b` |
| RN-06 | `OCUPADA` es efecto derivado de un registro `ACTIVO`, no un valor escribible manualmente. | **vigente desde B3** — `case10`; y `case13` confirma que la entrada sí la produce |
| RN-07 | La salida libera la celda de ese registro y solo si estaba `OCUPADA`. | **vigente desde B3** (`liberarCelda(celdaId, registroPropioId)`) — `case15`, `case17`, `case18b` |
| RN-08 | A nivel de base, no puede existir más de un registro `ACTIVO` por celda. | **escrita en `V3`, sin probar** — H2 no soporta índices parciales y Flyway está desactivado en `test`. Con la salvedad de RF-NF-05 |
| RN-09 | Un cliente no es una cuenta: sin credenciales, sin rol, sin token. | **nueva — vigente desde B2** (por construcción: `Cliente` no tiene columna de credencial ni autoridad y el login solo lee `usuario`) |
| RN-10 | Documento de cliente y documento/correo de personal son únicos; el duplicado se comunica como `409`, no como `500`. | **cumplida**: personal en B0, cliente en B2 con la pareja tipo+documento en servicio y en `uk_cliente_tipo_documento`. Solo observada sobre H2 |
| RN-11 | Debe existir siempre al menos un `ADMINISTRADOR` activo. | **nueva — vigente desde B1** (secuencial probado; la garantía concurrente sigue pendiente de PostgreSQL) |
| RN-12 | Ninguna baja (cliente, celda, personal) es física si hay histórico asociado. | **cliente, personal y celda vigentes** (B2, B1 y B3): no existe `DELETE` de celdas, la vía admitida es `PATCH /celdas/{id}/activo` y se rechaza con ocupación activa (`case07`) |
| RN-13 | El histórico de registros, novedades y comprobantes no se altera por la desactivación de un cliente o una celda. | **cliente y celda cumplidas y probadas sobre H2** (`case17_actualizarElClienteNoReescribeLosHistoricosDeRegistro` en B2; `case06` y `case16` en B3: desactivar conserva el estado operativo y el historial cerrado sigue íntegro) |
| RN-14 | Un cliente **inactivo** no admite vinculaciones nuevas ni el ingreso de sus vehículos; la salida sí se permite. | **nueva — vigente desde B2** (`VehiculoService.vincularCliente` y la guardia de `RegistroService.registrarEntrada`) |
| RN-15 | Cambiar el titular de un vehículo **ya vinculado** exige `ADMINISTRADOR` y que el vehículo no tenga un ingreso activo. | **nueva — vigente desde B2**, aplicada en el servicio (no en el controlador) para que dependa del estado guardado |
| RN-16 | El texto libre de `propietario_*` **no** puede cambiar la titularidad de un vehículo con cliente. | **nueva — vigente desde B2** (`crearOActualizar` ignora esos campos si hay titular; `toDTO` responde siempre desde el cliente) |
| RN-17 | La identidad del cliente es la pareja **tipo de documento + documento**, no el documento aislado. | **nueva — vigente desde B2** (`uk_cliente_tipo_documento` + `findByTipoDocumentoAndDocumento`) |

### Casos de error acordados (formato y código)

| Caso | Código | Mensaje esperado | Dónde se produce |
|---|---|---|---|
| Cuenta desactivada usa un token aún vigente | `401` | sesión no disponible, token rechazado | `AuthService.validarToken` / filtro |
| Rol sin permiso sobre la operación | `403` | acceso denegado | `@PreAuthorize` (handler ya existe, `GlobalExceptionHandler.java:84`) |
| Documento de cliente o de personal duplicado | `409` | «ya existe…», **sin** filtrar el valor de la otra cuenta | servicio + constraint |
| Correo de personal duplicado | `409` | ya implementado | `UsuarioService` |
| Falta la contraseña inicial / es demasiado corta | `400` | detalle por campo | Bean Validation en el DTO |
| Desactivar celda con ocupación activa | `409` | «La celda tiene un vehículo adentro; no se puede desactivar» | **B3** — `CeldaService.cambiarActivo` (`case07`) |
| Cambiar manualmente una celda a `OCUPADA` | `409`, **no `400`** | «El estado OCUPADA se produce al registrar una entrada, no se puede asignar manualmente» | **B3** — `CeldaService.cambiarEstado`, antes de leer la base (`case10`) |
| Editar código/piso/tipo de una celda ocupada | `409` | «La celda está ocupada: solo se pueden cambiar las observaciones» | **nuevo en B3** — `CeldaService.actualizar` (`case05`) |
| Mantenimiento sobre celda inactiva | `409` | «La celda está inactiva; reactívala antes de cambiar su estado operativo» | **nuevo en B3** — `case09` |
| Estado operativo y registros que no cuadran | `409` | «El estado de la celda no coincide con sus registros activos» — **no se repara** | **nuevo en B3** — `exigirCoherencia` (`case18b`) |
| Liberar una celda que pertenece a otro registro | `409` | «La celda corresponde a otro registro activo» | **nuevo en B3** — `liberarCelda` (`case18`) |
| Sin celdas disponibles del tipo pedido | `409` | mensaje actual, conservado | `CeldaService.asignarCeldaDisponible` |
| Dos entradas simultáneas por la misma celda (perdedor) | `409` | «No hay celdas disponibles para vehículos tipo …» o, si el bloqueo se agotó, el mensaje de bloqueo de celda. **Nunca SQL ni nombres de restricción** | conflicto de ocupación (`PessimisticLockingFailureException` → `GlobalExceptionHandler`) |
| Duplicado que escapa a la validación y llega a la BD | `409` | handler **ya existente desde B0**, con catálogo por nombre exacto de restricción (`uk_usuario_documento`, `uk_usuario_correo`, `uk_cliente_tipo_documento` y, desde **B3**, `uk_celda_numero`) | `GlobalExceptionHandler.MENSAJES_POR_RESTRICCION` |
| Vincular un cliente inactivo | `409` | «No se puede vincular un vehículo a un cliente inactivo» | **nuevo en B2** — `VehiculoService.vincularCliente` |
| Cambiar el titular con un ingreso activo | `409` | «El vehículo tiene un ingreso activo; registre la salida antes de cambiar su titular» | **nuevo en B2** — `VehiculoService.vincularCliente` |
| Ingreso de un vehículo cuyo titular está inactivo | `409` | «El cliente vinculado está inactivo y no admite un nuevo ingreso» | **nuevo en B2** — `RegistroService.registrarEntrada`, antes de asignar celda |
| OPERADOR que desactiva un cliente o cambia un titular | `403` | formato `ErrorResponse` global | **nuevo en B2** — `@PreAuthorize` y `AccessDeniedException` en el servicio |
| Vinculación con placa o cliente inexistente | `404` | «Vehículo no encontrado con placa: …» / «Cliente no encontrado con ID: n» | **nuevo en B2** |
| Id o documento inexistente | `404` | ya implementado | `ResourceNotFoundException` |
| Payload inválido | `400` | con mapa de campos, ya implementado | `MethodArgumentNotValidException` |

Decisión de diseño (**D7**, resuelta en B2 con la opción específica): el mensaje de `409` por documento duplicado
puede revelarse como vector de enumeración de documentos. En una app interna de mostrador se consideró
aceptable y quedó como una sola constante (`ClienteService.MENSAJE_DOCUMENTO_DUPLICADO`). Si Isabella lo ve
distinto, se unifica el mensaje; es reversible y no cambia el esquema.

---

## 8. Pantallas necesarias

El frontend actual es un SPA de JS sin empaquetador (`src/main/resources/static/`: `index.html`, `app.js`
244 líneas en `HEAD`, 521 tras B2 y **759 tras B3**, `styles.css`), con login y **seis** vistas: **dashboard,
entrada, salida, novedades, clientes y celdas**. No hay rediseño en esta iteración: se extiende el patrón
existente (vista + formulario + tabla).

| # | Pantalla | Rol | Contenido mínimo | Estado |
|---|---|---|---|---|
| 1 | Dashboard | ADMIN/OPERADOR | tile de celdas: disponibles / ocupadas / mantenimiento / **inactivas** (dato nuevo) | **ampliado en B3** con `celdasActivas`, `celdasDisponibles` y `celdasMantenimiento`; sin revisión en navegador |
| 2 | Registrar entrada | OPERADOR | formulario actual + selector/creación rápida de cliente (RF-CLI-08) | existente; **B2 no la tocó** |
| 3 | Registrar salida | OPERADOR | sin cambios funcionales; mostrar celda liberada | existente |
| 4 | Novedades | OPERADOR/ADMIN | sin cambios | existente |
| 5 | **Gestión de clientes** | ADMIN/OPERADOR | lista con filtro por documento/nombre, alta, edición, activar/desactivar, vehículos asociados | **existe desde B2** (buscador, filtro, paginación, formulario, chip de estado, panel de vehículos, vista «sin titular» y vinculación por placa). Verificada con 14 pruebas en Node, **no en navegador** |
| 6 | **Gestión de celdas** | ADMIN (consulta también OPERADOR) | alta, edición (piso/tipo/observaciones), cambiar estado (mantenimiento), desactivar/reactivar, indicador de ocupación actual | **existe desde B3** (buscador por código, cuatro filtros, paginación, formulario, chip de estado operativo e indicador activo/inactivo, confirmación en dos clics, estados de carga/vacío/error y bloqueo de dobles envíos). **Sin botón alguno para poner `OCUPADA` ni liberar a mano.** Verificada con 16 pruebas en Node, **no en navegador** |
| 7 | **Gestión de personal** | ADMIN | lista, alta con contraseña inicial, edición, desactivar/reactivar, rol | **parcial**: hoy solo API, sin vista |
| 8 | Login | — | sin cambios; debe reflejar el `401` por cuenta desactivada | existente |

Restricción práctica: en `app.js` las rutas de API y las vistas están hardcodeadas; no hay router ni
componentes. Cada vista nueva es un bloque de HTML + funciones JS. Esto es consistente con «no rediseño del
frontend» pero implica que las vistas nuevas **no** pueden tratarse como entregable pulido: son funcionales
para la demostración.

---

## 9. Modelo de datos propuesto y tratamiento del histórico

Las secciones de **clientes** quedaron escritas en `V2__clientes_y_vinculo_vehiculos.sql` dentro de B2 — **escritas,
no ejecutadas**: ninguna base persistente las ha interpretado. Las de **celdas** siguen siendo propuesta.
`V1` no se toca.

### 9.1 Tabla `cliente` — como quedó en V2 (B2)

```
cliente
  id               BIGINT GENERATED BY DEFAULT AS IDENTITY PK
  tipo_documento   VARCHAR(20)  NOT NULL          -- CHECK en CC/CE/TI/PASAPORTE/OTRO
  documento        VARCHAR(20)  NOT NULL          -- UNIQUE junto con tipo_documento
  nombre_completo  VARCHAR(120) NOT NULL
  telefono         VARCHAR(20)  NOT NULL
  activo           BOOLEAN      NOT NULL DEFAULT TRUE
  fecha_registro   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
  CONSTRAINT uk_cliente_tipo_documento UNIQUE (tipo_documento, documento)
```

Diferencias con la propuesta original de esta sección, todas deliberadas y ya reflejadas en el código:

- **no hay `correo`** (D8 resuelta en negativo: el cliente no autentica ni se notifica por ese canal);
- **no hay `observaciones`** (ningún requisito la pedía);
- `nombre`/`apellido` se fundieron en **`nombre_completo`**, como en la ficha del encargo;
- `telefono` es **obligatorio** (el encargo lo pide) y `tipo_documento` también, porque la identidad es la
  pareja, no el documento aislado;
- la unicidad es `(tipo_documento, documento)` en lugar de `documento` a secas.

Sin `clave_hash`, sin `tipo` de usuario, sin Authorities. **Es la garantía estructural de que un cliente no
puede iniciar sesión.** Índices: `idx_cliente_documento`, `idx_cliente_nombre_completo`,
`idx_cliente_activo`.

### 9.2 `vehiculo`: coexistencia con la Iteración 1 — **resuelto en B2 con la opción 1**

`ALTER TABLE vehiculo ADD COLUMN cliente_id BIGINT NULL` + `CONSTRAINT fk_vehiculo_cliente FOREIGN KEY
(cliente_id) REFERENCES cliente(id) ON DELETE RESTRICT` + `idx_vehiculo_cliente`. Las columnas
`propietario_*` se **conservan** (RF-CLI-07). De las tres formas que se barajaban:

1. **Solo lectura — elegida y escrita.** Con un giro adicional para que no haya ambigüedad: cuando el
   vehículo **tiene** cliente, `crearOActualizar` deja de escribir en `propietario_*` y `toDTO` responde
   siempre desde el cliente. Así las columnas viejas son residuo histórico de la Iteración 1 y **una sola
   función decide la fuente**, sin duplicar la regla en el controlador, el servicio y la pantalla.
2. Sincronizadas — descartada: dos fuentes de verdad activas y más código para el mismo resultado.
3. Backfill y abandono — **descartada**: poblar `cliente` desde `propietario_documento` habría convertido
   texto libre sin normalizar en identidades oficiales e imposibles de reconciliar. La vinculación quedó
   como operación humana, consciente y auditada.

`VehiculoDTO` **añadió** `clienteId` y conservó `propietarioDocumento/Nombre/Telefono`, así que el contrato
con Isabella, el Postman y el frontend no se rompió. `RegistroEntradaDTO` **no se tocó**: ningún endpoint de
entrada exige cliente obligatorio. Lo que sí cambió fue la *respuesta* de `POST`/`PUT /vehiculos` (ver §10.1
y `07_B2_CLIENTES_POSTGRESQL.md` §5).

### 9.3 `celda`: baja lógica sin contaminar el estado — **resuelto en B3**

```
celda
  ... (numero, piso, tipo_vehiculo, estado, observaciones intactos)
  activo          BOOLEAN NOT NULL DEFAULT TRUE   -- NEW (columna añadida en V3, aún sin ejecutar)
```

La propuesta de añadir también `version BIGINT` con `@Version` **no se implementó**: el bloqueo pesimista con
revalidación ya serializa las escrituras y amontonar mecánicas no aportaba nada (así lo pedía el encargo de
B3). Si algún día se quisiera el bloqueo optimista, sería una decisión nueva del equipo.

`EstadoCelda` **no** se amplía con `INACTIVA`: mezclar vigencia y ocupación en un solo enum es lo que hoy
impide cumplir RF-CEL-04 y RF-CEL-06. Con la columna aparte, el filtro de asignación pasó a
`activo = TRUE AND estado = DISPONIBLE AND NOT EXISTS (registro ACTIVO sobre esa celda)` (RF-CEL-07); la
tercera condición no estaba en la propuesta y hace falta porque el estado puede ir rezagado respecto del
registro. «Inactiva» en la pantalla es etiqueta derivada de `activo = false`.

### 9.4 Regla de ocupación única — **escrita en B3, sin probar en PostgreSQL**

- En **Java** (decisión **D1**, resuelta en B3 por el equipo con el encargo en la mano): bloqueo pesimista
  `@Lock(PESSIMISTIC_WRITE)` sobre el finder de la celda **y** sobre la fila del vehículo por placa, con
  revalidación después de adquirir el bloqueo, orden de bloqueo documentado (vehículo → celda) y reintento
  acotado a 5 candidatos. No se usó `@Version`.
- En **PostgreSQL**: los dos índices únicos parciales sobre `registro(celda_id)` y `registro(vehiculo_id)`
  restringidos a `estado = 'ACTIVO'` **ya están escritos en `V3`**.
  **Precaución que se mantiene verificada:** el perfil de prueba usa H2 en modo PostgreSQL, que **no** soporta
  índices parciales, y además **Flyway está desactivado en `test`**, así que ni `V2` ni `V3` se interpretan
  durante la suite. Consecuencia: la restricción de BD queda **sin cobertura de pruebas automáticas** hasta
  que haya una prueba real contra PostgreSQL. Registrarlo, no afirmarlo como resuelto.

### 9.5 Histórico: qué se conserva y cómo

- `registro` es la línea de tiempo maestra: FK a `vehiculo`, `celda`, `usuario_entrada`, `usuario_salida`,
  con `fecha_hora_entrada`/`fecha_hora_salida` y `estado`. **No** se reescribe nunca al desactivar cliente o
  celda (RF-CLI-05, RF-CEL-05, RN-13).
- Las bajas de cliente, celda y personal son **lógicas** (`activo = false`). No existe borrado físico
  admitido cuando hay histórico; si no lo hay, la vía sigue siendo la lógica. **Cerrado para clientes en
  B2**: no hay endpoint `DELETE` de clientes y la base rechaza el borrado con `ON DELETE RESTRICT`
  (comprobado en H2). Para personal quedó cerrado en B1 (el `DELETE` es baja lógica). Celdas: pendiente B3.
- `novedad`, `comprobante` y `auditoria_log` conservan sus FK actuales; `auditoria_log` es el lugar donde debe
  quedar «quién desactivó qué y cuándo» (RF-TRX-02), para que la trazabilidad no dependa de reconstruir
  valores antiguos.
- Una celda desactivada con histórico debe seguir apareciendo en reportes de ocupación con la marca
  «dada de baja» (evita que un informe del mes pasado pierda filas).

### 9.6 Consecuencia operativa del `ddl-auto: validate`

Añadir un campo a la entidad sin su columna en `V2` **rompe el arranque**, y las pruebas con H2 usan
`create-drop` desde las entidades, así que **los tests pueden estar verdes mientras la aplicación real no
levanta**. Por eso cada bloque de implementación termina con una comprobación de arranque contra PostgreSQL
(o al menos con `validate` contra el esquema migrado), no solo con la suite de pruebas.

**B2 dejó ese paso sin cumplir.** Sus 259 pruebas Java están verdes sobre H2 y aun así **no hay ninguna
evidencia de que `Cliente` y `Vehiculo` validen contra el esquema de V2 en PostgreSQL**, porque V2 nunca se
ejecutó ahí y la aplicación no se arrancó (el perfil `dev` la lanzaría contra una base de datos no
confirmada). Es exactamente el escenario que esta sección advertía: véase
`07_B2_CLIENTES_POSTGRESQL.md` §3.2 y §9.2. Queda como primer paso pendiente del siguiente bloque con
autorización de escritura.

---

## 10. Endpoints existentes que se conservan y endpoints propuestos

### 10.1 Se conservan sin cambiar su contrato (todos verificados en el código)

| Verbo y ruta | Controller |
|---|---|
| `POST /api/v1/auth/login`, `POST /auth/logout`, `GET /auth/me` | `AuthController.java:31,38,46` |
| `POST /api/v1/registros/entrada`, `PUT /registros/salida/{placa}`, `DELETE /registros/{id}`, `GET /registros/activos`, `GET /registros/{placa}`, `GET /registros/ultimos` | `RegistroController.java:31-80` |
| `GET /api/v1/celdas`, `GET /celdas/disponibles`, `GET /celdas/{id}`, `PUT /celdas/{id}/estado` | `CeldaController.java` — **contrato de array conservado en B3**; lo que cambió fue el comportamiento del `PUT …/estado` (ver abajo) |
| `GET /api/v1/vehiculos`, `GET /vehiculos/{placa}`, `POST /vehiculos`, `PUT /vehiculos/{id}`, `PUT /vehiculos/{placa}/cliente` | `VehiculoController.java:30-77` (la última, nueva en B2) |
| `POST /api/v1/novedades`, `GET /novedades`, `GET /novedades/pendientes` | `NovedadController.java:27-51` |
| `GET /api/v1/comprobantes/{codigo}/pdf`, `GET /comprobantes?registroId=` | `ComprobanteController.java:27-42` |
| `GET /api/v1/reportes/dashboard`, `GET /reportes/ocupacion` | `ReporteController.java:27-36` |
| `GET /api/v1/usuarios`, `POST /usuarios`, `PUT /usuarios/{id}`, `DELETE /usuarios/{id}` | `UsuarioController.java:28-53` |

Cambios **de comportamiento** (misma firma, nueva validación) sin romper contrato: `PUT /celdas/{id}/estado`
**ya rechaza `OCUPADA` desde B3** con `409` y `MENSAJE_OCUPADA_NO_ASIGNABLE_MANUAL` —no con `400`/`422` como
se propuso aquí— y además rechaza sobre celda inactiva o inconsistente (RF-CEL-08); `DELETE /usuarios/{id}`
pasa a respetar RN-11 (último admin); `POST /usuarios` pasa a exigir `claveInicial` (RF-ACC-03). El primero
**cambió respuestas observables** y hay que avisarlo a Isabella porque afecta sus casos de uso y su plan de
pruebas.

Cambio **de contrato** ya ejecutado en B2 (hay que avisarlo a Isabella): `POST /api/v1/vehiculos` y
`PUT /api/v1/vehiculos/{id}` dejaron de responder el eco del cuerpo recibido y ahora responden la
proyección del servicio; `VehiculoDTO` gana `clienteId` y, en un vehículo con titular, los campos
`propietario_*` se responden desde el `Cliente`. Motivo y evidencia en `07_B2_CLIENTES_POSTGRESQL.md` §5.

### 10.2 Rutas propuestas y su estado real (prefijo `/api/v1`, convenciones actuales: `ResponseEntity`, DTO, `@Valid`)

| Verbo y ruta | Rol | Descripción |
|---|---|---|
| `GET /clientes?criterio=&activo=&page=&size=` | ADMIN/OPERADOR | **existe desde B2**. Listado paginado (tope de página 100, orden `id ASC`); la búsqueda es un único `criterio` que contiene documento **o** nombre, no dos parámetros separados como se propuso aquí. |
| `POST /clientes` | ADMIN/OPERADOR | **existe desde B2**. Alta; `409` por repetir la pareja tipo de documento + documento. |
| `GET /clientes/{id}` | ADMIN/OPERADOR | **existe desde B2**. Detalle del cliente **sin** los vehículos; la lista de vehículos es su propia ruta. |
| `GET /clientes/{id}/vehiculos` | ADMIN/OPERADOR | **existe desde B2** (ruta añadida; no estaba en la propuesta). |
| `PUT /clientes/{id}` | ADMIN/OPERADOR | **existe desde B2**. Actualiza datos, nunca el estado. |
| `PATCH /clientes/{id}/activo` | **ADMIN** | **existe desde B2** (`@PreAuthorize`), con `activo` obligatorio en el cuerpo. |
| `POST /api/v1/celdas` | ADMIN | **existe desde B3**. Alta; nace `activo = true` y `DISPONIBLE`; `409` si el código ya está tomado. |
| `PUT /api/v1/celdas/{id}` | ADMIN | **existe desde B3**. Reenvía la ficha completa; si la celda está ocupada solo admite observaciones. |
| `PATCH /api/v1/celdas/{id}/activo` | ADMIN | **existe desde B3** — desactivar/reactivar con las reglas RN-04/RN-06; **no** es una vía para ocupar ni liberar. |
| `GET /api/v1/celdas/pagina` | ADMIN/OPERADOR | **existe desde B3** (ruta añadida, sustituye la propuesta `GET /celdas/estadistica`). `Page` con `criterio`, `estado`, `tipo`, `piso`, `activo`, `page`, `size` (tope 100) y orden `numero ASC`. Los conteos del panel no viven aquí: los trae `GET /reportes/dashboard` con `celdasTotal`, `celdasActivas`, `celdasDisponibles`, `celdasOcupadas`, `celdasMantenimiento`. |
| `PATCH /api/v1/usuarios/{id}/activo` | ADMIN | **existe desde B1** — reactivar/desactivar explícito (D3) |
| `PUT /api/v1/vehiculos/{placa}/cliente` | ADMIN (cambio de titular) / ADMIN y OPERADOR (vinculación inicial) | **existe desde B2**. La ruta propuesta como `GET /{placa}/cliente` y `PUT /{id}/cliente` **no** se implementó: la vinculación quedó en una sola operación, con la placa como llave natural, por la razón documentada en `07_B2_CLIENTES_POSTGRESQL.md` §4.1. |

De la tabla queda pendiente solo `GET /clientes/documento/{documento}`, descartada en favor del `criterio`
único. Las demás existen y tienen prueba sobre H2. La vista de administración de celdas propuso además un
`GET /celdas/estadistica` que **no** se implementó: los conteos viajan en el dashboard existente.

---

## 11. Pruebas necesarias

### 11.1 Unitarias de servicio (sin Spring, Mockito, patrón ya usado)

| Grupo | Casos mínimos |
|---|---|
| `ClienteService` | alta correcta; documento duplicado `409` (creación y actualización); actualización sin tocar el histórico; desactivar cliente con vehículos asociados; un cliente **nunca** expone credenciales; vehículo sin cliente sigue siendo operable. |
| `CeldaService` | alta con `numero` único; desactivar con ocupación activa → rechaza; desactivar con solo histórico cerrado → ok; reactivar → `DISPONIBLE`/`MANTENIMIENTO`, jamás `OCUPADA`; asignación salta inactivas; asignación salta `MANTENIMIENTO`; liberar solo la celda indicada; liberar no pisa una de mantenimiento; cambio manual a `OCUPADA` rechazado. |
| `UsuarioService` | `claveInicial` obligatoria y política de longitud; hash BCrypt aplicado (no se guarda en claro); duplicado de documento en **actualización** → `409`; último admin no se desactiva; último admin no se degrada; reactivar. |
| `AuthService` | `validarToken` rechaza cuenta inactiva; reactivar restaura; el login sigue rechazando inactivos (regresión). |
| `RegistroService` | entrada con cliente asociado; salida libera la celda correcta; entrada no asigna celda inactiva. |

### 11.2 Pruebas de permisos (integración MockMvc, patrón `@WithMockUser`/`spring-security-test`)

Una matriz por cada endpoint nuevo y por los tres que cambian de comportamiento: `401` sin token, `403` con
el rol equivocado, `2xx` con el rol correcto, y **`401` con token de cuenta desactivada** (RF-ACC-01). Hoy
solo `UsuarioControllerTest` cubre rechazo de rol; hay que llevar eso a celdas, clientes y a los endpoints
ya existentes.

### 11.3 Pruebas de persistencia

- `@DataJpaTest` sobre H2: restricciones únicas (`uk_cliente_documento`, `uk_celda_numero`), cascadas, que la
  desactivación **no** borre filas y que el histórico siga consultable.
- Verificación explícita de que las entidades nuevas y sus columnas coinciden con `V2` (arranque con
  `ddl-auto: validate`). **Esto no lo cubre la suite actual**, que usa `create-drop`.

### 11.4 Pruebas de concurrencia (la que decide RF-CEL-10)

- **Ejecutadas en B3** (`CeldasIntegridadIntegrationTest`): dos hilos sobre `registrarEntrada` cuando solo
  queda una celda asignable, con `CountDownLatch` para sincronizar la salida y **tope de espera** en el `get`
  de cada hilo (un bloqueo indefinido falla la prueba en vez de colgarla). Aserción: `1` registro `ACTIVO`,
  `0` celdas con dos registros activos, `1` celda `OCUPADA`, el perdedor con `BusinessRuleException` y **sin
  fila de vehículo escrita**. Variante añadida: el mismo vehículo ingresado a la vez, con la fila precreada
  para que el bloqueo por placa pueda serializar.
- **Límite honesto (sigue en pie después de B3):** H2 en memoria no reproduce el bloqueo ni el índice parcial
  de PostgreSQL, y el perfil `test` **no corre Flyway**, así que los índices de `V3` ni siquiera se
  interpretaron. Las dos pruebas demuestran el mecanismo de Java —bloqueo pesimista, revalidación y reintento
  acotado—, **no** la restricción de BD. Hasta que no se ejecuten contra PostgreSQL real con una base de
  prueba autorizada, **no** se afirmará que RF-CEL-10 y RF-CEL-11 están cubiertos «en base de datos».

### 11.5 Pruebas de frontend (`node --test src/test/js/app.test.js`)

Extender el archivo actual (empezó con 4 pruebas de humo sobre `app.js`, **14** con las de clientes y **30**
con las de celdas añadidas en B3): falta revisar las vistas **en navegador** (§17) y que el `401` de cuenta
desactivada se maneje visualmente.

### 11.6 Regresión obligatoria

`mvn -B clean verify` con **la suite completa en verde** (0 fallos, 0 errores, 0 omitidas) en cada bloque,
más `node --test`. La cifra de referencia es la del último cierre: **314 Java + 30 Node** al terminar B3
(139 al cerrar B0, 220 al cerrar B1, 259 + 14 al cerrar B2). Sin `-Dmaven.test.failure.ignore` y sin
reutilizar cifras de corridas anteriores.

---

## 12. Secuencias que deberán representarse en StarUML

Herramienta obligatoria: **StarUML** (`.mdj` en `docs/staruml/`). Los diagramas Mermaid del repo
(`docs/08_ENTREGABLES_VISUALES.md`) apoyan pero **no** sustituyen el entregable. Solo existe
`AutosColombia_Iteracion1.mdj` (26 KB, 2026-10-19) y **no** está abierto ni verificado en StarUML.

**Iteración 1 (a modelar, desde el código real):**
1. Inicio de sesión: `AuthController → AuthService → UsuarioRepository → BCrypt → JwtUtil` y el filtro en
   peticiones siguientes.
2. Registro de entrada: `RegistroController → RegistroService → PicoYPlacaService → VehiculoService →
   CeldaService → RegistroRepository → Comprobante(QR) → AuditoriaService`.
3. Registro de salida: `RegistroController → RegistroService → CeldaService.liberarCelda → Comprobante`.

**Iteración 2 (a modelar):**
4. Clientes y titularidad — **con las clases y rutas reales ya implementadas en B2**, el listado quedó
   detallado en `07_B2_CLIENTES_POSTGRESQL.md` §12 (10 secuencias, diagramas aún sin producir): alta de
   cliente con `ClienteController → ClienteService → ClienteRepository → AuditoriaService`, alta concurrente
   resuelta por `uk_cliente_tipo_documento`, vinculación y cambio de titular por
   `VehiculoService.vincularCliente`, desactivación como baja lógica, **ingreso rechazado por titular
   inactivo antes de asignar celda**, salida permitida con titular inactivo y carga de la pantalla
   `#clientes-view`.
5. Creación de una cuenta del personal con contraseña inicial (ADMIN → `UsuarioService` → BCrypt →
   `auditoria_log`).
6. **Rechazo** de un token de cuenta desactivada (el caso nuevo que la Iteración 1 no tiene).
7. Desactivación de celda **con** ocupación activa (camino de error) y **sin** ella (camino de éxito),
   mostrando la rama de decisión.
8. Entrada con **conflicto de concurrencia** por la misma celda: dos hilos, bloqueo, uno gana, el otro
   recibe `409`.
9. Salida que libera únicamente su celda, con la rama de «celda en mantenimiento no se toca».
10. Protección del último administrador activo (rama de rechazo).

Cada secuencia debe nombrar las clases reales de esta carpeta, no componentes inventados.

---

## 13. Componentes reales que deberán representarse en StarUML

Extraídos de los paquetes existentes (**recuento verificado hoy**: 8 controladores, 10 servicios, 8
entidades, 7 enums, 8 repositorios y 19 DTOs; JaCoCo analiza **90 clases** en la corrida 2026-10-31
12:28). Cifras anteriores en esta bitácora: 72 clases con 8 servicios y 7 entidades, antes de B1 y B2.

| Componente | Paquete / artefacto | Estado |
|---|---|---|
| Controladores REST | `com.autoscolombia.presentation.controllers` (9: Auth, Registro, Celda, Vehiculo, Novedad, Comprobante, Reporte, Usuario, **Cliente**) | real; `ClienteController` **existe desde B2**; falta ampliar `CeldaController` |
| DTOs y mapeo | `presentation.dto` (19 archivos) | real; `ClienteDTO`, `ClienteCrearDTO`, `CambiarEstadoClienteDTO` y `VincularClienteDTO` **existen desde B2** |
| Configuración de seguridad | `presentation.config`: `SecurityConfig`, `JwtAuthenticationFilter`, `PasswordEncoderConfig` | real |
| Configuración temporal | `presentation.config.ClockConfig` | real, de la fase 01 |
| Servicios de dominio | `domain.service` (10: Auth, Registro, Vehiculo, Celda, Novedad, Comprobante, Reporte, Usuario, Auditoria, PicoYPlaca, **Cliente**) | real; `ClienteService` **existe desde B2** |
| Modelo de dominio | `domain.model` (8 entidades) + `domain.enums` (7 enums) | real; `Cliente` y `TipoDocumento` **existen desde B2** |
| Persistencia | `infrastructure.repository` (Spring Data JPA, 8) | real; `ClienteRepository` **existe desde B2** |
| Manejo de errores | `infrastructure.exception.GlobalExceptionHandler` + excepciones | real; el handler de `DataIntegrityViolationException` **existe desde B0** y B2 le añadió el catálogo por nombre de restricción |
| Utilidad JWT | `com.autoscolombia.util.JwtUtil` (jjwt) | real |
| Frontend estático | `src/main/resources/static/` servido por Spring Boot | real; `#clientes-view` **existe desde B2** |
| Migraciones | `src/main/resources/db/migration/` (Flyway) | real `V1`; **`V2` escrito en B2 y aún no ejecutado** |
| Base de datos | PostgreSQL (producción) · **H2 en memoria (pruebas)** | real; hay que dibujar la diferencia, porque hoy los tests no tocan PostgreSQL |
| Registro de dependencias | `pom.xml` (sin Maven Wrapper) | real |
| Imagen y orquestación | `Dockerfile` multi-etapa, `docker-compose.yml` | existen, **fuera del alcance de esta iteración**; si se dibujan, marcarlos como no modificados |
| CI | `.github/workflows/ci.yml` | real, reescrito a solo verificación |

Vistas recomendadas: componentes del sistema (desplegando `presentation` / `domain` / `infrastructure`),
componentes de despliegue (app + PostgreSQL + CI) y, si el equipo lo pide, paquete de pruebas separado.

---

## 14. Orden de implementación y archivos previsiblemente afectados

Cada bloque termina con la suite completa en verde. No adelantar bloque sin cerrar el anterior.

**B0 — Cimientos transversales** *(sin reglas de negocio nuevas)* — **hecho el 2026-10-30, bitácora `05`**
- `infrastructure/exception/GlobalExceptionHandler.java`: handler de `DataIntegrityViolationException` → `409`.
- `domain/service/UsuarioService.java`: validación de documento también en `actualizar` (RF-ACC-05).
- `infrastructure/repository/UsuarioRepository.java`: `findByDocumento` (consulta de lectura añadida en B0).
- Pruebas: `UsuarioServiceTest` (+4), `GlobalExceptionHandlerTest` (+8), `UsuarioIntegridadMockMvcTest` (nueva,
  8), `UsuarioIntegridadIntegrationTest` (nueva, 2). Suite: 139 → 161, sin regresión.
- Por qué primero: sin esto, todo lo nuevo que viole una única restricción devuelve 500 y las pruebas de
  duplicado no pueden escribirse.

**B1 — Cuentas del personal seguras** *(bloquea a los demás en términos de permisos)* — **hecho el 2026-10-30,
bitácora `06`**
- Nuevos (producción, 4 archivos): `presentation/dto/UsuarioCrearDTO.java` (único DTO que transporta la clave),
  `ContrasenaDentroDelLimiteBCrypt.java` + su `…Validator.java` (límite de 72 bytes UTF-8 medido en la
  librería), `CambiarEstadoUsuarioDTO.java` (`activo` obligatorio, nulo → `400`).
- Modificados (producción, 6): `UsuarioController.java` (sin contraseña fija compartida, `PATCH /{id}/activo`,
  salida siempre por `UsuarioDTO`), `UsuarioService.java` (`crear(UsuarioCrearDTO)`, `cambiarEstado`, guard
  del último administrador activo compartido por `PATCH`/`PUT`/`DELETE`), `AuthService.java` (`validarToken`
  consulta `activo` en toda petición protegida), `UsuarioRepository.java` (`findByTipoAndActivo` con
  `@Lock(PESSIMISTIC_WRITE)`), `SecurityConfig.java` (entrada anónima responde `401` con el formato
  `ErrorResponse`; `PATCH` en CORS), `GlobalExceptionHandler.java` (`HttpMessageNotReadableException` → `400`
  sin reflejar el payload).
- Pruebas: `CuentasPersonalIntegrationTest` (nueva, 33, con JWT reales y sin `@WithMockUser`),
  `ValidacionContrasenaDeCuentaTest` (nueva, 11), `LimiteRealDeBcryptTest` (nueva, 3), `UsuarioServiceTest`
  +10, `UsuarioControllerTest` +1, `AuthServiceTest` +1. Suite: 161 → **220, 0 fallos, 0 errores, 0 omitidas**.
- Lo que **no** cerró B1: la interfaz que consume estas rutas (frontend sin consumo verificado), la garantía
  de concurrencia sobre PostgreSQL (el `FOR UPDATE` solo se ejercitó en H2) y los endpoints que se dejaron
  excluidos a propósito (recuperación por correo, reinicio administrativo, cambio forzado en primer ingreso,
  MFA). Detalle en `06_B1_CUENTAS_PERSONAL.md` §8.
- Riesgo alto: tocar el filtro/validación de token podía romper las 139 pruebas actuales; no ocurrió.

**B2 — Clientes, relación cliente–vehículo, migración `V2` y su pantalla** — **hecho el 2026-10-31,
bitácora `07`, con PostgreSQL y el navegador pendientes**
- Nuevos (producción, 10): `db/migration/V2__clientes_y_vinculo_vehiculos.sql` (aditivo; **no se ejecutó
  sobre ninguna base persistente**), `domain/enums/TipoDocumento.java`, `domain/model/Cliente.java`,
  `infrastructure/repository/ClienteRepository.java`, `domain/service/ClienteService.java`,
  `presentation/controllers/ClienteController.java`, `ClienteDTO`, `ClienteCrearDTO`,
  `CambiarEstadoClienteDTO`, `VincularClienteDTO`. El archivo previsto se iba a llamar `V2__iteracion2.sql`;
  se nombró por su contenido, porque las celdas de B3 necesitan su propia migración.
- Modificados (producción, 7): `Vehiculo.java` (relación Many-To-One con `fk_vehiculo_cliente`),
  `VehiculoRepository.java` (3 buscadores de la relación), `VehiculoDTO.java` (+`clienteId`),
  `VehiculoService.java` (proyección única `toDTO`, vinculación/cambio de titular, `propietario_*` congelados
  si hay titular), `RegistroService.java` (guardia de titular inactivo antes de asignar celda),
  `GlobalExceptionHandler.java` (catálogo `uk_cliente_tipo_documento`), `VehiculoController.java`
  (`PUT /{placa}/cliente` y respuesta con la proyección del servicio).
- Interfaz (avance de B5, solo clientes): `static/index.html` (`#clientes-view`), `static/app.js` (módulo de
  clientes con paginación, escapado y bloqueo de doble envío), `static/styles.css`.
- Pruebas: `ClienteServiceTest` (nueva, 13), `ClientesVehiculosIntegrationTest` (nueva, 26, JWT reales y
  `Clock` fijado), `app.test.js` (+10), `VehiculoServiceTest` y `VehiculoControllerTest` adaptados. Suite:
  220 → **259 pruebas Java, 0 fallos, 0 errores, 0 omitidas** (`mvn -B -o clean verify`,
  2026-10-31T12:28:12-05:00) **+ 14/14 en Node**.
- Defectos encontrados y corregidos por las pruebas: `POST`/`PUT /vehiculos` respondían el eco del cuerpo
  (contradicción de titularidad resuelta en el backend, con el contrato y sus dos pruebas antiguas
  reescritos a la vista), el paginador de la pantalla invertía el estado del botón «Siguiente», y el aviso
  «Vinculación pendiente (ficha de la Iteración 1)» estaba en una rama inalcanzable (resuelto con la vista
  «Vehículos sin titular»).
- Lo que **no** cerró B2: el arranque validando el esquema contra PostgreSQL (`ddl-auto: validate` nunca
  corrió contra el motor objetivo), la observación de `23505`/`23503` reales, la verificación en navegador y
  RF-CLI-08 (cliente dentro del flujo de entrada), que quedó fuera del alcance autorizado.
- `RegistroEntradaDTO` **no** se tocó: los contratos de entrada conservan su forma y ningún endpoint de
  entrada exige cliente obligatorio.

**B3 — Celdas: baja lógica y estados controlados**
- `Celda.java` (+`activo`, +`@Version`), **`V3`** con esas columnas (V2 ya está ocupado por clientes),
  `CeldaService.java` (reglas RN-04…RN-07),
  `CeldaController.java` (endpoints nuevos, restricción de cambio manual), `CeldaRepository.java` (finder
  con filtro `activo` y bloqueo), `EstadoCelda` sin ampliar, pruebas `CeldaServiceTest`,
  `CeldaControllerTest`.
- Aquí vive el riesgo de concurrencia (RF-CEL-10).

**B4 — Integración con entrada/salida**
- `RegistroService` usa la asignación nueva y libera solo la celda correspondiente; pruebas de
  `RegistroServiceTest`, `RegistroControllerIntegrationTest` (manteniendo el reloj fijo de la fase 01).

**B5 — Frontend mínimo**
- `static/index.html`, `static/app.js`, `styles.css`; pruebas `src/test/js/app.test.js`.
- **Parcialmente adelantado en B2**: la pantalla de clientes y sus 8 pruebas JS ya existen. Falta la de
  celdas (B3) y la verificación en navegador de ambas.

**B6 — Entregables de diseño**
- `docs/staruml/AutosColombia_Iteracion2.mdj` (secuencias §12 y componentes §13) y verificación de que abre
  en StarUML. Los documentos de Isabella se actualizan por su lado con este insumo.

**B7 — Cierre**
- `README.md` (si el equipo quiere documentar los endpoints nuevos), bitácora `05_*` con resultados reales.

Fuera de este orden a propósito: `release.yml`, despliegue, pagos, y cualquier cambio de pico y placa.

---

## 15. Decisiones que todavía requieren confirmación

Solo las que **bloquean** escribir código.

| ID | Pregunta | Por qué bloquea | Opción recomendada (criterio técnico, no decisión tomada) |
|---|---|---|---|
| **D1** | ¿Mecanismo contra doble ocupación: bloqueo pesimista (`@Lock(PESSIMISTIC_WRITE)`), optimista (`@Version` + reintento) o solo constraint de BD? | Definía `Celda`, `CeldaRepository`, la migración y la prueba de concurrencia | **Resuelta y aplicada en B3** por el encargo: **bloqueo pesimista con revalidación** y reintento acotado a 5 candidatos, **sin** `@Version`, **más** los dos índices únicos parciales de `V3` como segunda línea. H2 sigue sin validar la parte de BD (RF-NF-05), así que la garantía de base continúa pendiente de PostgreSQL. Detalle en `08_B3_CELDAS_E_INTEGRACION.md` §5 y §6. |
| **D2** | ¿Qué hacemos con `propietario_documento/nombre/telefono` al introduce `cliente`? | Contractura del DTO, backfill y compatibilidad con Iteración 1 | **Resuelta e implementada en B2** con la Opción 1 y una precisión: las columnas se **conservan** y siguen legibles, pero dejan de escribirse cuando el vehículo tiene titular, y la respuesta se proyecta desde el cliente. **No se hizo backfill** desde `propietario_*` (habría creado clientes falsos a partir de texto libre). Detalle en `07_B2_CLIENTES_POSTGRESQL.md` §3.3 y §3.4. |
| **D3** | ~~¿Activar/desactivar personal con `PATCH /usuarios/{id}/activo` dedicado o basta el `PUT` actual con el campo `activo`?~~ **Resuelta y aprobada el 2026-10-30 dentro de B1**: endpoint dedicado, con `activo` obligatorio (nulo o ausente → `400`) y las mismas reglas de último administrador aplicadas a `PUT` y `DELETE`. | Ya no bloquea | Se documentó en `06_B1_CUENTAS_PERSONAL.md` §3 y §5. |
| **D4** | ¿Quién puede desactivar un **cliente**: también el operador, o solo administrador? | Matriz de permisos (§3) y pruebas de `403` | **Resuelta y verificada en B2**: solo `ADMINISTRADOR` (el operador recibe `403` con JWT real), y el mismo rol se exige para **cambiar el titular** de un vehículo ya vinculado. El operador sí crea, consulta, busca, actualiza y vincula un cliente activo a un vehículo sin titular. `case08`, `case09`. |
| **D5** | ¿Cancelar un registro (`DELETE /registros/{id}`) debe quedar restringido a administrador? Hoy cualquier rol autenticado puede. | Endpoint existente: cambiarlo rompe contrato con frontend y plan de pruebas | Requiere decisión de negocio; sin ella, no se toca y queda registrado como riesgo. |
| **D6** | ¿El cliente necesita `tipo_documento` (CC/CE/NIT) o basta el documento como texto? | Columna, validación y formato en el DTO | **Resuelta e implementada en B2**: `tipo_documento` es **obligatorio** y la identidad es la pareja tipo + documento (`uk_cliente_tipo_documento`), no el documento aislado. Los tipos son `CC`, `CE`, `TI`, `PASAPORTE`, `OTRO` — clasificación interna del proyecto, no validación oficial de identidad. NIT quedó fuera: el cliente es persona titular, no empresa. |
| **D7** | Mensaje de `409` por documento duplicado: ¿específico (revela que el documento existe) o genérico? | Casos de error (§7) y plan de pruebas de Isabella | **Resuelta en B2 con la opción específica** (`«Ya existe un cliente registrado con ese tipo y número de documento»`), en una sola constante compartida por el servicio y el handler. Es app interna de mostrador: el operador necesita saber que la pareja ya está registrada. Sigue siendo decisión de negocio reversible. |
| **D8** | ¿El cliente guarda correo y, con ello, datos personales sujetos a retención? | RF-NF-10, exports, dumps y documentos de clase | **Resuelta en B2: no se guarda correo**. El cliente no autentica ni se notifica por ese canal, así que no se añadió la columna ni su riesgo de retención. El dato personal que existe (documento, nombre, teléfono) se excluye del `detalle` de auditoría, comprobado en `case18`. |
| **D9** | Nombre exacto de la base de datos PostgreSQL real y si hay datos que deban conservarse | `V2`/`V3`, `validate` y el backfill de `cliente` | **Dato resuelto el 2026-10-31 por el usuario**: host `localhost`, puerto `5432`, base `Parqueadero de autos en colombia`, rol `postgres`, **sin datos que conservar**. Queda abierta la parte que bloquea la ejecución: **esquema, servidor objetivo (18 instalado vs `postgres:16-alpine`), si `flyway_schema_history` existe y con qué checksum, y el mecanismo** (psql fuera del `PATH`, Flyway o pgAdmin). Nada de esto se comprobó en el servidor: **no se conectó, no se migró**. Ver `08` §12. |
| **D10** | ~~Política de la contraseña inicial del personal: longitud mínima, si el usuario debe cambiarla al primer acceso, y cómo se entrega~~ | `UsuarioDTO`, validaciones, historias HU-I2-02 y criterios de aceptación | **Parcialmente resuelta en B1**: mínimo **15 caracteres** con tope de **72 bytes UTF-8** medido en la librería, sin reglas de composición, sin `trim` y sin truncar; el cambio obligatorio en primer acceso quedó **excluido** por el encargo. Sigue abierto **cómo se entrega** la clave al personal (canal fuera de la API). |

Y una decisión de proceso, no de código: **cuándo** se hace el primer commit/push de esta línea preparada
(commits siguen pendientes, `e701c75` ya publicado con el workflow antiguo de publicación de imágenes).

---

## 16. Datos no secretos necesarios para verificar el entorno PostgreSQL

Necesarios antes de poder afirmar algo sobre la base real. **No se pide la contraseña**: esa se configurará
de forma privada, igual que `DB_PASSWORD` en el README.

Reconocimiento de **solo lectura** hecho el 2026-10-31 dentro de B2, sin conectar a ninguna base y sin abrir
ningún archivo de secretos: el servicio `postgresql-x64-18` está **Running**; `psql.exe` sí existe, en
`C:\Program Files\PostgreSQL\18\bin` (fuera del `PATH`); hay una instalación de pgAdmin 4 junto a ella; no
hay Docker en el `PATH`; no existe `.env` (solo `.env.example`) ni `.pgpass`; y no hay ninguna variable
`DB_*`, `SPRING_*` ni `PG*` definida en el entorno. El perfil activo por defecto es `dev`
(`ddl-auto: validate`, Flyway `enabled: true`, `baseline-on-migrate: true`), por lo que **arrancar la
aplicación aplicaría V2**: no se arrancó. La lectura de `postgresql.conf` quedó bloqueada por la política del
entorno y **no se reintentó** por ninguna vía.

| Dato | Formato esperado | Estado |
|---|---|---|
| **Host** | `localhost`, IP o nombre de servicio | **confirmado por el usuario (2026-10-31): `localhost`** —la dirección `::1` es su versión IPv6, o sea el propio equipo. |
| **Puerto** | número (por defecto `5432`) | **confirmado por el usuario: `5432`**. `8080` es el puerto HTTP de la app, no de PostgreSQL. |
| **Nombre exacto de la base** | texto tal como está en el servidor | **confirmado por el usuario: `Parqueadero de autos en colombia`**. **No coincide** con `Garajeautomovil` (`application.yml`) ni con `garajeautomovil` (`docker-compose.yml:8`). Lleva espacios: como identificador hay que escribirlo **entre comillas dobles**. Sigue sin comprobarse en el servidor (no se conectó). |
| **Esquema** | `public` u otro | sin confirmar |
| **Usuario / rol de conexión** | nombre del rol (p. ej. `postgres` u otro) | **confirmado por el usuario: `postgres`**. Se desconoce si tiene privilegios de DDL. **La contraseña no se compartió y no se escribe en ningún archivo.** |
| **Versión del servidor** | p. ej. `16.x` | el servicio instalado es **18** (`postgresql-x64-18`). `docker-compose.yml` fija `postgres:16-alpine`, es decir el contenedor y esta instalación **no son el mismo servidor**; hay que decidir cuál es el objetivo. |
| **Herramienta disponible** | pgAdmin / psql / otra (DBeaver, tabla de Docker) | **confirmado**: psql 18 y pgAdmin 4 instalados, ninguno en el `PATH`; sin Docker en el `PATH`. |
| **¿Contiene datos que deban conservarse?** | sí / no / «es solo la semilla de `V1`» | **confirmado por el usuario: no** —sin tablas de usuario ni filas registradas. Por eso no hace falta respaldo; **aun así no se ejecutó nada**. |
| **¿La base ya está migrada?** | si la tabla `flyway_schema_history` existe y con qué versión | sin confirmar. Con la base vacía es **probable que `V1` no esté aplicado** —inferencia, no comprobación. |
| **Cómo se ejecutará la verificación** | psql / Flyway / pgAdmin, sobre el servicio local 18 | pendiente de la autorización específica de escritura (§9.1 de `07_B2_CLIENTES_POSTGRESQL.md`), que ahora abarca **V1 → V2 → V3**. |

Con eso se puede: verificar `validate`, aplicar `V1`, `V2` y `V3` y cerrar las pruebas de persistencia de
§11.3. Sin la autorización expresa, **toda afirmación sobre PostgreSQL sigue siendo sin verificar**, como
hasta ahora.

---

## 17. Qué NO afirma este documento

- Este documento se escribió como **especificación previa** y sigue teniendo secciones que son propuesta, no
  realidad. Lo que **sí** está implementado hoy son los bloques cerrados: **B0** (bitácora `05`), **B1**
  (`06`), **B2** (`07`) y **B3** (`08`). B3 absorbió por completo lo que aquí se numeraba como «celdas (B3)» y
  «pantalla de celdas (B5)», además de la integración de celdas con entrada, salida, cancelación y dashboard.
  Lo que **no existe** sigue siendo: el cliente elegido **dentro** del flujo de entrada (RF-CLI-08), la vista
  de personal, los diagramas de StarUML y cualquier comprobación contra PostgreSQL.
- Lo implementado en B2 y en B3 está **probado sobre H2, no sobre PostgreSQL**: ni `V2` ni `V3` fueron
  interpretados por el motor objetivo (el perfil `test` desactiva Flyway), `ddl-auto: validate` nunca corrió
  contra él, los índices únicos parciales de `V3` no se ejercitaron y las cifras `23505`/`23503` reales no se
  observaron.
- **Ninguna pantalla se abrió en un navegador.** Clientes (B2) y Celdas (B3) existen, suman 30 pruebas en
  Node y **cero** verificación visual; los recorridos pendientes están en `08` §13.
- La matriz de permisos describe el **hoy verificado** y el **objetivo**; las celdas con ❌ son defectos o
  huecos reales, no trabajo terminado.
- RF-NF-09: el workflow de solo verificación está reescrito y validado **solo localmente**; no se ejecutó en
  GitHub Actions.
- Los diagramas de §12 y §13 son **listados de lo que hay que modelar**, no diagramas existentes.
- Las decisiones abiertas son las que siguen sin tachado en §15 (**D1**, **D5**, **D9**, **D10** en su parte
  de canal de entrega). Si algo se implementa asumiendo una de esas opciones sin confirmación, eso es
  decisión propia del agente, no del equipo.
- Nada de lo anterior es instrucción textual del profesor: la Iteración 2 es **una propuesta del equipo**
  para resolver el enunciado.
