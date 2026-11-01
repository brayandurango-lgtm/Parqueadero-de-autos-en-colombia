# 08 — Bloque B3: gestión de celdas, integración con la operación e interfaz

**Proyecto:** Sistema de información del parqueadero Autos Colombia
**Bloque:** B3 — Celdas, integridad de la ocupación, integración y pantalla de administración
**Carpeta de trabajo:** `C:\Users\Administrador\Downloads\Parqueadero de autos en colombia`
**Fecha:** 2026-10-31
**Autorización que gobierna este documento:** «AUTORIZACIÓN — B3: CELDAS, INTEGRACIÓN E INTERFAZ», apartados A a K.

> **Qué afirma y qué no afirma este documento.** Todo lo marcado *verificado* se ejecutó en esta sesión y su
> cifra está respaldada por el comando indicado en §11. Todo lo marcado *pendiente* no se ejecutó: **no se
> arrancó la aplicación**, **no se abrió ningún navegador**, **no se conectó ni escribió en PostgreSQL** y
> **no se aplicó ninguna migración** sobre base persistente. Las pruebas de integración y de concurrencia
> corrieron sobre **H2 en modo PostgreSQL**, que no es PostgreSQL (§12).

---

## 1. De dónde partía el bloque

El encargo pedía conservar expresamente estas limitaciones, y se conservan:

| Punto de partida | Estado al iniciar B3 |
|---|---|
| B2 entregó clientes (backend, vinculación y pantalla) | entregado |
| La pantalla de clientes nunca se revisó en navegador | **seguía sin revisar; sigue sin revisar** |
| `V2__clientes_y_vinculo_vehiculos.sql` estaba escrito | **escrito y nunca ejecutado** |
| Suite al cierre de B2 | 259 pruebas Java + 14 pruebas Node |

A eso se sumaba el defecto propio de celdas que la especificación (`04`) ya tenía registrado y que este
bloque cerró: `asignarCeldaDisponible` hacía lectura-then-escritura sin bloqueo (dos ingresos podían tomar la
misma celda), `liberarCelda` fijaba `DISPONIBLE` sin condiciones y `cambiarEstado` aceptaba cualquier estado,
incluido `OCUPADA` puesto a mano.

---

## 2. Reglas de negocio y permisos aplicados

| Regla del encargo | Cómo quedó en el código |
|---|---|
| Conservar `DISPONIBLE` / `OCUPADA` / `MANTENIMIENTO` | `EstadoCelda` sin cambios; **no se añadió `INACTIVA`** |
| Baja lógica con columna, no con un cuarto estado | `celda.activo BOOLEAN NOT NULL DEFAULT TRUE` (entidad `Celda`, migración `V3`) |
| Celda asignable = activa + `DISPONIBLE` + sin registro `ACTIVO` | `CeldaService.estaAsignable` y las consultas `listarAsignables*` / `contarAsignables` |
| Datos administrables: código, piso, tipo, observaciones | `CeldaCrearDTO` / `CeldaActualizarDTO` (solo esos cuatro campos) |
| Sin tarifas, reservas ni asignación fija | no aparecen en DTO, entidad, service ni pantalla |
| `ADMINISTRADOR` crea, edita, desactiva, reactiva y mueve mantenimiento | `@PreAuthorize("hasRole('ADMINISTRADOR')")` en POST, PUT, `PATCH /{id}/activo`, `PUT /{id}/estado` |
| `OPERADOR` solo consulta y usa las celdas mediante entrada/salida | GET abiertos a ambos roles; escrituras cerradas con `403` |
| Sin borrado físico de celdas | no existe `DELETE` de celdas en ningún controlador |
| Desactivar se rechaza si hay ocupación | `MENSAJE_CELDA_OCUPADA_NO_DESACTIVABLE` → `409` |
| Desactivar se permite con historial cerrado y **conserva** el estado operativo | `cambiarActivo` solo escribe `activo`; no toca `estado` |
| Reactivar no quita un mantenimiento pendiente | el mismo método: reactivar deja `MANTENIMIENTO` intacto |
| Con la celda ocupada no se cambia código, piso ni tipo; observaciones sí | `cambiaIdentidadFisica` + `MENSAJE_CELDA_OCUPADA_NO_EDITABLE` |
| Solo `DISPONIBLE ↔ MANTENIMIENTO` a mano, en celda activa y libre | `cambiarEstado` (orden de comprobaciones en §4.3) |
| `OCUPADA` nunca desde un endpoint manual | primer `if` de `cambiarEstado`: rechaza el destino `OCUPADA` antes de leer la base |
| La salida/cancelación libera **solo** su propia celda | `liberarCelda(celdaId, registroPropioId)` |
| Repetir la operación no duplica liberaciones ni comprobantes | el segundo intento no encuentra registro `ACTIVO` y la liberación no vuelve a ejecutarse |
| Incongruencia → conflicto controlado, nunca reparación silenciosa | `exigirCoherencia` y `MENSAJE_CELDA_EN_OTRO_REGISTRO`; ningún camino escribe un estado «arreglado» |
| Sin cambios parciales | una sola `@Transactional` por operación; la auditoría se enrolla en ella (§9) |

---

## 3. Las dos dimensiones de la celda

