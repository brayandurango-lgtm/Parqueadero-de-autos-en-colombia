# 05 — Bloque B0: validación de documento y manejo de conflictos de integridad

**Proyecto:** Sistema de información del parqueadero Autos Colombia
**Iteración:** 2 — bloque B0 del orden de `04_ESPECIFICACION_IMPLEMENTACION.md` §14
**Fecha de la ejecución:** viernes 30 de octubre de 2026. Las corridas completas de cierre quedaron registradas
entre 21:15 y 21:24 (hora local `-05:00`); las pruebas dirigidas, incluida la roja de §5.1, se ejecutaron justo
antes. Que sea **viernes** importa: es la condición que usaba la línea base para el defecto de calendario, así que
la suite volvió a pasar un viernes con la regla de pico y placa activa.
**Alcance real de este documento:** dos validaciones y un manejador de excepciones. **No** es la gestión de
usuarios terminada: no hay clientes, no hay bajas lógicas de celda, no hay migración `V2`, no hay contraseña
inicial. Ver §9.

---

## 1. Defectos corregidos

| # | Defecto | Dónde estaba | Consecuencia antes de B0 |
|---|---|---|---|
| **D-1** | `UsuarioService.actualizar` validaba la unicidad de `correo` pero **no** la de `documento`. | [UsuarioService.java:61-82](file:///C:/Users/Administrador/Downloads/Parqueadero%20de%20autos%20en%20colombia/src/main/java/com/autoscolombia/domain/service/UsuarioService.java#L61-L82) (versión anterior) | Un `PUT /api/v1/usuarios/{id}` con el documento de otra cuenta llegaba hasta `save()` y fallaba en la base (`uk_usuario_documento`), no en la regla de negocio. |
| **D-2** | `GlobalExceptionHandler` no tenía handler de `DataIntegrityViolationException`. | `infrastructure/exception/GlobalExceptionHandler.java` (7 manejadores, ninguno de integridad) | Cualquier conflicto de unicidad o de clave foránea se respondía **500** por el handler genérico, con mensaje inútil y sin el `409` que la especificación (RF-ACC-05, §7) da por supuesto. |
| **D-3** | No existía consulta por documento en el repositorio. | `UsuarioRepository` solo tenía `findByCorreo`; `crear()` comparaba contra `findAll()`. | Sin consulta disponible, la única forma de validar en `actualizar` habría sido cargar la tabla entera, como hace `crear()`. |

D-1 y D-2 son la pareja que hace que el sistema **no pueda responder 409** a un duplicado detectado por la
base, que es precisamente lo que B1–B5 van a necesitar (clientes con documento único, celdas con ocupación
única). D-3 es el pre-requisito mecánico.

---

## 2. Archivos modificados y creados

### Código de producción (`src/main`)

| Archivo | Cambio | Líneas |
|---|---|---|
| [domain/service/UsuarioService.java](file:///C:/Users/Administrador/Downloads/Parqueadero%20de%20autos%20en%20colombia/src/main/java/com/autoscolombia/domain/service/UsuarioService.java#L71-L76) | Bloque de validación de documento en `actualizar`, después del de correo y antes de modificar el entity. | +6 |
| [infrastructure/exception/GlobalExceptionHandler.java](file:///C:/Users/Administrador/Downloads/Parqueadero%20de%20autos%20en%20colombia/src/main/java/com/autoscolombia/infrastructure/exception/GlobalExceptionHandler.java#L113-L166) | `@ExceptionHandler(DataIntegrityViolationException.class)` → `409` + catálogo de restricciones conocidas + 3 helpers privados. | +62 |
| [infrastructure/repository/UsuarioRepository.java](file:///C:/Users/Administrador/Downloads/Parqueadero%20de%20autos%20en%20colombia/src/main/java/com/autoscolombia/infrastructure/repository/UsuarioRepository.java#L18-L20) | `Optional<Usuario> findByDocumento(String)`. | +5 |

Total producción: **73 líneas añadidas, 0 eliminadas.** No se tocó ningún otro método, ninguna firma pública
de API, ningún DTO, `pom.xml`, `V1__schema_inicial.sql`, el frontend ni `.github/workflows/ci.yml`.

### Pruebas (`src/test`)

| Archivo | Cambio | Líneas |
|---|---|---|
| `service/UsuarioServiceTest.java` | +4 pruebas de `actualizar` | +43 |
| `infrastructure/exception/GlobalExceptionHandlerTest.java` | +8 pruebas de mapeo de integridad | +85 |
| `presentation/controllers/UsuarioIntegridadMockMvcTest.java` | **nuevo**, 8 pruebas | 184 |
| `integration/UsuarioIntegridadIntegrationTest.java` | **nuevo**, 2 pruebas | 107 |

### Documentación

`README.md` (fecha de entrega → «Pendiente de confirmar»), `02_PREPARACION_COMMIT.md` y
`03_CREDENCIALES_Y_CI.md` (registro de esa misma corrección), `04_ESPECIFICACION_IMPLEMENTACION.md`
(correcciones F.1–F.3 y estado de B0) y este `05_...md`.

---

## 3. Reglas implementadas

### A. Documento en `UsuarioService.actualizar`

```java
// Verificar si el documento ya existe en otro usuario
Optional<Usuario> existentePorDocumento = usuarioRepository.findByDocumento(dto.getDocumento());
if (existentePorDocumento.isPresent() && !existentePorDocumento.get().getId().equals(id)) {
    throw new BusinessRuleException("Ya existe otro usuario con el documento: " + dto.getDocumento());
}
```

| Regla pedida | Cómo quedó |
|---|---|
| A.1 Documento de otro usuario → conflicto, sin guardar, datos originales intactos | `BusinessRuleException` → el `@ExceptionHandler` ya existente la mapea a **409**. La comprobación está **antes** de cualquier `setX()`, así que el entity no se mutó; además el método es `@Transactional`. |
| A.2 Documento propio → permitido | `!getId().equals(id)` excluye al propio usuario; la prueba `actualizar_PermiteConservarElDocumentoPropio` lo fija. |
| A.3 Documento nuevo disponible → permitido | `Optional.empty()` → continúa. |
| A.4 Conservar la validación de correo | No se tocó su lógica ni su orden (correo primero, documento después): el mensaje y el caso de error existentes no cambian. Que el **correo propio no se rechaza** lo prueba la prueba preexistente `actualizar_CambiaDatosSinAlterarEstadoCuandoNoSeProvee`, que devuelve al mismo usuario en `findByCorreo` y aún así actualiza (P5). |
| A.5 Convenciones actuales, sin cambiar el contrato | Se reutilizó `BusinessRuleException` con el mismo estilo de mensaje que `crear()`; el `PUT` devuelve lo de siempre (`200` con el DTO) y en conflicto `409` con `ErrorResponse`. |
| A.6 Reutilizar consultas; mínima consulta nueva justificada | Se añadió **una** consulta de derivación (`findByDocumento`), equivalente en forma a `findByCorreo` que ya existía. **Por qué fue necesaria:** la alternativa era copiar el `findAll().stream()` de `crear()`, que carga la tabla `usuario` entera en memoria en cada actualización; se añadió una prueba que prohíbe ese camino (`actualizar_UsaSoloBusquedasDirectasYNoCargaLaTabla`). |
| A.7 Sin reglas nuevas de formato o normalización | **Ninguna añadida.** `documento` sigue siendo texto libre con `@NotBlank` y `@Size(max=20)`; no se recorta, no se normaliza, no se valida contra un `tipo_documento`. Queda como decisión abierta (relacionada con D6 de `04`). |

### B. `DataIntegrityViolationException` → 409

| Condición pedida | Cómo quedó |
|---|---|
| B.1 Conflicto previsto → 409 | Todos los `DataIntegrityViolationException` mapean a `409 Conflict` con `error = "Conflict"`. |
| B.2 No asumir que todo conflicto es documento | El mensaje depende de la restricción reconocida; si no se reconoce, se usa el genérico. |
| B.3 Mensaje específico solo si la identificación es fiable | Comparación **exacta** contra el catálogo `{uk_usuario_documento, uk_usuario_correo}` (nombres declarados en `V1__schema_inicial.sql`). Nada fuera de esa lista produce un mensaje específico. |
| B.4 Conflicto no identificado → mensaje seguro | `"La operación entra en conflicto con los datos existentes o sus relaciones"`. |
| B.5 No exponer SQL, tablas, restricciones, stack traces, mensajes del driver ni datos personales | La respuesta se construye **solo** con `timestamp`, `status`, `error` y uno de los tres mensajes fijos. El nombre de la restricción se compara pero **nunca** se copia en la respuesta; el `getMessage()` de la excepción **nunca** se usa. Verificado por pruebas que niegan la presencia de `insert`, `PUBLIC`, `uk_`, del documento y del correo en el cuerpo. |
| B.6 Clasificar por metadatos, no por textos con valores personales | Se lee `ConstraintViolationException.getConstraintName()` (metadato de Hibernate) tras recorrer la cadena de causas. No se busca ninguna subcadena en el mensaje: se compara **igualdad** contra dos claves controladas. **Dato importante medido en H2:** ese campo no siempre es un nombre limpio (ver §7.3), y por eso la comparación es de igualdad exacta y nunca se imprime. |
| B.7 Sin `catch` genérico que convierta todo en 409 | El handler está tipado a `DataIntegrityViolationException`. El handler genérico de `Exception` (→ 500) sigue igual y dos pruebas lo confirman: `IllegalStateException` y `DataRetrievalFailureException` (otro `DataAccessException`, **no** de integridad) siguen devolviendo 500. |
| B.8 Conservar el tratamiento de entrada inválida, recurso inexistente, autenticación y autorización | Ningún handler existente se modificó. Pruebas MockMvc: `404` con `ResourceNotFoundException`, `400` con `Validation Error`. Los `401`/`403` conservan sus pruebas unitarias previas. |

Un detalle de orden que importa: Spring resuelve el handler **más específico** por jerarquía de excepciones, así que
agregar `DataIntegrityViolationException` no desvía los casos que ya capturaban `BusinessRuleException`,
`MethodArgumentNotValidException` o `ResourceNotFoundException`. Eso es lo que comprueban las dos últimas pruebas
de la tabla §4 (P8), no es una suposición.

---

## 4. Pruebas añadidas

| Petición tuya | Prueba | Clase | Resultado |
|---|---|---|---|
| **P1** Rechaza documento de otro, no guarda, no modifica originales | `actualizar_RechazaDocumentoDeOtroUsuarioSinModificarElOriginal` | `UsuarioServiceTest` | pasa |
| **P2** Conservar documento propio | `actualizar_PermiteConservarElDocumentoPropio` | `UsuarioServiceTest` | pasa |
| **P3** Documento nuevo disponible | `actualizar_PermiteDocumentoNuevoNoAsignado` | `UsuarioServiceTest` | pasa |
| **P4** Correo duplicado sigue rechazado | `actualizar_RechazaCorreoUsadoPorOtroUsuario` (preexistente, intacta) | `UsuarioServiceTest` | pasa |
| **P5** Correo propio sigue permitido | `actualizar_CambiaDatosSinAlterarEstadoCuandoNoSeProvee` (preexistente: `findByCorreo` devuelve al mismo usuario y la actualización pasa). La prueba nueva `actualizar_PermiteConservarElDocumentoPropio` no ejercita el correo propio: deja `findByCorreo` sin simular, por lo que devuelve vacío | `UsuarioServiceTest` | pasa |
| **P6** Integridad → 409, formato consistente, sin detalles internos | `restriccionDeDocumentoConocidaDevuelve409SinFiltrarMetadatos`, `restriccionDeCorreoConocidaDevuelve409ConMensajePropio` | `UsuarioIntegridadMockMvcTest` | pasa |
| **P6 (unidad)** mapeo por restricción | `integrityWithCataloguedDocumentConstraintMapsTo409WithoutInternalNames`, `integrityWithCataloguedEmailConstraintMapsToItsOwnMessage`, `integrityWithUnknownConstraintMapsToSafeMessage`, `integrityWithNullConstraintNameMapsToSafeMessage`, `constraintNameIsMatchedIgnoringCaseAndSurroundingSpaces`, `constraintIsLocatedEvenWhenNestedInMoreCauses`, `safeIntegrityMessageOmitsSqlAndPersonalValues` | `GlobalExceptionHandlerTest` | pasa |
| **P7** Integridad sin causa detallada | `integridadSinCausaDetalladaDevuelve409YNoFallaelManejador` (MockMvc) + `integrityWithoutCauseIsHandledSafely` (unidad) | ambas | pasa |
| **P8** Error no relacionado no se convierte en 409 | `errorDeProgramacionNoSeConvierteEnConflicto`, `falloDeConexionNoSeConvierteEnConflicto`, `recursoInexistenteSigueSiendo404`, `payloadInvalidoSigueSiendo400` | `UsuarioIntegridadMockMvcTest` | pasa |
| (extra) No cargar la tabla para validar | `actualizar_UsaSoloBusquedasDirectasYNoCargaLaTabla` | `UsuarioServiceTest` | pasa |
| (extra) Conflicto **real** de base, no simulado | `duplicadoRealDeDocumentoLlegaComoConflictoSeguro`, `conflictoRealEnH2UsaElMensajeGenericoPorqueElNombreNoEsElDeclaradoEnV1` | `UsuarioIntegridadIntegrationTest` | pasa |

**22 pruebas nuevas** (139 → 161). Ninguna prueba existente fue eliminada ni modificada en su aserción: las dos
pruebas de `actualizar` que ya existían (`P4`, `P5`) quedaron intactas, y así lo muestra `git diff` (solo hay
líneas añadidas en el archivo de prueba).

MockMvc se usó en dos niveles: `standaloneSetup(new UsuarioController(...)).setControllerAdvice(new GlobalExceptionHandler())`
para ver la resolución de excepciones dentro de la cadena real de MVC **sin** levantar el contexto ni la base, y
`@SpringBootTest @ActiveProfiles("test")` para provocar el duplicado real sobre H2 y entregar esa excepción al bean
`GlobalExceptionHandler` del contexto. No se abrió ninguna conexión fuera de H2.

---

## 5. Evidencia del fallo inicial y de su corrección

Se escribió la prueba antes de la corrección y se ejecutó. Dos notas de honestidad sobre el procedimiento:

* La consulta `findByDocumento` y la prueba P1 se escribieron juntas: sin la consulta declarada, la prueba **no
  compilaba** (Mockito necesita el método del interface para hacer el stub). Lo que sí se reprodujo en rojo fue el
  **comportamiento defectuoso**, no la firma del repositorio.
* Entre la primera y la segunda ejecución hubo un error mío: importé `CannotGetJdbcConnectionException` de
  `org.springframework.dao` (vive en `org.springframework.jdbc`), lo que produjo un fallo de compilación
  —no un fallo de prueba—. Se sustituyó por `DataRetrievalFailureException` (`org.springframework.dao`), que cumple
  el mismo papel: un `DataAccessException` que **no** es de integridad.

### 5.1 Rojo (antes de la corrección)

`mvn -B -o test -Dtest='UsuarioServiceTest,UsuarioIntegridadMockMvcTest'` — no se le tomó marca de tiempo; quedó
entre la primera tentativa fallida de compilación y la corrida completa de las 21:15:42.

```
[ERROR] Tests run: 8,  Failures: 4, Errors: 0 -- UsuarioIntegridadMockMvcTest
[ERROR] Tests run: 16, Failures: 1, Errors: 2 -- UsuarioServiceTest
[ERROR] Tests run: 24, Failures: 5, Errors: 2, Skipped: 0
[INFO] BUILD FAILURE
```

Detalles textuales del informe Surefire de esa corrida:

```
UsuarioIntegridadMockMvcTest.integridadSinCausaDetalladaDevuelve409YNoFallaelManejador:122
    Status expected:<409> but was:<500>
UsuarioIntegridadMockMvcTest.restriccionDeCorreoConocidaDevuelve409ConMensajePropio:92
    Status expected:<409> but was:<500>
UsuarioIntegridadMockMvcTest.restriccionDeDocumentoConocidaDevuelve409SinFiltrarMetadatos:72
    Status expected:<409> but was:<500>
UsuarioIntegridadMockMvcTest.restriccionNoCatalogadaDevuelve409ConMensajeSeguro:106
    Status expected:<409> but was:<500>
UsuarioServiceTest.actualizar_RechazaDocumentoDeOtroUsuarioSinModificarElOriginal:127
    Expected com.autoscolombia.infrastructure.exception.BusinessRuleException to be thrown, but nothing was thrown.
```

Los dos `Errors` restantes (no fallos de lógica) son `UnnecessaryStubbingException` en
`actualizar_PermiteConservarElDocumentoPropio` (línea 137) y `actualizar_PermiteDocumentoNuevoNoAsignado`
(línea 147): el stub de `findByDocumento` no era llamado por nadie porque la validación no existía. Confirman el
mismo defecto por otra vía.

### 5.2 Verde, después de la corrección

Ejecución dirigida a las cuatro clases, después de la corrección y antes de la corrida de cierre de §6
(`mvn -B -o test -Dtest=...`):

```
[INFO] Tests run: 15, Failures: 0, Errors: 0 -- GlobalExceptionHandlerTest
[INFO] Tests run: 2,  Failures: 0, Errors: 0 -- UsuarioIntegridadIntegrationTest
[INFO] Tests run: 8,  Failures: 0, Errors: 0 -- UsuarioIntegridadMockMvcTest
[INFO] Tests run: 16, Failures: 0, Errors: 0 -- UsuarioServiceTest
[INFO] Tests run: 41, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 6. Resultados reales de las suites (ejecución de cierre)

### 6.1 Backend — `mvn -B clean verify`

```
[INFO] Tests run: 161, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  01:08 min
```

* Command completo: `mvn -B clean verify` (Maven 3.9.16, Java 17.0.12, Oracle).
* Hora de inicio 21:22:47, fin 21:23:59 del 2026-10-30.
* **No** se usó `-Dmaven.test.failure.ignore`. No se omitió ni eliminó ninguna prueba.
* 161 = 139 de la línea estabilizada + 22 nuevas de este bloque.

Antecedentes de esta misma sesión (noche del 30 de octubre), para que no se confundan las corridas:

* 21:15 — `mvn -B clean verify` **falló por compilación** (la prueba de integración usaba una constante
  package-private del handler). No es un fallo de lógica ni un resultado de prueba.
* 21:16:28 — 160 pruebas, 0 fallos, 01:10 min. Todavía sin la prueba
  `integrityWithNullConstraintNameMapsToSafeMessage`, que se escribió después al ver que la línea 150 del manejador
  tenía una rama sin ejercitar.
* 21:20:25 — 161 pruebas, 0 fallos, 44,962 s.
* 21:22:47 — **corrida de cierre reportada arriba** (161 pruebas, 01:08 min), tras reordenar los campos estáticos y
  el método nuevo dentro de `GlobalExceptionHandler` sin cambiar su comportamiento.

### 6.2 Frontend — `node --test src/test/js/app.test.js`

```
ℹ tests 4
ℹ pass 4
ℹ fail 0
ℹ duration_ms 232.5867
```

Versión de Node medida en esta máquina: **v26.7.0** (un solo binario, `C:\Program Files\nodejs\node`). El frontend
no se modificó en este bloque; la suite es la misma de antes y pasa igual.

### 6.3 Cobertura de esta corrida (JaCoCo 0.8.11, generada por `mvn -B clean verify` de 21:23)

Contadores del informe global (`target/site/jacoco/jacoco.xml`, nivel `<report>`), cada métrica por separado:

| Métrica | Cubierto | Total | % |
|---|---:|---:|--:|
| Líneas | 893 | 987 | **90,5 %** |
| Instrucciones | 5.442 | 9.512 | **57,2 %** |
| Ramas | 161 | 906 | **17,8 %** |
| Métodos | 557 | 774 | **72,0 %** |
| Clases | 72 | 72 | **100 %** |

Nota de consistencia ya conocida: la suma de las filas por clase del CSV da líneas 912/1006 (90,7 %) frente a
987 del total del informe; la diferencia es atribución de líneas a clases internas. Se reporta como oficial la del
informe, igual que en `01`.

Clases tocadas por este cambio:

| Clase | Líneas | Ramas | Métodos |
|---|---:|---:|---:|
| `GlobalExceptionHandler` | 80/80 (100 %) | 12/12 (100 %) | 14/14 (100 %) |
| `UsuarioService` | 53/54 (98,1 %) | 15/16 (93,8 %) | 12/12 (100 %) |
| `UsuarioController` (sin cambios, referencia) | 9/9 | sin ramas | 5/5 |

Comparación con la medición anterior documentada en `01` §7 (corrida de las 15:47 del mismo día, cifras de su
propia ejecución):

| Métrica | Antes (15:47) | Después (21:23) | Delta |
|---|---:|---:|---|
| Líneas | 865/960 (90,1 %) | 893/987 (90,5 %) | +28 cubiertas / +27 totales |
| Instrucciones | 5.313/9.406 (56,5 %) | 5.442/9.512 (57,2 %) | +129 / +106 |
| Ramas | 144/890 (16,2 %) | 161/906 (17,8 %) | **+17 cubiertas / +16 totales** |
| Métodos | 546/769 (71,0 %) | 557/774 (72,0 %) | +11 / +5 |
| Pruebas | 139 | 161 | +22 |

**Lectura honesta del +17 cubiertas / +16 totales de ramas:** el código nuevo de B0 aporta 16 ramas (4 en el `if` de
documento, 12 entre el manejador y sus helpers) y **están todas cubiertas** (`GlobalExceptionHandler` 12/12 y el
`if` nuevo de `UsuarioService` sin ramas perdidas). Aun así las ramas cubiertas subieron 17 y las perdidas bajaron
de 746 a 745: **una rama preexistente pasó a estar cubierta**. No puedo decir cuál, y no lo voy a inventar:
`mvn clean` borró el reporte XML de la corrida de las 15:47 y no guardé un comparativo por método. Se registra como
hecho sin atribuir, no como mérito de este bloque. Lo que sí está medido en esta corrida es la rama que sigue sin
cubrirse en `UsuarioService`: la línea 83, `if (dto.getActivo() != null)` en su camino «el DTO sí trae activo», que
es código preexistente a B0 y ninguna prueba lo ejercita.

**Qué NO dice esta tabla:** que la aplicación esté bien, que la gestión de usuarios esté completa, ni que no haya
defectos. La cobertura mide qué líneas ejecutan las pruebas, no qué comportamientos son correctos; sigue habiendo
12 requisitos de la Iteración 2 sin ninguna prueba (ver `04` §6.1) y 17 riesgos de la Iteración 1 sin atender.

---

## 7. Limitaciones de la verificación sobre H2

### 7.1 Las pruebas no corren contra PostgreSQL

El perfil `test` usa `jdbc:h2:mem:...;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`, `ddl-auto: create-drop` y
**Flyway deshabilitado**. Nada de lo verificado aquí toca una PostgreSQL persistente: no se conectó, no se creó, no
se migró.

### 7.2 El esquema de prueba no es el esquema real

Con Flyway deshabilitado, `uk_usuario_documento` —el nombre declarado en `V1__schema_inicial.sql`— **no existe**
en la base de prueba: Hibernate genera sus propias restricciones a partir de `@Column(unique = true)`. Por eso el
catálogo de `GlobalExceptionHandler` no puede coincidir en H2. La consecuencia verificada: **la rama de mensaje
específico nunca se dispara contra una base real de prueba**; solo se comprobó con excepciones construidas en las
pruebas unitarias y MockMvc.

### 7.3 Qué devuelve realmente `getConstraintName()` en H2 (medido)

La prueba `conflictoRealEnH2...` capturó la cadena que Hibernate expone al violar la unicidad en H2, con esta forma
(valores de prueba ficticios):

```
public.CONSTRAINT_INDEX_F ON public.usuario(documento NULLS FIRST) VALUES ( /* n */ '<documento de prueba>' )
```

Tres consecuencias, todas incorporadas en el diseño:

1. **No es un nombre limpio:** trae esquema, tabla, columnas y el valor que provocó el conflicto.
2. Por eso el código lo usa **solo para una comparación de igualdad** y jamás lo copia en la respuesta; si se
   hubiera hecho un `contains` sobre ese texto, la respuesta filtraría el documento. Las pruebas niegan la
   presencia del documento, de `PUBLIC`, de `insert` y de `uk_` en el cuerpo de la respuesta.
3. El conflicto real de H2 cae en el **mensaje genérico** (eso es exactamente lo que afirma la segunda prueba de
   integración), que sigue siendo un `409` correcto y sin fuga.

### 7.4 Qué queda sin cubrirla prueba de nivel unidad

* P1 verifica contra un `UsuarioRepository` **simulado**: comprueba que no se llama a `save()` y que el objeto
  `Usuario` no cambió. No comprueba un rollback real en base de datos; el `@Transactional` no está ejercitado por
  esa clase. El escenario de base real lo cubre `UsuarioIntegridadIntegrationTest`, pero sobre H2.
* El `save()` que sí llega a la base y viola la restricción devuelve 409 en la cadena **Java + excepción**; no se
  verificó el HTTP completo contra PostgreSQL, porque el duplicado real se produce dentro del repositorio y el
  controlador ya está protegido por la validación nueva (haría falta un dato que salte la validación para forzarlo
  por HTTP, lo que sería una prueba artificial).
* Los índices parciales de PostgreSQL (`... WHERE estado = 'ACTIVO'`) que pide RF-NF-05 **no son soportados por
  H2**: cualquier restricción de ocupación que se escriba así queda sin verificar en esta suite. No se creó ninguna
  en este bloque.

---

## 8. Qué queda pendiente para PostgreSQL

1. Confirmar el entorno real (lista de datos no secretos en `04` §16: host, puerto, nombre exacto de la base, rol,
   versión, herramienta, si hay datos que conservar). **La contraseña no se pide por chat.**
2. Verificar que `ddl-auto: validate` levanta la aplicación con `V1` aplicado (hoy solo se comprueba contra H2).
3. Comprobar contra PostgreSQL qué cadena entrega Hibernate en `getConstraintName()` al violar
   `uk_usuario_documento` / `uk_usuario_correo`: si aparece el nombre exacto, la rama de mensaje específico por fin
   se ejerce; si no, el catálogo debe ajustarse a lo que la base realmente reporte. **Esto es un experimento, no
   una promesa: hoy no sé cuál de los dos ocurrirá.**
4. Prueba HTTP end-to-end del 409 por restricción de base (con un insert que salte la validación de servicio, o
   con dos peticiones concurrentes).
5. Sin eso, todo lo que se afirma aquí vale para la capa Java y para H2, no para el entorno de producción.

---

## 9. Qué NO declara este bloque

* **No** está terminada la gestión de usuarios. Faltan, entre otras: contraseña inicial obligatoria (RF-ACC-03/04),
  corte de acceso de cuentas desactivadas (RF-ACC-01), regla del último administrador (RF-ACC-06) y el endpoint
  explícito de activación (D3).
* **No** hay clientes, ni entidad nueva, ni `V2__iteracion2.sql`, ni cambios de esquema.
* **No** hay reglas nuevas de formato o normalización de documentos (A.7): se dejó explícitamente fuera.
* **No** se cambió el contrato de la API: mismos paths, mismos métodos, mismos cuerpos de éxito; solo un estado
  nuevo (`409`) para casos que antes daban `500`, que es el estado que la especificación ya daba por supuesto.
* **No** se modificó `V1`, el frontend, `pom.xml`, `docker-compose.yml` ni `.github/workflows/ci.yml` en esta fase.
* **No** se ejecutó nada en GitHub Actions ni se publicaron imágenes.
* **No** se reveló ningún valor de credencial: la revisión de este bloque no abrió archivos de secretos.

## 10. Restricciones de la autorización y su cumplimiento

| Restricción | Estado |
|---|---|
| No conectar a PostgreSQL persistente | cumplido (H2 en memoria, perfil `test`) |
| No crear ni ejecutar migraciones | cumplido (`git diff` no toca `db/migration`) |
| No modificar `V1__schema_inicial.sql` | cumplido (`git diff` sobre ese archivo: vacío; sigue perteneciendo a `e701c75`) |
| No modificar frontend | cumplido (`src/main/resources/static` sin cambios) |
| No modificar el workflow en este bloque | cumplido (`ci.yml` trae el cambio de la fase `03`, nada nuevo) |
| No instalar herramientas del sistema | cumplido |
| No `git add`, commit ni push | cumplido: índice vacío (`git diff --cached` sin salida), `HEAD` sigue en `e701c75` |
| No modificar la carpeta original | cumplido (`Ingenieria - Evidencia de aprendizaje 1` sin cambios en su estado de Git) |
| No pagos ni pico y placa nuevo | cumplido |
| No `-Dmaven.test.failure.ignore` | cumplido (no se usó; el rojo de §5.1 se reportó como BUILD FAILURE) |
| No eliminar pruebas para aprobar | cumplido (0 pruebas eliminadas; 139 → 161) |

## 11. Correcciones documentales de esta fase (apartado F de la autorización)

| # | Corrección | Dónde |
|---|---|---|
| F.1 | La fecha de entrega **no** estaba fijada en la consigna compartida. `README.md:18` dice ahora «Pendiente de confirmar, Colombia, Antioquia, Medellín» (la ubicación sí proviene de la consigna y se conserva). Registrado también en `03` §3.4 y `02` (propuesta de commit). | `README.md`, `02`, `03` |
| F.2 | §5 de `04` reescrito: las cifras de pruebas, cobertura y duración dejaron de ser «requisitos no funcionales medidos» y pasaron a columnas separadas de **requisito verificable / evidencia observada / fecha y alcance de la medición**. Las métricas de este bloque alimentan RF-NF-01, RF-NF-02 y RF-NF-03 con su fecha. | `04` §5 |
| F.3 | Trazabilidad real: se contaron los 32 requisitos de §4 contra los criterios de aceptación de las 6 historias. **19 con cobertura explícita completa, 1 parcial (RF-CEL-11) y 12 sin ninguna.** Se corrigieron los «Cubre: RF-XX-01…09» que afirmaban cobertura por rango, y se descubrió que el criterio de RF-ACC-02 (`403` al operar como OPERADOR) **no tiene ninguna prueba actual**: `UsuarioControllerTest` es unitario con Mockito y no ejercita Spring Security. | `04` §6 y §6.1 (nuevo) |

Nada de esto toca los entregables de Isabella: el conteo de trazabilidad es un insumo para que ella asigne los
identificadores formales de historia, no una renumeración de sus documentos.
