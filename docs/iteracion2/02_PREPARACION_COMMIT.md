# 02 — Preparación del primer commit y revisión de lo publicado

**Proyecto:** Sistema de información del parqueadero Autos Colombia
**Carpeta:** `C:\Users\Administrador\Downloads\Parqueadero de autos en colombia`
**Fecha:** 2026-10-30, ~16:10 -05:00
**Documento anterior:** `01_ESTABILIZACION_PRUEBAS.md`

---

## 0. Hallazgo que cambia el punto de partida

El objetivo declarado era *preparar* el primer commit **sin** ejecutarlo ni hacer push. Al revisar el
repositorio se encontró que **eso ya ocurrió**, antes de esta revisión:

| Verificación | Resultado |
|---|---|
| `git log` | existe commit **inicial** `e701c759dd25991ed15cb01c75d82df7f18455a9` |
| Mensaje | `Preparacion del proyecto codificativo sobre vehiculos en el primer rol` |
| Autor | `Alejandro Urrea <brayan.durango@est.iudigital.edu.co>` |
| Fecha | 2026-10-30 15:57:40 -0500 |
| Archivos versionados | 109 |
| `git status` | `nothing to commit, working tree clean` (antes de mis ediciones de hoy) |
| Ref remota | `refs/remotes/origin/main` = `e701c75` |
| `.git/logs/refs/remotes/origin/main` | `update by push` → el commit **se envió a GitHub** |
| `git ls-remote --heads <origin>` | `e701c75  refs/heads/main` → el remoto lo tiene |
| GitHub Actions (página pública) | run #1 de `CI/CD Pipeline` en `main`, por `e701c75`, **completado con éxito**, 3m26s, 30 oct 2026 |
| GHCR (página pública) | paquete `parqueadero-de-autos-en-colombia/autos-colombia` **publicado el 30 oct 2026** |

**Consecuencia:** no hay "primer commit" que preparar. Hay un commit publicado, una imagen en GHCR y un
workflow que ya se ejecutó. Nada de esto lo ejecutó este agente. Todo lo que sigue es revisión de ese
estado, y las correcciones pedidas en los puntos 6-7 del encargo.

---

## 1. Carpeta, rama, estado y remoto

| Punto | Valor |
|---|---|
| Carpeta actual | `C:\Users\Administrador\Downloads\Parqueadero de autos en colombia` |
| Rama actual | `main` |
| Estado | limpia salvo las 2 ediciones de hoy (`README.md`, `01_ESTABILIZACION_PRUEBAS.md`) |
| `origin` (fetch y push) | `https://github.com/brayandurango-lgtm/Parqueadero-de-autos-en-colombia.git` |
| Coincidencia con la URL del encargo | **no se pudo comprobar**: el campo quedó como marcador `[PEGA AQUÍ LA URL REAL DEL REPOSITORIO]`, sin URL real |
| Visibilidad del repo | **público** (la página de Actions cargó sin autenticación) |

`origin` es la única remota configurada. Si la URL que tenías en mente es esa misma, hay coincidencia; si
es otra, indícala y **no** cambio el remoto sin confirmación.

### Identidad de Git

`user.name` = `Alejandro Urrea` y `user.email` = la dirección institucional de Brayan. **No** está fijado en
`--local`; proviene de la configuración **global** del equipo. Por eso el commit publicado queda **atribuido**
a nombre de otra persona. Ojo con el vocablo: la atribución de Git es solo texto `Author:` dentro del objeto
commit; **no** implica ninguna firma criptográfica, y tener `user.name` configurado no firma nada. Es un dato
que queda visible en GitHub y afecta la atribución académica del
trabajo; corregirlo (`git config --local user.name ...`) es decisión tuya y no lo toqué.

---

## 2. Contenido del commit publicado (109 archivos)

| Zona | Archivos |
|---|---:|
| `src/main` (Java + recursos + static) | 67 |
| `src/test` (Java + JS) | 21 |
| `docs` (11 `.md`, 2 `.html`, 1 PNG, 1 `.mdj`, 1 Postman `.json`, 2 bitácoras iteración 2) | 14 |
| Raíz (`pom.xml`, `Dockerfile`, `docker-compose.yml`, `README.md`, `.gitignore`, `.env.example`) | 6 |
| `.github/workflows/ci.yml` | 1 |