```
                 estado (operativo)                     activo (lógico)
              DISPONIBLE | OCUPADA | MANTENIMIENTO       true | false
                       \                 /                   |
                          disponible (derivado, nunca escrito)
     = activo == true  y  estado == DISPONIBLE  y  no existe registro ACTIVO sobre la celda
```

* `estado` dice **qué pasa físicamente** en la celda. `OCUPADA` solo la produce una entrada válida.
* `activo` dice **si la celda sigue prestando servicio**. Es la baja lógica; no compite con `estado`.
* `disponible` es un campo **calculado** en `CeldaDTO.aDTO()`. La interfaz lo usa para el indicador y para
  ocultar acciones inválidas; la base no lo guarda y nadie puede escribirlo.
* «Inactiva» en la pantalla es una **etiqueta derivada** de `activo = false`, no un valor persistido.

Invariante entre las dos dimensiones, fijada también en la base por `V3`:
`chk_celda_inactiva_no_ocupada CHECK (activo = TRUE OR estado <> 'OCUPADA')`.

`contarAsignables()` es la única definición de «disponible» que usa el dashboard. `countByEstado(DISPONIBLE)`
no sirve: una celda inactiva o con un registro `ACTIVO` puede conservar el estado `DISPONIBLE` y aun así no
admitir un ingreso.

---

## 4. Endpoints reales

Ruta base `/api/v1/celdas`. Ninguna ruta nueva sustituye un contrato heredado: `GET /celdas` sigue devolviendo
**array** porque la operación ya lo consumía; la pantalla de administración usa `GET /celdas/pagina`, que es
`Page`. No se cambió ningún contrato de `entrada`, `salida`, `dashboard`, `registros` ni `comprobantes`.

### 4.1 Consultas — `ADMINISTRADOR` y `OPERADOR`

| Método y ruta | Parámetros | Respuestas |
|---|---|---|
| `GET /celdas` | `estado`, `tipo`, `piso` (opcionales) | `200` array de `CeldaDTO` · `401` sin token · `403` sin rol |
| `GET /celdas/pagina` | `criterio` (por código), `estado`, `tipo`, `piso`, `activo`, `page` (0), `size` (20, tope 100) | `200` `Page<CeldaDTO>` ordenado por `numero` ascendente |
| `GET /celdas/disponibles` | `tipo` (opcional) | `200` array con **solo asignables** (activa + `DISPONIBLE` + sin registro `ACTIVO`) |
| `GET /celdas/{id}` | — | `200` `CeldaDTO` · `404` `Celda no encontrada con ID: n` |

### 4.2 Escrituras — solo `ADMINISTRADOR`

| Método y ruta | Cuerpo | Éxito | Errores |
|---|---|---|---|
| `POST /celdas` | `{numero ≤10, piso 1-20, tipoVehiculo, observaciones ≤255}` | `201` (nace `activo=true`, `DISPONIBLE`) | `400` validación · `401` · `403` · `409` código duplicado o restricción `uk_celda_numero` |
| `PUT /celdas/{id}` | ficha completa (mismos límites) | `200` | `400` · `401` · `403` · `404` · `409` ocupada con identidad física cambiada, código ajeno, o inconsistencia |
| `PATCH /celdas/{id}/activo` | `{activo: true\|false}` | `200` | `400` · `401` · `403` · `404` · `409` desactivar con vehículo adentro |
| `PUT /celdas/{id}/estado` | `{estado: DISPONIBLE\|MANTENIMIENTO}` | `200` | `400` (JSON ilegible o enum inválido) · `401` · `403` · `404` · `409` `OCUPADA` manual, celda inactiva, transición no permitida o inconsistencia |

No hay `DELETE`. `PATCH /{id}/activo` **no** es una vía alternativa para ocupar o liberar: es estado lógico y
el servicio lo trata como tal.

### 4.3 Orden de comprobaciones en `cambiarEstado`

Importa porque decide qué mensaje ve el administrador:

1. ¿El destino es `OCUPADA`? → `409 MENSAJE_OCUPADA_NO_ASIGNABLE_MANUAL` (**antes** de tocar la base).
2. Bloquear la fila (`buscarConBloqueo`); si no existe → `404`.
3. ¿Está inactiva? → `409 MENSAJE_CELDA_INACTIVA`.
4. ¿`estado` y sus registros `ACTIVO` son coherentes? → `409 MENSAJE_INCONSISTENCIA`.
5. ¿El estado pedido ya es el vigente? → devuelve la celda **sin escribir ni auditar** (idempotencia).
6. ¿El estado actual permite la transición? → `409 MENSAJE_TRANSICION_NO_PERMITIDA`.
7. Escribir, auditar y responder `200`.

---

## 5. Estrategia de concurrencia e integridad

**Una sola estrategia transaccional para todo el bloque:** cada escritura de celda abre una transacción,
adquiere un **bloqueo pesimista de escritura sobre la fila** y **revalida las reglas sobre la fila ya
bloqueada**. No se mezcló con `@Version`: el encargo lo advertía y la columna de versión no aporta nada que el
bloqueo y la revalidación no cubran.

