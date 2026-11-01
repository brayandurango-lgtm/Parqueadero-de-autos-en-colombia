# Cierre técnico — Rol de Brayan — Iteración 2

**Fecha de revisión:** 2026-11-01
**Responsabilidad:** desarrollo e integración técnica. Este cierre no sustituye los entregables funcionales de Isabella.
**Estado:** migraciones, arranque y recorrido sintético de cliente/entrada/salida verificados en PostgreSQL 18.6; autenticación, salud, panel y lecturas paginadas comprobadas en navegador. La administración de celdas, los diagramas visuales y la publicación siguen pendientes.

## 1. Alcance implementado en código

- **Personal:** `UsuarioController`, `/api/v1/usuarios`: listar, crear con contraseña inicial propia obligatoria, editar sin cambiar contraseña, activar/desactivar y baja lógica. Acceso restringido en backend a `ADMINISTRADOR`; las respuestas no incluyen contraseña ni hash; se protege el último administrador activo y las peticiones de cuentas inactivas reciben `401`.
- **Clientes y vehículos:** `/api/v1/clientes` para consulta paginada, búsqueda, alta, edición, cambio de estado y vehículos vinculados; `PUT /api/v1/vehiculos/{placa}/cliente` gestiona la titularidad. La identidad se valida por tipo y número de documento. Los vehículos heredados pueden permanecer con `cliente_id` nulo. Los cambios de titularidad no se infieren del texto libre.
- **Celdas:** `/api/v1/celdas` incluye consulta y paginación, alta, edición, estado activo y mantenimiento. La API impide desactivar una celda ocupada y no permite establecer `OCUPADA` manualmente.
- **Integración operativa:** la entrada exige una celda asignable, evita registros activos incompatibles, verifica el titular inactivo y conserva la regla existente de Pico y Placa. Salida/cancelación liberan solo la celda asociada al registro; la auditoría se mantiene transaccional.
- **Fuera de alcance:** no se agregaron pagos, tarifas ni reservas.

## 2. Arquitectura y configuración

Aplicación monolítica Java 17 / Spring Boot 3.2.5; capas `presentation`, `domain` e `infrastructure`, Spring Security/JWT, JPA/Hibernate, Flyway y PostgreSQL. El HTML/CSS/JavaScript vanilla de `src/main/resources/static/` se sirve desde Spring Boot. Las pruebas de navegador automatizadas no están configuradas; la suite Node valida contratos y comportamiento de interfaz en un DOM simulado.

Configuración leída en `src/main/resources/application.yml`: `ddl-auto: validate`, `baseline-on-migrate: false`, migraciones Flyway habilitadas; credenciales por variables de entorno. El perfil `dev` se apuntó a la base solicitada. En la base confirmada `Parqueadero de autos en colombia`, servidor PostgreSQL 18.6, esquema `public`, se comprobó inicialmente que no había tablas de usuario ni historial Flyway. Tras autorización expresa, Flyway validó y aplicó V1, V2 y V3; Hibernate inició con `ddl-auto: validate`. En el reinicio posterior Flyway informó versión actual 3 y “No migration necessary”.

Endpoints de referencia:

| Área | Rutas principales |
|---|---|
| Autenticación | `POST /api/v1/auth/login`, `GET /api/v1/auth/me` |
| Personal | `GET/POST /api/v1/usuarios`, `PUT /api/v1/usuarios/{id}`, `PATCH /api/v1/usuarios/{id}/activo` |
| Clientes | `GET/POST /api/v1/clientes`, `PUT /api/v1/clientes/{id}`, `PATCH /api/v1/clientes/{id}/activo`, `GET /api/v1/clientes/{id}/vehiculos` |
| Vehículos | `PUT /api/v1/vehiculos/{placa}/cliente` |
| Celdas | `GET /api/v1/celdas`, `GET /api/v1/celdas/pagina`, `POST /api/v1/celdas`, `PUT /api/v1/celdas/{id}`, `PUT /api/v1/celdas/{id}/estado`, `PATCH /api/v1/celdas/{id}/activo` |
| Operación | `POST /api/v1/registros/entrada`, `PUT /api/v1/registros/salida/{placa}`, `DELETE /api/v1/registros/{id}` |

## 3. Pruebas ejecutadas en esta revisión