Por extensión: 80 `.java`, 11 `.md`, 5 `.yml`, 2 `.js`, 2 `.html`, 1 `.xml`, 1 `.sql`, 1 `.png`, 1 `.mdj`,
1 `.json`, 1 `.gitignore`, 1 `.example`, 1 `.css`, 1 sin extensión. Ningún archivo supera 100 KB.

El commit **incluye la estabilización** (Clock + `ClockConfig` + pruebas deterministas) y las dos bitácoras
de `docs/iteracion2/`. Es decir, no es "la Iteración 1 tal cual"; es la Iteración 1 **estabilizada**, que es
justo la referencia que pediste conservar.

### StarUML

Solo existe `docs/staruml/AutosColombia_Iteracion1.mdj` (26.483 bytes, 2026-10-19). **No** hay modelo
`.mdj` de la Iteración 2. No abrí el archivo en StarUML, así que su validez como modelo sigue **sin
verificarse** (el propio README ya lo advierte).

---

## 3. Verificación de exclusiones

| Elemento | Estado | Evidencia |
|---|---|---|
| `.env` | **no existe** en la carpeta y no está versionado | búsqueda directa |
| `.env.local`, `credentials.json`, `service-account.json`, `application-local.yml` | no existen | búsqueda directa |
| `target/` (classes, surefire, jacoco) | **excluido** | `git check-ignore -v target/classes` → `.gitignore:18:target/` |
| Logs y cachés | excluidos por `.gitignore` | revisión de Fase A |
| Dumps y respaldos (`*.dump`, `*.bak`, `pg_dump*`, `backup*/`) | excluidos; `db/migration/*.sql` **sí** versionado a propósito | regla explicada en `00_LINEA_BASE.md` |
| `node_modules/` | excluido (y no existe: el frontend no usa empaquetador) | `.gitignore` |
| Secretos en rutas (`*.pem`, `*.key`, `*.jks`, `id_rsa`) | excluidos | `.gitignore` |
| PDFs de documentación | excluidos (`docs/*.pdf`) | `.gitignore` |
| Archivos de trabajo StarUML (`*.mdj.backup`) | excluidos | `.gitignore` |

Ninguna ruta ignorada aparece en `git ls-files`: **0 archivos ignorados versionados**.

---

## 4. Hallazgos de secretos (ubicación y tipo; valores no reproducidos)

### H1 — RETIRADO: no eran credenciales reales (fue un falso positivo de mi detección)

> ⚠️ **Corrección de este hallazgo.** Al revisar el contenido publicado afirmé que `README.md` exponía
> **credenciales literales reales** y lo clasifiqué como «crítico». **Es incorrecto.** Al abrir los valores,
> los cuatro corresponden a **ejemplos ilustrativos en español** del tipo «una clave local» / «una clave de
> firma larga para desarrollo», no a secretos operativos.

| Ubicación | Variable | Qué hay realmente |
|---|---|---|
| `README.md:150` | `DB_PASSWORD` | marcador ilustrativo de 15 caracteres, texto descriptivo en español |
| `README.md:151` | `JWT_SECRET` | marcador ilustrativo de 40 caracteres, texto descriptivo en español |
| `README.md:176` | `DB_PASSWORD` | otro marcador ilustrativo (14 caracteres) — **redacción distinta** de la línea 150, no un secreto distinto |
| `README.md:177` | `JWT_SECRET` | mismo marcador ilustrativo que la línea 151 |

**Por qué me equivoqué:** clasifiqué los valores con un script que medía longitud y clases de caracteres y
buscaba patrones de marcador en inglés (`example`, `placeholder`, `changeme`, `tu_`, `my_`). Los textos del
README están en español (`una-clave-…`, `la-clave-…`) y no contenían espacios, así que el clasificador los
marcó como «sin patrón de marcador» y su longitud (40 caracteres con guiones) produjo una entropía aparente
alta. **La entropía de un texto descriptivo no es evidencia de secreto.** La lección operativa es la que
diste en el encargo: hay que leer el valor, no inferir su naturaleza por su forma.

**Consecuencias corregidas:**

- **No hay credenciales reales publicadas en el README.** La rotación que plantear como «urgente» en esta
  versión del documento **no corresponde a estos cuatro valores**.