| Elemento | Implementación |
|---|---|
| Bloqueo de celda | `CeldaRepository.buscarConBloqueo(id)` — `@Lock(PESSIMISTIC_WRITE)` + JPQL |
| Bloqueo de vehículo | `VehiculoRepository.buscarConBloqueoPorPlaca(placa)` — añadido en este bloque |
| **Orden de bloqueo (documentado)** | **vehículo → celda**. `registrarEntrada` bloquea la fila del vehículo y después, dentro de `asignarCeldaDisponible`, la de la celda (candidatos ordenados `numero, id`). Ninguna otra ruta bloquea al revés, así que dos ingresos no se entorpan en un ciclo |
| Revalidación tras el bloqueo | la lista de candidatos se lee **sin** bloqueo; al adquirirlo se comprueban otra vez las tres condiciones (`activo`, `DISPONIBLE`, sin registro `ACTIVO`) |
| Reintento acotado | `MAXIMO_CANDIDATOS_POR_ASIGNACION = 5`: si el primer candidato ya fue ocupado por otro hilo, se pasa al siguiente, con tope. Nunca es indefinido |
| Respuesta al perdedor | `409` con mensaje de negocio («No hay celdas disponibles…», el conflicto concreto o `MENSAJE_BLOQUEO_CELDA` si el propio bloqueo se agotó). Sin SQL, sin stack, sin nombres de restricción |
| Escritura tras un fallo | la transacción revierte entera; el servicio no escribe la celda después de rechazar el registro, así que no quedan ocupaciones huérfanas |
| Liberación | `liberarCelda(celdaId, registroPropioId)` — si queda un registro `ACTIVO` de **otro** vehículo sobre esa celda, `409 MENSAJE_CELDA_EN_OTRO_REGISTRO`; si el estado no era `OCUPADA`, `409 MENSAJE_INCONSISTENCIA`. No se «arregla» nada |

El `@Version` heredado de otras entidades no se tocó; el bloqueo pesimista es la única mecánica nueva.

---

## 6. Migración `V3__gestion_celdas_e_integridad_ocupacion.sql`

**Escrita y revisada en solo lectura. No se ejecutó sobre ninguna base persistente.** Con V1 y V2 presentes y
sin aplicar, el archivo nuevo es V3; V1 y V2 quedaron intactos byte a byte y se conservaron los nombres de
restricción existentes.

Es estrictamente aditiva:

| # | Contenido |
|---|---|
| 1 | `DO $v3_pre$` con **cuatro comprobaciones previas** que abortan la migración (§6.1) |
| 2 | `ALTER TABLE celda ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE` + `COMMENT ON COLUMN` de `activo` y `estado`; `CREATE INDEX idx_celda_activo` |
| 3 | `CREATE UNIQUE INDEX uk_registro_celda_activo ON registro (celda_id) WHERE estado = 'ACTIVO'` · `CREATE UNIQUE INDEX uk_registro_vehiculo_activo ON registro (vehiculo_id) WHERE estado = 'ACTIVO'` · `ALTER TABLE celda ADD CONSTRAINT chk_celda_inactiva_no_ocupada CHECK (activo = TRUE OR estado <> 'OCUPADA')` · `CREATE INDEX idx_celda_numero_lower ON celda (lower(numero))` |

### 6.1 Comprobaciones previas

Cada una falla con un mensaje **accionable** que dice qué consulta correr para ver las filas involucradas.
Ninguna elige qué fila conservar, ninguna borra, ninguna repara estados:

| Anomalía detectada | Qué impide |
|---|---|
| Una celda con más de un registro `ACTIVO` | crear `uk_registro_celda_activo` |
| Un vehículo con más de un registro `ACTIVO` | crear `uk_registro_vehiculo_activo` |
| Registro `ACTIVO` sobre celda que no está `OCUPADA` | dejar el inventario incoherente con la invariante |
| Celda `OCUPADA` sin ningún registro `ACTIVO` | que luego choque con `chk_celda_inactiva_no_ocupada` al desactivarla |

Como el `DO` y las sentencias van en la misma transacción de Flyway, un `RAISE EXCEPTION` revierte todo: la
base queda como estaba y sin índices a medias.

### 6.2 Límites de estos índices

* Son **parciales**: solo afectan a las filas `ACTIVO`. El historial (`CERRADO`, `CANCELADO`) sigue conviviendo
  sin límite, que es lo que se quiere.
* La condición está sobre el **estado del registro**, no sobre la celda, tal como pedía el encargo.
* `idx_celda_numero_lower` acelera el filtro de la pantalla; la unicidad la sigue dando `uk_celda_numero` de V1,
  y el servicio normaliza con `normalizarCodigo` (recortar y `toUpperCase(Locale.ROOT)`) para que ambas capas
  comparen la misma forma del texto.
* **Ni V2 ni V3 se interpretan en las pruebas**: el perfil `test` desactiva Flyway y crea el esquema con
  Hibernate sobre H2 (§12).

---

## 7. Integración con la operación existente

