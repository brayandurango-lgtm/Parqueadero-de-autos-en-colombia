# 03 — Corrección de exposición del README y CI de solo verificación

**Proyecto:** Sistema de información del parqueadero Autos Colombia
**Carpeta:** `C:\Users\Administrador\Downloads\Parqueadero de autos en colombia`
**Fecha de esta fase:** 2026-10-30, 16:21 -05:00 (viernes)
**Documentos relacionados:** `01_ESTABILIZACION_PRUEBAS.md`, `02_PREPARACION_COMMIT.md`

---

## 1. Remoto confirmado

| Comprobación | Resultado |
|---|---|
| `git config --get remote.origin.url` | `https://github.com/brayandurango-lgtm/Parqueadero-de-autos-en-colombia.git` |
| URL confirmada en el encargo | la misma |
| Comparación byte a byte | **COINCIDE EXACTAMENTE** |
| Acción | **no se cambió el remoto** |

---

## 2. Rectificación del hallazgo H1 (lo importante de esta fase)

En `02_PREPARACION_COMMIT.md` clasifiqué los valores de `DB_PASSWORD` y `JWT_SECRET` del README como
**credenciales literales reales** y lo marqué como crítico. **Era falso.** Leídos uno a uno, son
**ejemplos ilustrativos en español** («una clave local», «una clave de firma larga para desarrollo»).

Error de método: los clasifiqué con un script que medía longitud, clases de caracteres y entropía aparente,
y cuya lista de patrones de marcador estaba escrita en inglés (`example`, `placeholder`, `changeme`). Un
texto descriptivo en español con guiones pasa ese filtro y sale como «alta entropía». **La forma de un valor
no demuestra que sea un secreto.** Sección H1 de `02` quedó reescrita con la rectificación.

**Estado real, en los tres términos que pediste distinguir:**

| Concepto | Estado |
|---|---|
| Valor literal publicado | Los cuatro literales del README son **marcadores ilustrativos**; **no** hay secretos operativos publicados en ellos |
| Uso real confirmado / sin verificar | **Sin verificar.** No se abrió ningún archivo privado de secretos ni se leyeron variables de entorno; este agente no comparó los marcadores contra tus valores reales |
| Rotación | **Pendiente / sin confirmar**, la gestionas tú en privado. **Ninguna rotación fue ejecutada ni se afirma lo contrario.** |

Lo que sí quedaba mal y se corrigió: unos marcadores ambiguos como instrucción de instalación.

---

## 3. Cambios en `README.md`

### 3.1 Bloque «Opción 1: Docker Compose» (líneas 148-154)

Antes: dos asignaciones `$env:` con ejemplos en español. Después:

```powershell
# Estos marcadores son texto de documentación: NO son valores ejecutables.
# Genera secretos propios para tu máquina y no los compartas ni los subas al repositorio.
$env:DB_PASSWORD = "<CONFIGURAR_LOCALMENTE>"
$env:JWT_SECRET  = "<GENERAR_Y_CONFIGURAR_LOCALMENTE>"
```

Sintaxis PowerShell conservada (es el shell que el README ya documentaba en ese bloque).

### 3.2 Bloque «Opción 2: ejecución local» (líneas 172-185)

- `DB_PASSWORD` → `<CONFIGURAR_LOCALMENTE>`; `JWT_SECRET` → `<GENERAR_Y_CONFIGURAR_LOCALMENTE>`.
- `DB_NAME` → `<NOMBRE_DE_TU_BASE_DE_DATOS>` y `DB_USER` → `<USUARIO_DE_BASE_DE_DATOS>`. No son secretos,
  pero fijarlos en el README sugería que eran valores de operación. Se añadió una nota aclarando que el nombre
  real de la base **depende de tu instalación y sigue sin verificar**.
- **Corrección de sintaxis documental:** el bloque estaba marcado con triple cerca ` ```bash ` pero contenía
  `$env:...`, que es PowerShell. Ahora el bloque dice ` ```powershell `. En bash real esas líneas no funcionan.

### 3.3 Sección «Credenciales de Prueba (Semilla)»

- Advertencia explícita: **uso exclusivo de demostración, datos ficticios, no seguros para una instalación
  accesible públicamente**; hay que cambiar esas contraseñas o eliminar las cuentas antes de exponer el
  servicio.
- La contraseña compartida en texto claro **sale de la tabla** (queda «acordada en clase; en la base está
  almacenada como hash BCrypt»). Sigue siendo un dato ficticio, pero ya no funciona como receta de acceso
  contra un despliegue descuidado.

### 3.4 Información académica (fase anterior, ratificada)