- `DB_NAME` (línea 174) y `DB_USER` (línea 175) tampoco son secretos: son el nombre de base por defecto que
  también aparece en `application.yml` y el usuario `postgres`. **Sigue sin verificarse** contra un servidor
  real de PostgreSQL (no hay `psql` en esta máquina y no se autoriza conectar).
- El resto de este documento y la sección 5 siguen valiendo tal como están escritos.

**Lo que sí queda pendiente**, aunque no sea una filtración: los marcadores ilustrativos son ambiguos como
instrucción de instalación (un lector podría copiarlos literalmente). Se reemplazaron por marcadores
explícitos `<CONFIGURAR_LOCALMENTE>` / `<GENERAR_Y_CONFIGURAR_LOCALMENTE>` en la fase documentada en
`03_CREDENCIALES_Y_CI.md`.

**Rotación:** ninguna rotación fue ejecutada ni afirmada por este agente. El estado de los valores reales de
operación lo gestionas tú de forma privada; hasta que lo confirmes, este documento debe decir
**«rotación: pendiente / sin confirmar»**.


### H2 — Bajo: clave de firma JWT literal en una prueba

[src/test/java/com/autoscolombia/util/JwtUtilTest.java:14](file:///C:/Users/Administrador/Downloads/Parqueadero%20de%20autos%20en%20colombia/src/test/java/com/autoscolombia/util/JwtUtilTest.java#L14)
define una constante de clave de 57 caracteres usada solo dentro de la prueba (no se conecta a nada). Es
práctica aceptable en un test, **siempre que no coincida** con el `JWT_SECRET` de operación. Ese cotejo
**no** se hizo: este agente no abre ni compara tus secretos privados. Queda como comprobación tuya.

### H3 — Correcto: configuración sin secretos

- `src/main/resources/application.yml` (líneas 5, 6, 7, 28) y `application-dev.yml` (3, 4, 5): **todas** las
  credenciales se resuelven por `${VAR}` de entorno. Ningún literal.
- `docker-compose.yml` (líneas 7, 30, 31): referencia a variables de entorno. Sin literales.
- `.env.example`: 7 líneas, sin valores reales.
- Ningún archivo versionado coincide con rutas de clave/credencial (`.pem`, `.key`, `id_rsa`, dumps).
- `src/main/resources/application-test.yml` (líneas 6 y 15) sí define un password y un secret **del perfil de
  prueba** (base H2 en memoria y firma JWT de test). Son valores de aislamiento, no de operación; igual
  conviene que no coincidan con los reales. Este agente no los comparó.

### H4 — Media: contraseña de las cuentas semilla documentada en texto claro

`README.md` tenía la sección «🔐 Credenciales de Prueba (Semilla)» mostrando en **texto claro** la contraseña
compartida por las dos cuentas semilla que inserta `V1__schema_inicial.sql`, junto con sus correos.

- **No es una filtración de un secreto operativo:** son **datos ficticios de demostración** del ambiente
  académico.
- **Sí es un riesgo de configuración:** cualquiera que lea el README puede autenticarse como `admin` contra
  cualquier instalación que se haya desplegado **sin cambiar esas cuentas**. Por eso la sección se reescribió
  para declarar explícitamente que son credenciales de demostración y **no** son seguras fuera de la máquina
  local (ver `03_CREDENCIALES_Y_CI.md`).
- Relacionado, sin cambios por estar fuera de alcance:
  [UsuarioController.java:39](file:///C:/Users/Administrador/Downloads/Parqueadero%20de%20autos%20en%20colombia/src/main/java/com/autoscolombia/presentation/controllers/UsuarioController.java#L39)
  asigna esa misma contraseña por defecto al crear usuarios. Es un defecto de diseño ya registrado (R7/Q4),
  no una exposición nueva.


---

## 5. Revisión de `.github/workflows/ci.yml` (sin modificar, sin ejecutar)

**Eventos disparadores** ([ci.yml:3-7](file:///C:/Users/Administrador/Downloads/Parqueadero%20de%20autos%20en%20colombia/.github/workflows/ci.yml#L3)):

- `push` a `main`
- `pull_request` dirigido a `main`

**Permisos** (l.9-11): `contents: read`, **`packages: write`** ← permiso de publicación de imágenes.

**Trabajo `test`** (l.14-39): JDK 17 temurin → `mvn clean verify` → `mvn jacoco:report` → sube artefacto
`jacoco-report`.

**Trabajo `build`** (l.41-83, `needs: test`): `mvn clean package -DskipTests` → calcula
`IMAGE_TAG=ghcr.io/<repo en minúsculas>/autos-colombia:latest` → `docker/build-push-action@v5` con
**`push: true`**, autenticado con `secrets.GITHUB_TOKEN`.

### Comportamiento real al hacer push

Cada `push` a `main` **construye y publica una imagen en GHCR** como `:latest`, sin aprobación previa y sin
distintivo de versión. **Ya ocurrió**: el push de `e701c75` publicó
`parqueadero-de-autos-en-colombia/autos-colombia` el 30-10-2026. Un push experimental publica imagen.

### Brechas observadas

1. Las pruebas de frontend (`node --test src/test/js/app.test.js`) **no** están en CI; el pipeline solo
   verifica Maven. La suite que stabilizamos no se ejecuta completa en CI.
2. `mvn clean verify` dos veces (`test` y `build`) ⇒ tiempo y caché duplicados.
3. Solo etiqueta `:latest`: sin SHA ni versión, y `cache-from: type=gha,mode=max` mantiene cachés grandes.
4. Sin `concurrency`, así que dos pushes seguidos corren dos publicaciones.
5. CI corre con zona `UTC`; en local se opera en `America/Bogota`. Con `Clock.systemDefaultZone()` la zona
   sigue dependiendo del host: es el punto 1 de las limitaciones de `01_ESTABILIZACION_PRUEBAS.md`.

### Propuesta de separación (no aplicada, para tu aprobación)

- **`ci.yml` → solo verificación.** Renombrar a `Verify`. Disparadores `push` a `main` y `pull_request`.
  Un único trabajo con JDK 17 + Node 26 (misma versión local) que ejecute:
  `mvn -B clean verify` y `node --test src/test/js/app.test.js`, y suba JaCoCo como artefacto.
  Permisos: `contents: read` (quitar `packages: write`).
- **`release.yml` → solo publicación.** Trabajo de imagen Docker en un archivo aparte, disparado por
  `workflow_dispatch` (manual) y/o una etiqueta `v*`, nunca por `push` común. Publicar `:latest` **y** el SHA
  corto. Idealmente con un *environment* de GitHub que exija aprobación.
- Beneficio: un commit académico o un push de prueba deja de publicar imágenes y deja de gastar GHCR.

---

## 6. Propuesta de commit (corregida según el estado real)

El mensaje que sugeriste describe exactamente lo que **ya** quedó publicado:

```
chore: establecer base de iteracion 1 con pruebas deterministas
```

Como ese commit existe (`e701c75`), propongo usarlo en retrospectiva y titular el siguiente:

**Commit A — documentación (seguro, se puede preparar ahora):**
```
docs: actualizar informacion academica a iteracion 2 y corregir registro de estabilizacion
```
Contenido: `README.md` (bloque de información académica → S25 / Iteración 2; la fecha de entrega se fijó luego
en «Pendiente de confirmar», ver `03_CREDENCIALES_Y_CI.md` §3.4) y
`docs/iteracion2/01_ESTABILIZACION_PRUEBAS.md` (correcciones a-e) y este `02_PREPARACION_COMMIT.md`.

**Commit B — seguridad (decisión pendiente, no lo preparo hasta que autorices):**
```
fix(docs): reemplazar credenciales literales del README por marcadores
```
Requiere primero la rotación de secretos (H1). Retirar el texto sin rotar no elimina la exposición.

Nada de esto se ejecuta sin tu aprobación.

---

## 7. Estado de esta revisión

- No se ejecutó `git add`, `git commit` ni `git push`.
- No se cambió el remoto.
- No se modificó código de producción, pruebas, `pom.xml`, `.github/workflows/ci.yml` ni la base de datos.
- No se ejecutó Docker ni el workflow.
- Se modificaron **solo** dos archivos: `README.md` (información académica pedida) y
  `docs/iteracion2/01_ESTABILIZACION_PRUEBAS.md` (correcciones a-e del punto 6 del encargo).
- `docs/iteracion2/00_LINEA_BASE.md` permanece **sin alterar**: conserva 136 pruebas / 1 fallo / BUILD FAILURE.