| Flujo | Qué se revisó o adaptó |
|---|---|
| **Entrada** (`POST /registros/entrada`) | pico y placa → **bloqueo de la fila del vehículo** → revalidación de que no haya un registro `ACTIVO` para esa placa → rechazo si el **cliente titular está inactivo** (usa la fila ya bloqueada, sin segunda lectura) → `asignarCeldaDisponible(tipo)` → `OCUPADA` como consecuencia de la entrada → registro `ACTIVO` → comprobante → auditoría |
| **Salida** (`PUT /registros/salida/{placa}`) | cierra el propio registro y llama `liberarCelda(celdaId, registroId)`. Libera **únicamente** esa celda; una celda de otra placa o en `MANTENIMIENTO` queda intacta |
| **Cancelación** | misma liberación acotada; **los permisos de cancelación no cambiaron** |
| **Repetición** | el segundo intento no encuentra registro `ACTIVO`: no hay segunda liberación ni segundo comprobante |
| **Disponibles** (`GET /celdas/disponibles`) | excluye inactivas y en mantenimiento; es la misma consulta que usa el algoritmo de asignación |
| **Dashboard** (`GET /reportes/dashboard`) | tres contadores nuevos, sin tocar los existentes |
| **Pico y placa** | regla intacta (sigue siendo la de Bogotá D.C., limitación preexistente) |
| **Vehículo histórico sin cliente** | la excepción de B2 se conserva: un vehículo sin titular sigue pudiendo entrar y salir |
| **Cliente inactivo** | su vehículo **no** puede nuevo ingreso; **sí** puede salir, y al salir la celda vuelve a ser asignable |

### 7.1 Qué significa cada contador del panel

`DashboardDTO` conservó `celdasOcupadas`, `celdasTotal`, `porcentajeOcupacion`, `vehiculosHoy`,
`novedadesPendientes` y `ultimosIngresos`; se añadieron tres:

| Campo | Definición exacta |
|---|---|
| `celdasTotal` | **filas del inventario**, incluidas las dadas de baja lógica |
| `celdasActivas` | `activo = true`: las que hoy prestan servicio |
| `celdasDisponibles` | `contarAsignables()` — realmente asignables: activas, `DISPONIBLE` y sin registro `ACTIVO` |
| `celdasOcupadas` | estado operativo `OCUPADA` |
| `celdasMantenimiento` | estado operativo `MANTENIMIENTO` (siempre activas: el mantenimiento no se aplica a una inactiva) |
| `porcentajeOcupacion` | `celdasOcupadas / celdasTotal`; se conserva el denominado histórico aunque la cifra «de verdad asignable» sea otra |

La tarjeta de capacidad aclara en la propia pantalla que `celdasTotal` es inventario y que «disponibles» son
las listas para asignar, no el resultado de restar.

---

## 8. Auditoría

| Operación | Acción en `auditoria_log` |
|---|---|
| Alta de celda | `CELDA_CREAR` |
| Edición | `CELDA_EDITAR` |
| Inicio o fin de mantenimiento | `CELDA_ESTADO` |
| Desactivación / reactivación | `CELDA_DESACTIVAR` / `CELDA_ACTIVAR` |
| Entrada, salida, cancelación | `REGISTRO_ENTRADA`, `REGISTRO_SALIDA`, `REGISTRO_CANCELAR` (heredadas, sin cambios) |

Se reutilizó el `AuditoriaService` existente. Es `@Transactional` de propagación `REQUIRED`, así que el evento
**vive o muere con la operación de negocio**: una escritura rechazada o revestida no deja auditoría
(case20b). Un no-op (estado o `activo` ya vigente) no escribe y **no audita**, para no producir eventos
duplicados. El detalle es descriptivo (`Alta de celda A-01`, `Desactivación de la celda A-05 (estado operativo
DISPONIBLE)`): no se guarda el cuerpo de la petición ni datos personales.

---

## 9. Interfaz: la vista «Celdas»

Se añadió con el stack actual (HTML, CSS y JS vanilla del proyecto; sin librerías, sin framework, sin
capturas que sustituyan los mockups de Isabella).

**Consultables por cualquier rol autenticado:** buscador por código, cuatro selectores (estado lógico, estado
operativo, tipo, piso), botón *Buscar*, tabla con código, vehículo al que sirve, piso, tipo, estado operativo,
indicador activo/inactivo, observaciones y acciones; paginación Anteriores/Siguientes con «N de total ·
página x de y»; estados de *Cargando…*, vacío («No hay celdas con esos criterios»), error de carga en línea y
en *toast*.

**Solo para `ADMINISTRADOR`** (el backend lo vuelve a exigir; ocultar el botón no concede permiso): *＋ Nueva
celda*, *Editar*, *Desactivar*/*Reactivar* y *Mantenimiento*/*Liberar de mantenimiento*. El control
`data-admin-only` se oculta en `showApplication()` según `currentUser.tipo`.

**Lo que la pantalla no ofrece, a propósito:**

* Ningún control para escribir `OCUPADA` ni para «liberar» una celda a mano: la ocupación llega con la entrada
  y se va con la salida.
* Al editar una celda ocupada, código, piso y tipo se deshabilitan y las observaciones quedan disponibles; si
  el backend igualmente rechaza, se muestra su mensaje.
* Desactivar y el cambio de estado piden **segundo clic de confirmación** en la misma fila; el `busy` de
  `cellState` impide que una doble entrega repita la solicitud.
* Los textos del usuario se renderizan con `escapeHtml()`; nunca se inyectan como HTML.