`Actividad: S25 - Evidencia de Aprendizaje 2 … Iteración 2`, `Fecha de Entrega: Noviembre 2026, Colombia,
Antioquia, Medellín`. **Procedencia de la fecha:** no la inventé; viene textual de tu encargo del paso
anterior, que escribió «Noviembre 2026, Colombia, Antioquia, Medellín». Si eso cambia, se reemplaza por
«Pendiente de confirmar». Las referencias a la **Iteración 1** que describen contenido histórico real
(líneas 34, 61, 251 y el pie «Octubre 2026», línea 256) **se conservaron** sin tocar.

**Actualizado el 2026-10-30 (fase `05`, corrección F.1 de tu autorización):** confirmaste que la consigna
compartida **no** fija la fecha de entrega, así que se activó la condición prevista arriba. El README dice
ahora `Fecha de Entrega y Ubicación: Pendiente de confirmar, Colombia, Antioquia, Medellín`. La ubicación se
conserva porque sí proviene de la consigna; solo la fecha queda sin confirmar. El texto anterior de esta
sección se mantiene como registro de lo que estaba escrito en ese momento.

---

## 4. Inventario de asignaciones de credencial en el repositorio

Escaneo de los 109 archivos versionados. **Solo ubicaciones y nombres de clave; ningún valor reproducido.**

| Ubicación | Clave | Clasificación |
|---|---|---|
| `README.md:150,151` y `:176,177` (pre-edición) | `DB_PASSWORD`, `JWT_SECRET` | marcadores ilustrativos → ahora `<CONFIGURAR_LOCALMENTE>` / `<GENERAR_Y_CONFIGURAR_LOCALMENTE>` |
| `src/main/resources/application.yml:7,28` | `password`, `secret` | `${VAR}` de entorno — correcto |
| `src/main/resources/application-dev.yml:5` | `password` | `${VAR}` de entorno — correcto |
| `src/main/resources/application-test.yml:6,15` | `password`, `secret` | literales **del perfil de prueba** (H2 en memoria + firma JWT de test). No de operación |
| `src/test/java/.../util/JwtUtilTest.java:14` | `SECRET` | constante de prueba (57 caracteres), sin alcance de producción |
| `docker-compose.yml:7,30,31` | `POSTGRES_PASSWORD`, `DB_PASSWORD`, `JWT_SECRET` | referencia a entorno — correcto |
| `.env.example:6,7` | `DB_PASSWORD`, `JWT_SECRET` | plantilla, sin valores |
| `.github/workflows/ci.yml:74` (pre-edición) | `password` | `${{ secrets.GITHUB_TOKEN }}` del propio runner — eliminado con la reescritura |
| `UsuarioController.java:39` | contraseña por defecto literal | defecto de diseño ya registrado (R7/Q4), no exposición nueva |
| `V1__schema_inicial.sql` | 2 hashes BCrypt de cuentas semilla | datos ficticios de demostración |

Rutas de archivo sensibles (`*.pem`, `*.key`, `*.jks`, `id_rsa`, `settings.xml`, `.env`, dumps): **no existen
en la carpeta y ninguna está versionada**. En el disco solo hay `.env.example`.

**No se abrió ningún archivo privado de secretos ni se imprimieron variables de entorno.** Las de operación
en la shell de trabajo están **sin definir** (`DB_PASSWORD`, `JWT_SECRET`, `DB_HOST`, `DB_NAME`, `DB_USER`).

---

## 5. `.github/workflows/ci.yml` reescrito (solo verificación)

| Aspecto | Antes | Ahora |
|---|---|---|
| `name` | `CI/CD Pipeline` | `CI - Verificación` |
| Disparadores | `push` a main, `pull_request` a main | iguales (no se cambiaron: seguir verifying en PR y main) |
| Permisos | `contents: read` + **`packages: write`** | **`contents: read` + `actions: write`** |
| Jobs | `test` y `build` | **uno solo: `verify`** |
| Pasos Maven | `mvn clean verify` + `mvn jacoco:report` + `mvn clean package -DskipTests` | **un único `mvn -B clean verify`** |
| Docker | `login` a GHCR + `build-push-action` con `push: true` | **eliminado** |
| Pruebas JS | ausentes | `node --test src/test/js/app.test.js` |
| Artefacto | JaCoCo | JaCoCo con `if: always()` e `if-no-files-found: warn` |
| Concurrency | ninguno | `verify-${{ github.ref }}` con `cancel-in-progress` |
| `release.yml` | — | **no se creó** (publicación fuera de esta fase) |