| Prueba | Entorno | Resultado | Fecha | Evidencia |
|---|---|---|---|---|
| Suite Java completa tras el ajuste PostgreSQL | Windows 11, JDK 17.0.12; configuración de pruebas del proyecto (H2) | **319** aprobadas; 0 fallos | 2026-11-01 | resultado de ejecución de pruebas Java |
| `CeldaServiceTest` + `CeldasIntegridadIntegrationTest` tras el ajuste | configuración de pruebas del proyecto (H2) | **62** aprobadas; 0 fallos | 2026-11-01 | resultado de ejecución focalizada |
| `node.exe --test src/test/js/app.test.js` | Node v26.7.0 | **35** aprobadas; 0 fallos | 2026-11-01 | salida del comando; incluye regresión para observaciones nulas |
| Consulta inicial PostgreSQL de solo lectura | `localhost:5432`, base indicada, rol `postgres`; transacción finalizada con `ROLLBACK` | conexión autenticada; PostgreSQL 18.6; esquemas de usuario `pg_toast` y `public`; cero tablas de usuario y sin `public.flyway_schema_history` | 2026-11-01 | salida de `psql` aportada por el usuario |
| Flyway/JPA en PostgreSQL real | aplicación Spring Boot 3.2.5, perfil `dev` | Flyway validó 3 migraciones, aplicó V1–V3 y alcanzó V3; Hibernate validó el mapeo; reinicio posterior quedó al día | 2026-11-01 | logs de arranque de la aplicación |
| Salud y lectura en navegador | aplicación local `http://localhost:8080`, con PostgreSQL 18.6 | `/actuator/health` `UP`; login y `/auth/me` HTTP 200; dashboard 16 celdas; clientes HTTP 200 (0 antes de prueba); celdas HTTP 200, 16 filas, 10 por página; filtro `b-0` HTTP 200/4 coincidencias | 2026-11-01 | navegador compartido y respuestas HTTP observadas |
| Cliente + entrada/salida sintética | flujo web y API reales, PostgreSQL 18.6 | cliente ficticio creado; vehículo `QWE123` vinculado; entrada en `A-01` `ACTIVO`; salida `CERRADO`; comprobantes de entrada/salida con QR; `GET /registros/activos` devuelve 0 y `A-01` vuelve a estar disponible | 2026-11-01 | respuestas del navegador/API y comprobantes persistidos |

Las pruebas Java ejercitan H2; no cuentan como pruebas de PostgreSQL. La evidencia PostgreSQL procede de las migraciones y consultas reales descritas arriba. Se detectó primero un HTTP 500 al listar celdas (`upper(bytea)`); se corrigió el filtro opcional para usar cadena vacía y se comprobó de nuevo con PostgreSQL real. Flyway 9.22.3 advirtió que su versión solo declara soporte probado hasta PostgreSQL 15; el arranque en PostgreSQL 18.6 funcionó, pero esa advertencia de compatibilidad debe quedar visible. No se usó `failure.ignore`.

## 4. PostgreSQL y migraciones — comprobación real

La conexión, destino y esquema se comprobaron en vivo. La base estaba vacía antes de ejecutar migraciones; con autorización se aplicaron V1–V3 mediante Flyway. No se ejecutaron `baseline`, `repair`, `clean`, `DROP` ni `ddl-auto:update`. El inicio reportó una advertencia de compatibilidad de Flyway 9.22.3 con PostgreSQL 18.6 (última versión declarada como probada: PostgreSQL 15).

La contraseña de conexión se introdujo únicamente en un prompt local oculto; no se registra aquí ni se reutiliza el archivo local de credenciales. La clave JWT se generó en memoria para cada arranque de verificación. Con autorización, se creó un cliente de prueba (documento `TEST20261101A`) y el vehículo ficticio `QWE123`; el registro de entrada/salida quedó cerrado, junto con sus dos comprobantes y auditoría. Estos datos **permanecen en la base local** como historial de prueba. No se crearon altas administrativas de celdas ni se probaron sus escrituras contra PostgreSQL.

## 5. Interfaz y comprobación manual

El código contiene las vistas de Resumen, Personal, Clientes, Celdas, Entrada, Salida y Novedades. La aplicación se abrió en `http://localhost:8080`; se verificó salud, login de administración, dashboard, cliente ficticio, selección del titular en entrada, ingreso con comprobante QR, búsqueda y salida con liberación de celda, lista/paginación/filtro de celdas y render de observaciones nulas. Los datos sintéticos quedan en la base según la autorización recibida. No se entregan credenciales de demostración en este documento.

## 6. StarUML y evidencias

Se conserva intacto `docs/staruml/AutosColombia_Iteracion1.mdj`. `docs/staruml/AutosColombia_Iteraciones1y2.mdj` existe y su texto declara diez nombres de diagramas de secuencia y uno de componentes; la inspección del JSON muestra que son **marcadores vacíos**, sin participantes/mensajes de secuencia ni vistas/conexiones de componentes suficientes para considerarlos diagramas terminados. StarUML no está instalado en este equipo; no hubo apertura visual ni exportación de imágenes. El `.mdj` no se declara validado.

Para cerrar esta evidencia: abrir el archivo de ambas iteraciones en StarUML; completar secuencias de entrada, salida, cancelación, personal, clientes/vinculación y celdas; añadir dependencias/interfaces del diagrama integrado de componentes; contrastar participantes y rutas con el código final; guardar el `.mdj` y exportar imágenes para que Isabella las incorpore. No incluir pagos como implementados.