---

## 10. Trazabilidad de las 21 pruebas pedidas

Los 21 casos del encargo están cubiertos por `CeldasIntegridadIntegrationTest` (33 métodos, con variantes `b`
cuando un caso pedía comprobar dos consecuencias distintas), `CeldaServiceTest` (28 pruebas unitarias) y
`CeldaControllerTest` (10).

| Caso del encargo | Prueba |
|---|---|
| 1 alta válida | `case01_crearCeldaValidaResponde201NaceActivaYDisponible` |
| 2 código duplicado | `case02_crearCeldaConCodigoDuplicadoDevuelve409YNoDejaFila` |
| 3 campos inválidos | `case03_camposInvalidosDevuelven400YSinEscritura` |
| 4 edición libre | `case04_editarCeldaLibreCambiaCodigoPisoYTipo` |
| 5 edición con ocupación | `case05_…RechazaCambiarIdentidadFisica`, `case05b_…PermiteObservacionesYNoLiberaLaCelda`, `case05c_editarConCodigoDeOtraCeldaDevuelve409` |
| 6 desactivar con historial cerrado | `case06_desactivarCeldaConHistorialCerradoConservaElEstadoOperativo` |
| 7 desactivar ocupada | `case07_desactivarCeldaOcupadaSeRechazaPor409` |
| 8 reactivar mantiene mantenimiento | `case08_reactivarNoQuitaElMantenimientoPendiente` |
| 9 mantenimiento solo activa y libre | `case09_mantenimientoSoloConCeldaActivaYSinOcupacion` |
| 10 `OCUPADA` manual rechazada | `case10_endpointManualDeEstadoRechazaOCUPADASinTocarLaBase` |
| 11 operador sin permisos de administración | `case11_operadorRecibe403EnTodaLaAdministracionDeCeldas`, `case11b_sinTokenODatosInvalidos…` |
| 12 operador consulta y usa celdas | `case12_operadorConsultaYPuedeUsarLasCeldasEnEntradaYSalida` |
| 13 entrada ocupa celda asignable | `case13_laEntradaOcupoUnaCeldaAssignableYDejaUnRegistroActivo` |
| 14 no asigna inactivas ni mantenimiento | `case14_laEntradaNoUsaCeldasInactivasNiEnMantenimiento`, `case14b_entradaUsaLaActivaYDejaLaInactivaIntacta` |
| 15 salida libera solo su celda | `case15_laSalidaLiberaUnicamenteLaCeldaDelRegistro` |
| 16 cancelación | `case16_cancelacionDejaCeldaLibreRegistroCanceladoYSinComprobanteDeSalida` |
| 17 repetir no duplica | `case17_repetirLaSalidaNoGeneraLiberacionNIComprobanteDuplicado`, `case17b_repetirDesactivacionYNinguneoDeEstadoNoVuelvenAEscribirNiAuditar` |
| 18 conflicto/inconsistencia sin escritura parcial | `case18_conflictoDeCeldaCompartidaRevierteTodoYNoDejaRegistroParcial`, `case18b_celdaOcupadaSinRegistroActivoSeRechazaComoInconsistencia` |
| 19 filtros y paginación | `case19_filtrosYPaginacionDeLaListaAdministrativa`, `case19b_elArrayHeredadoSeConservaYDisponiblesExcluyeInactivasYMantenimiento` |
| 20 auditoría sin datos personales | `case20_lasCuatroOperacionseAdministrativasDejanAuditoriaSinDatosPersonales`, `case20b_lasOperacionesFallidasDeCeldaNoDejanAuditoria` |
| 21 regresión de clientes/cuentas y celdas | `case21_regresionDeClientesYDeCuentasDelPersonalConCeldasActivas`, `case21b_salidaDeVehiculoDeClienteInactivoSiguePermitidaYPuedoVolverseaOcuparLaCelda` |
| (extra) contadores del panel | `elDashboardDistingueTotalActivasDisponiblesOcupadasYMantenimiento` |
| (extra) concurrencia | `dosIngresosConcurrentesPorUnaUnicaCeldaDejanUnSoloRegistroActivo`, `elMismoVehiculoIngresadoALaVezNoQuedaConDosRegistrosActivos` |

### 10.1 Pruebas de concurrencia

Ambas corren con dos hilos, transacciones independientes del servicio, `CountDownLatch` para sincronizar la
salida y **tope de espera** (`Future.get` con tiempo límite), de modo que un bloqueo indefinido falle la prueba
en vez de colgar la suite:

1. **Dos ingresos compitiendo por la única celda asignable** → exactamente `1` registro `ACTIVO`, `0` celdas
   con dos registros activos, `1` celda `OCUPADA`, y el perdedor recibe `BusinessRuleException`. Se comprueba
   además que el perdedor **no dejó escrito su vehículo** (`vehiculoRepository.count() == 1`) y que no quedó
   ninguna celda asignable.
2. **El mismo vehículo ingresado a la vez** → el vehículo se precrea para que el bloqueo por placa pueda
   serializar (un `INSERT` concurrente sobre una fila inexistente no se bloquea a sí mismo). Resultado: un
   solo registro `ACTIVO` y el segundo con conflicto controlado.

### 10.2 Pruebas antiguas ajustadas (conservando su intención)