**Por qué se elimina el `mvn jacoco:report` aparte:** el plugin enlaza el goal `report` a la fase `test` en
`pom.xml` (ejecución con `<id>report</id><phase>test</phase>`), así que `verify` ya genera el reporte. Se
confirmó en la corrida de hoy: un solo `mvn -B clean verify` produjo `target/site/jacoco/` (marca 16:21).

**Por qué `actions: write`:** es el ámbito que exige `upload-artifact` para publicar artefactos del run. Con
`contents: read` sigue sin haber permiso de escritura sobre el repositorio ni sobre paquetes. **No** se usó
`packages: write` porque ya no hay publicación de imágenes.

### Versión de Node: requisito medido, no copiado de los informes

| Evidencia | Dato |
|---|---|
| ¿`package.json`? | **no existe** → sin `engines` que respetar, sin dependencias npm |
| Import del test | `node:test`, `node:assert/strict`, `node:fs`, `node:path`, `node:vm` en **CommonJS** |
| Sintaxis | encadenamiento opcional `?.`, spread, funciones de prueba `test(nombre, fn)` |
| Suelo real | el runner nativo `node:test` es **estable desde Node 20** (experimental en 18) |
| Verificación local | `which -a node` → único binario `C:\Program Files\nodejs\node`, **v26.7.0**; la suite pasa con él |
| Decisión | `node-version: '26'` — la familia efectivamente verificada |

Si el equipo prefiere ceñirse a una línea LTS, `24` satisface el mismo suelo, pero **no se ejecutó aquí**, así
que no lo elijo por defecto. **No copié la versión desde los informes anteriores**: la medí en la máquina.

### Validación hecha con las herramientas disponibles

- `yq`, `actionlint`, `ruby`, `node js-yaml`: **no disponibles**. `python3` es solo el alias de Microsoft
  Store (no instala nada). Por tanto **no hubo validación con un parser YAML real ni contra el esquema de
  GitHub.**
- Comprobaciones que sí se hicieron sobre el archivo: sin caracteres de tabulación; sin claves duplicadas en
  el mismo nivel de mapeo (en particular `actions:` aparece **una sola vez** en `permissions`); claves raíz
  presentes (`name`, `on`, `jobs`, `permissions`); exactamente 2 pasos `run:`; **cero** referencias a
  `docker`, `ghcr`, `login` o `packages` fuera de los comentarios; y ningún paso requiere secretos: el
  workflow no declara `secrets.*`.
- **No se ejecutó ningún workflow remoto.** **No se afirma que GitHub Actions apruebe este cambio: todavía
  no está publicado.** El primer `push` que lo incluya será la primera validación real.

---

## 6. Qué pudo llegar a la imagen ya publicada

Análisis estático del `Dockerfile` y del contexto de build. **No se descargó, inspeccionó, borró ni publicó
ninguna imagen.**

### Instrucciones `COPY` del `Dockerfile`

| Etapa | Instrucción | Alcance |
|---|---|---|
| `build` (`maven:3.9-eclipse-temurin-17`) | `COPY pom.xml .` (l.3) | solo el POM |
| `build` | `COPY src ./src` (l.5) | **únicamente** el directorio `src/` |
| final (`eclipse-temurin:17-jre-jammy`) | `COPY --from=build /app/target/*.jar app.jar` (l.10) | solo el JAR del artefacto |

**El README no entra en ninguna etapa.** Tampoco `docs/`, `.git/`, `docker-compose.yml` ni `.env.example`:
ninguna `COPY` los menciona y no hay `ADD`.

### Qué llega a la imagen final (verificado sobre el JAR construido localmente con esta misma fuente)

`target/autos-colombia-1.0.0-iteracion1.jar`, 317 entradas:

- Contiene `BOOT-INF/classes/application.yml`, `application-dev.yml`, **`application-test.yml`** y
  `BOOT-INF/classes/db/migration/V1__schema_inicial.sql`, además del código y las dependencias.
- **0 entradas** que coincidan con `README`, `.env`, `docs/` o `.git`.
- `pom.xml` **no** define filtrado de recursos ni `<distributionManagement>` con credenciales, así que Maven
  no sustituye secretos dentro del JAR.
- El `Dockerfile` no usa `ARG`, `ENV`, `ADD` ni `--secret`: **no hay secretos de construcción** declarados.
  Los secretos de operación (`DB_PASSWORD`, `JWT_SECRET`) se esperan **en tiempo de ejecución** por
  variables de entorno, no van en la imagen.

### Consecuencia

**No puede afirmarse que la imagen contenga los valores del README**: el README no se copia en ninguna etapa
y el JAR verificado localmente no lo incluye. Lo que **sí** viaja dentro del JAR son los valores literales
del **perfil de prueba** (`application-test.yml`) y los **hashes BCrypt de las cuentas semilla ficticias**
(`V1`). Son datos de aislamiento y de demostración, no secretos operativos; igual conviene saber que están
extraíbles de la imagen por cualquiera que la descargue.