## 7. Matriz de insumos para Isabella

Los identificadores de historias deben enlazarse con los que Isabella defina; los siguientes nombres son descripciones técnicas, no sustituyen su catálogo formal.

| Requisito | Historia (mapear con Isabella) | Pantalla/API | Prueba automatizada | Diagrama | Estado |
|---|---|---|---|---|---|
| Cuentas, roles y último administrador | Gestión segura de personal | `usuarios-view`; `/api/v1/usuarios` | `CuentasPersonalIntegrationTest`, `UsuarioServiceTest`, `app.test.js` | Secuencia de personal requerida; archivo actual vacío | Suite H2; inicio de sesión administrador verificado en vivo; administración de usuarios no recorrida |
| Clientes, baja lógica y titularidad | Registro y conservación de clientes/vehículos | `clientes-view`; `/api/v1/clientes`, `/api/v1/vehiculos/{placa}/cliente` | `ClientesVehiculosIntegrationTest`, `ClienteServiceTest`, `app.test.js` | Secuencia cliente/vinculación requerida; archivo actual vacío | Alta sintética y vinculación durante entrada verificadas en PostgreSQL; edición/baja y endpoint de cambio de titularidad no recorridos |
| Celdas, mantenimiento y asignación íntegra | Administración y disponibilidad de celdas | `celdas-view`; `/api/v1/celdas` | `CeldasIntegridadIntegrationTest`, `CeldaServiceTest`, `app.test.js` | Secuencias de celdas requeridas; archivo actual vacío | Lista, filtros y paginación verificados en PostgreSQL; escrituras administrativas no recorridas |
| Entrada/salida/cancelación integradas | Operación de parqueadero (iteraciones 1 y 2) | `entrada-view`, `salida-view`; `/api/v1/registros/*` | `RegistroControllerIntegrationTest`, `CeldasIntegridadIntegrationTest` | Secuencias de operación requeridas; archivo actual vacío | Entrada/salida reales verificadas con registro sintético cerrado; cancelación real no recorrida |

Resultado útil para el plan resumido de Isabella: separar explícitamente evidencia Java/H2, Node, PostgreSQL, navegador y StarUML; no sumar estas verificaciones como si fueran equivalentes. Los wireframes existentes no son un prototipo Figma.

## 8. GitHub y publicación

Remoto configurado: `https://github.com/brayandurango-lgtm/Parqueadero-de-autos-en-colombia.git`. El workspace está en `main` y diverge de `origin/main` (un commit local por delante y otro remoto por detrás). El commit local `668a249` agrega `DB_PASSWORD.txt`; la rama remota actual no contiene ese archivo. **No se debe empujar ese commit ni su ascendencia.** Se añadió `DB_PASSWORD.txt` a `.gitignore` y se retiraron valores de acceso del README y de las notas. El archivo fue retirado del índice con `git rm --cached`; la copia local se conservó y quedó ignorada. El archivo conserva una credencial anterior y no debe volver a usarse ni publicarse.

Ruta segura propuesta: conservar la rama local, preparar tras autorización una rama saneada cuyo padre sea el `origin/main` verificado, excluir `DB_PASSWORD.txt`, `.env`, `target`, logs y datos personales, y publicar por fast-forward normal cuando el historial lo permita. No force-push, no rebase, no cambio de identidad Git, no imagen Docker ni despliegue. Antes de commit/push se pedirá confirmación con archivos, remoto/rama, mensaje y operaciones. El workflow local es solo de verificación; GitHub Actions de esta revisión **no se ha ejecutado** y los cambios aún no están publicados.

## 9. Límites y estado de cierre

| Criterio | Estado actual |
|---|---|
| Implementación de personal, clientes, celdas e integración | Presente en código |
| Pruebas Java | 319 aprobadas en configuración H2 del proyecto |
| Pruebas JavaScript | 35 aprobadas en Node |
| PostgreSQL / migraciones | PostgreSQL 18.6 real; V1–V3 validadas/aplicadas; esquema público en V3; advertencia de soporte Flyway pendiente de resolver |
| Aplicación / login / lecturas en navegador | `http://localhost:8080`; health UP, login, dashboard, clientes y celdas comprobados |
| Escrituras operativas en PostgreSQL | Alta de cliente y flujo entrada/salida sintéticos verificados; historial permanece local; escrituras administrativas de celdas pendientes |
| StarUML | Archivo presente pero diagramas vacíos; sin validación visual |
| GitHub / hash final / CI de esta revisión | Pendiente de autorización y publicación segura |
| Isabella | Requisitos, historias, casos de uso, mockups y plan siguen siendo sus entregables |