| Prueba | Ajuste | Intención que se conserva |
|---|---|---|
| `RegistroServiceTest` (salida/cancelación) | ahora verifica `liberarCelda(1L, 1L)` en lugar de `liberarCelda(1L)` | sigue comprobando que la salida libera la celda del registro; ahora además que lo hace **en nombre de ese registro** |
| `CeldaServiceTest` | reescrito como 28 pruebas unitarias con el repositorio bloqueante, la revalidación, el reintento acotado, la coherencia y la idempotencia | cada regla del apartado A tiene su propia prueba de unidad |
| `CeldaControllerTest` | reescrito como 10 pruebas; el `UserDetails` anónimo se sustituyó por `User.withUsername(…).build()` (el anterior no compilaba tras añadir la autoridad de rol) | el mapeo de las rutas y sus códigos sigue verificado sin base de datos |

Ninguna prueba se eliminó ni se marcó como ignorada. No se usó `-Dmaven.test.failure.ignore`.

---

## 11. Resultados reales de las ejecuciones de esta sesión

| Comando | Resultado |
|---|---|
| `mvn -o -B clean verify` | **BUILD SUCCESS**, exit 0. `Tests run: 314, Failures: 0, Errors: 0, Skipped: 0` (tiempo total 1:32 min). Jar generado: `target/autos-colombia-1.0.0-iteracion1.jar` |
| `node --test src/test/js/app.test.js` | **30 pruebas, 30 pasan, 0 fallan** (duración 243 ms). Ejecutado otra vez al cerrar esta bitácora, mismo resultado |

Clases relevantes de la corrida de Maven (todas verdes):

| Clase | Pruebas |
|---|---|
| `integration.CeldasIntegridadIntegrationTest` | 33 |
| `service.CeldaServiceTest` | 28 |
| `presentation.controllers.CeldaControllerTest` | 10 |
| `integration.ClientesVehiculosIntegrationTest` | 26 |
| `integration.CuentasPersonalIntegrationTest` | 33 |
| `service.RegistroServiceTest` | 10 |
| `service.VehiculoServiceTest` | 13 |
| `service.ReporteServiceTest` | 5 |

De 259 pruebas Java y 14 Node con las que arrancó el bloque, se pasó a **314 Java y 30 Node**, sin perder
ninguna prueba previa.

**Lo que NO se ejecutó:** `mvn spring-boot:run`, ningún cliente HTTP contra la aplicación, ningún navegador,
ninguna sentencia contra PostgreSQL, ninguna migración (`flyway:migrate` ni `psql -f`), ni `baseline`,
`repair` o `clean`.

---

## 12. Verificado frente a pendiente en PostgreSQL

### 12.1 Datos de conexión confirmados por el usuario (2026-10-31, sin conectar)

| Dato | Valor confirmado |
|---|---|
| Host | `localhost` (la dirección `::1` es su versión IPv6: el propio equipo) |
| Puerto | `5432` |
| Nombre exacto de la base | `Parqueadero de autos en colombia` |
| Usuario | `postgres` |
| Datos que conservar | **no tiene** tablas de usuario ni filas registradas |
| Contraseña | **no se compartió y no se escribió en ningún archivo** |

Consecuencias ya registradas en `07` §9.1, que este bloque mantiene:

* El nombre **no coincide** con `Garajeautomovil` (`application.yml`) ni con `garajeautomovil`
  (`docker-compose.yml:8`). Además, `Parqueadero de autos en colombia` lleva espacios, así que como
  identificador hay que escribirlo **entre comillas dobles** en cualquier comando.
* El servicio de Windows instalado es PostgreSQL **18** (`postgresql-x64-18`); `docker-compose.yml:4` fija
  `postgres:16-alpine`. Son dos servidores distintos y sigue sin decidir cuál es el objetivo.
* Con la base vacía es **muy probable que V1 no esté aplicada** —es una inferencia por lo que dijo el
  usuario, no una comprobación.

### 12.2 Verificado (sobre H2 en modo PostgreSQL)

Unicidad de código, rechazo de edición ocupada, baja lógica con historial cerrado, no duplicación de
liberaciones, permisos por rol, orden de comprobaciones, revalidación bajo bloqueo, reintento acotado,
auditoría y su ausencia al revertir, contadores del panel y el contrato de array heredado. Las dos pruebas de
concurrencia (§10.1) demuestran el **comportamiento del servicio** con dos hilos reales sobre H2.

### 12.3 Pendiente (no se puede dar por cierto sin PostgreSQL)

1. **Los índices únicos parciales** `uk_registro_celda_activo` y `uk_registro_vehiculo_activo`: **H2 no tiene
   índices únicos parciales** y el perfil `test` ni siquiera corre Flyway, así que **no se ha interpretado ni
   una línea de V2 ni de V3**. Que el servicio rechace el doble registro **no** prueba la garantía de base.
2. `chk_celda_inactiva_no_ocupada` y las comprobaciones previas del bloque `DO $v3_pre$` (sintaxis PL/pgSQL).
3. Que `ddl-auto: validate` del perfil `dev` acepte `Celda.activo` y `Vehiculo.cliente` contra el esquema real.
4. La traducción PostgreSQL de los conflictos: `23505` sobre los índices parciales y `23503`/`23514`, y que
   lleguen como `409` con mensaje seguro.