### Qué NO puede comprobarse sin inspeccionar la imagen

- El contenido real de las capas de `ghcr.io/brayandurango-lgtm/parqueadero-de-autos-en-colombia/autos-colombia`.
- La visibilidad del paquete en GHCR (público o privado) y si alguien lo ha descargado.
- Que el JAR construido por CI a las 15:58 sea idéntico al construido aquí (misma fuente, distinto host y
  caché de dependencias).
- Si el runner inyectó algo adicional durante el `docker build`.

### Hallazgo operativo: falta `.dockerignore`

No existe `.dockerignore`. El `COPY` es explícito, así que **la imagen** no se contamina, pero **el contexto
de build** que se envía al daemon incluye `README.md`, `.git/`, `docs/` y `target/` (decenas de MB).
Consecuencias: builds lentos y cualquier secreto futuro que caiga en la raíz quedaría expuesto al daemon y a
la caché de capas. **Propuesta** (no aplicada): crear `.dockerignore` con `target/`, `.git/`, `docs/`,
`README.md`, `*.md`, `.env*`, `node_modules/` y `*.dump`.

---

## 7. Resultados reales de las pruebas (esta fase)

Ejecutadas 2026-10-30 16:21 -05:00, sin `-Dmaven.test.failure.ignore`.

| Comando | Salida |
|---|---|
| `mvn -B clean verify` | código de salida **0**, **BUILD SUCCESS**, 43,541 s |
| Pruebas | **139 ejecutadas · 139 aprobadas · 0 fallidas · 0 con error · 0 omitidas** |
| JaCoCo | «Analyzed bundle … with 72 classes», reporte regenerado por el propio `verify` |
| `node --test src/test/js/app.test.js` | código de salida **0** · 4 pruebas · 4 pass · 0 fail · 0 skipped |
| Node empleado | **v26.7.0** (medido en la misma shell, `which -a node` → único binario) |

Cobertura de esta corrida (idéntica a la de `01_ESTABILIZACION_PRUEBAS.md`, como se espera: solo se
modificaron docs y el workflow): líneas 865/960 (90,1 %), instrucciones 5.313/9.406 (56,5 %), ramas
144/890 (16,2 %), métodos 546/769 (71,0 %), clases 72/72.

Que la suite pase con `DB_PASSWORD` y `JWT_SECRET` **sin definir** en la shell confirma empíricamente el
requisito del punto 4 del encargo: **CI no necesita secretos de operación para las pruebas aisladas.**

---

## 8. Restricciones respetadas

Sin `git add`, `git commit` ni `git push`. Sin force push ni reescritura de historia. Sin borrar repos,
paquetes ni imágenes. `V1__schema_inicial.sql` sin modificar y sin migraciones. La carpeta original
`Ingenieria - Evidencia de aprendizaje 1` sin tocar. Sin funcionalidades nuevas (usuarios, clientes, celdas).
Sin conectar a PostgreSQL. Sin ejecutar workflows remotos. Sin atribuir rotaciones.

## 9. Propuesta para el próximo commit

Dos commits pequeños, en este orden, y **solo** cuando autorices:

**A — seguridad documental y CI de verificación**
```
chore(ci): limitar el pipeline a verificacion y sanear marcadores del README
```
- `.github/workflows/ci.yml` (reescrito: sin GHCR, sin push de imágenes, `contents: read` + `actions: write`,
  pruebas Node añadidas, un solo `mvn verify`)
- `README.md` (marcadores `<CONFIGURAR_LOCALMENTE>` / `<GENERAR_Y_CONFIGURAR_LOCALMENTE>`, bloque PowerShell
  corregido, semilla marcada como demostración)

**B — bitácoras**
```
docs(iteracion2): registrar rectificacion de credenciales y preparacion de CI
```
- `docs/iteracion2/02_PREPARACION_COMMIT.md` (H1 retirado con su explicación, H2/H3/H4 ajustados,
  atribución ≠ firma)
- `docs/iteracion2/03_CREDENCIALES_Y_CI.md` (este documento)

Mensajes alternativos si prefieres un solo commit: `chore: saneamiento de documentacion y CI de verificacion`.

> **Recordatorio de contexto:** el commit `e701c75` ya publicado **sí** lleva el workflow que construye y
> publica imagen en GHCR en cada push a `main`. Mientras ese archivo siga en `main`, **cualquier push
> publica una imagen**. Por eso conviene que el commit A sea el siguiente push, y que mientras tanto se
> eviten pushes intermedios.