5. El comportamiento del `SELECT … FOR UPDATE` de PostgreSQL bajo dos sesiones, con el bloqueo real de fila.
6. Rendimiento de la asignación bloqueante sobre el inventario real (RF-NF-07).

### 12.4 Autorización exacta que falta

> Ejecutar, sobre la base `"Parqueadero de autos en colombia"` del esquema `<esquema>` en `localhost:5432`,
> por el medio `<psql | Flyway | pgAdmin>` y en este orden: `V1__schema_inicial.sql`,
> `V2__clientes_y_vinculo_vehiculos.sql` y `V3__gestion_celdas_e_integridad_ocupacion.sql`,
> después de `<respaldo / ninguno>` y confirmado `<servidor 18 | contenedor postgres:16>`.

Como la base no tiene datos que conservar, no hace falta respaldo; **aun así no se ejecutó nada** porque el
encargo lo prohibe sin autorización expresa. Después de eso, los pasos de verificación son los de `07` §9.2
más las cuatro comprobaciones nuevas de §12.3 (1, 2, 4 y 5).

### 12.5 Lista única de datos pendientes

* Esquema objetivo (`public` u otro) y verificación de que la base esté vacía.
* `flyway_schema_history`: qué hay aplicado y si el checksum de V1 coincide.
* Servidor elegido: 18 instalado o `postgres:16-alpine` de Compose.
* Mecanismo de migración: `psql` (no está en el `PATH`), Flyway del proyecto o pgAdmin 4.
* Contraseña de `postgres` — la aporta el usuario en su propio medio; **no se pide por chat ni se escribe**.

---

## 13. Recorridos manuales pendientes en navegador

**Ninguno se ejecutó.** No hay captura, clic ni revisión visual que afirmar. Cuando se autorice arrancar la
aplicación contra una base conocida, estos son los recorridos concretos:

1. **Panel** — entrar como `ADMINISTRADOR`; confirmar que *Capacidad del patio* muestre ocupadas sobre
   inventario y que el pie aclare que «disponibles» son las asignables. Comparar con `GET /reportes/dashboard`.
2. **Celdas como operador** — la vista lista y filtra; **no** aparece *＋ Nueva celda* ni los botones de fila
   administrativos. Probar a forzar `POST /celdas` con el token del operador → `403`.
3. **Alta** — crear `Z-99` piso 1 tipo CARRO; verificar `201`, chip «Activo» y estado «Disponible». Repetir el
   código → el mensaje de conflicto dentro del formulario, que **no** se cierra.
4. **Edición con ocupación** — registrar la entrada de un vehículo que ocupe `Z-99`; abrir Editar: código, piso
   y tipo deshabilitados; guardar solo observaciones → `200`; comprobar que la celda sigue `OCUPADA`.
5. **Desactivar ocupada** — el botón pide confirmación; al confirmar, el `409` del backend llega como *toast* y
   la fila no queda en modo confirmar.
6. **Salida y liberación** — cerrar la entrada de `Z-99`; verificar que solo esa celda quedó `DISPONIBLE`, que
   una en mantenimiento no cambió y que el panel sumó un disponible.
7. **Mantenimiento** — poner y quitar mantenimiento sobre una celda libre; en una inactiva debe rechazarlo.
8. **Estados de la tabla** — cargar, vacío (buscar `ZZZ`), error (cortar la red o usar un token caducado) y
   paginación con más de 20 celdas.
9. **Seguridad de renderizado** — crear una celda con observaciones `<img src=x onerror=alert(1)>`; debe verse
   como texto.
10. **Clientes (B2, arrastrado)** — mismo recorrido pendiente de la pantalla de clientes, que sigue sin
    revisarse en navegador.

---

## 14. Insumos para los diagramas de StarUML

Los entregables se producen en **StarUML**; lo siguiente es materia prima, no un diagrama que los reemplacce.

### 14.1 Secuencia: registrar entrada (con concurrencia)

`Operador → CeldaController/RegistroController → RegistroService → PicoYPlacaService → VehiculoRepository
(bloqueo por placa) → RegistroRepository (¿hay ACTIVO?) → ClienteService (¿titular permite ingreso?) →
CeldaService.asignarCeldaDisponible → CeldaRepository.listarAsignablesPorTipo → CeldaRepository
.buscarConBloqueo → (guardar celda OCUPADA) → RegistroRepository.save → ComprobanteService →
AuditoriaService → vista`.
Alternativas: pico y placa vigente (rechazo), vehículo con registro `ACTIVO` (`409`), titular inactivo (`409`),
candidato ya ocupado al bloquear (reintentar siguiente, tope 5), sin candidatos (`409` y revierte todo).
Nota para el diagrama: **el orden de bloqueo es vehículo → celda**, y el reintento es acotado.

### 14.2 Secuencia: cerrar una estancia

`Operador → RegistroController → RegistroService.registrarSalida → bloqueo del registro → cerrar registro →
CeldaService.liberarCelda(celdaId, registroId) → bloqueo de celda → ¿registro ACTIVO de otro? → ¿estado
OCUPADA? → DISPONIBLE → comprobante de salida → auditoría`.
Alternativas: inconsistencia (`409`, no repara), celda compartida (`409`), repetición (sin segunda liberación).

### 14.3 Secuencia: administración de celdas

Tres flujos cortos: `crear` (unicidad, nace activa y disponible), `actualizar` (rechazo de identidad física si
está ocupada), `cambiarActivo` y `cambiarEstado` (orden de las siete comprobaciones de §4.3, con el rechazo
explícito de `OCUPADA`).

### 14.4 Componentes

`presentation.controllers` (Celda, Registro, Reporte, Cliente, Vehículo, Usuario) → `domain.service`
(CeldaService **nuevo contrato con el registro**, RegistroService, ReporteService, ClienteService,
AuditoriaService, PicoYPlacaService) → `infrastructure.repository` (CeldaRepository con bloqueo pesimista,
VehiculoRepository con bloqueo por placa, RegistroRepository con las consultas de anomalías) →
`db/migration` (V1 → V2 → V3). Dependencia cruzada a dibujar: **`CeldaService` consulta `RegistroRepository`**
para decidir asignabilidad, coherencia y liberación; es la relación que antes no existía.

### 14.5 Diagrama de estados de la celda (dos dimensiones)

Estado operativo: `DISPONIBLE → OCUPADA` (solo por entrada), `OCUPADA → DISPONIBLE` (solo por salida o
cancelación), `DISPONIBLE ↔ MANTENIMIENTO` (manual, administrador). Transición **prohibida**: ninguna entrada
manual a `OCUPADA`. Sobre esas tres, un orthogonal `activo: true ↔ false` que no cambia `estado`, rechaza
desactivar con vehículo adentro y no borra la fila.

---

## 15. Archivos tocados en este bloque

| Área | Archivo |
|---|---|
| Modelo | `domain/model/Celda.java` (campo `activo`) · `domain/enums/EstadoCelda.java` (sin cambios) |
| Persistencia | `infrastructure/repository/CeldaRepository.java` (bloqueo, asignables, conteos, búsqueda con 5 filtros) · `infrastructure/repository/VehiculoRepository.java` (`buscarConBloqueoPorPlaca`) · `infrastructure/repository/RegistroRepository.java` (consultas de anomalías) |
| Dominio | `domain/service/CeldaService.java` (reglas, locks, coherencia, auditoría) · `domain/service/RegistroService.java` (orden de bloqueo y revalidación) · `domain/service/ReporteService.java` (contadores) |
| API | `presentation/controllers/CeldaController.java` · `presentation/dto/CeldaDTO.java`, `CeldaCrearDTO`, `CeldaActualizarDTO`, `CambiarActivoCeldaDTO`, `CambiarEstadoCeldaDTO`, `DashboardDTO` |
| Migración | `src/main/resources/db/migration/V3__gestion_celdas_e_integridad_ocupacion.sql` (nuevo, **sin ejecutar**) |
| Frontend | `static/index.html` (vista `celdas`) · `static/app.js` (bloque B3) · `static/styles.css` (chips y formulario de celdas) |
| Pruebas | `test/java/com/autoscolombia/integration/CeldasIntegridadIntegrationTest.java` (nuevo) · `service/CeldaServiceTest.java` · `presentation/controllers/CeldaControllerTest.java` · `service/RegistroServiceTest.java` · `test/js/app.test.js` (16 pruebas nuevas) |
| Docs | este archivo y la trazabilidad afectada en `04_ESPECIFICACION_IMPLEMENTACION.md`; conexión PostgreSQL anotada en `07` §9.1 |

Sin commits, sin push, sin cambios de workflow, sin dependencias nuevas y sin tocar la carpeta original de la
Iteración 1.

---

## 16. Limitaciones de este bloque

1. **Todo se verificó sobre H2 en modo PostgreSQL.** El perfil `test` desactiva Flyway: V2 y V3 **no se
   interpretaron**. La garantía de los índices únicos parciales y de `chk_celda_inactiva_no_ocupada` sigue
   **sin probar**; los 314 verdes no la respaldan.
2. **La concurrencia probada es la del servicio**, con dos hilos sobre H2. `SELECT … FOR UPDATE` de H2 no es
   el de PostgreSQL, y los bloqueos entre sesiones reales no se ejercitaron.
3. **Nada revisado en navegador** (§13), incluida la pantalla de clientes que venía arrastrada de B2.
4. **La aplicación nunca arrancó** contra ninguna base, así que `ddl-auto: validate` no se comprobó.
5. El orden de bloqueo **vehículo → celda** solo aplica a `registrarEntrada`; si otra ruta nueva bloquea al
   revés, la prueba de deadlock deja de valer.
6. El reintento de asignación toma los primeros 5 candidatos por `numero`; en un patio muy grande siempre se
   intenta la misma cabecera del inventario.
7. Pico y Placa sigue codificado para Bogotá D.C. sin festivos (limitación preexistente, fuera de B3).
8. El bloqueo pesimista se probó funcionalmente, no en rendimiento (RF-NF-07 sigue sin medir).
9. Los mockups de Isabella no se sustituyeron: la pantalla se hizo con el HTML/CSS actual y su revisión visual
   formal queda pendiente.
