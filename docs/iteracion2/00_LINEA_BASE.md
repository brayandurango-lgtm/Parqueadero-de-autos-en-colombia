# Línea base de la Iteración 2 — Autos Colombia

Responsable: Brayan Alejandro Durango Urrea (desarrollo e integración técnica).
Este documento registra el punto de partida verificado de la Iteración 2. No contiene
conclusiones de diseño ni decisiones de alcance: solo hechos comprobados en esta ejecución.

---

## 1. Datos de la revisión

| Dato | Valor |
|---|---|
| Fecha y hora real de la revisión | 2026-10-30 15:35 -05:00 (viernes) |
| Carpeta de origen (Iteración 1) | `C:\Users\Administrador\Downloads\Ingenieria - Evidencia de aprendizaje 1` |
| Carpeta de destino (Iteración 2) | `C:\Users\Administrador\Downloads\Parqueadero de autos en colombia` |
| Commit de origen usado como referencia | `c1f79191c44291192554297e173cec623361a443` (`c1f7919`) |
| Autor / fecha del commit de referencia | Alejandro Urrea — 2026-10-19 20:29:25 -05:00 |
| Mensaje del commit de referencia | "Actualizacion completa readme.md enfoque a los diagramas" |
| Estado del árbol de trabajo del origen | Limpio (`git status --short` sin salida). No fue modificado, movido ni borrado |
| Archivos copiados | 106 = exactamente los 106 archivos rastreados por Git en el commit de referencia |
| Verificación de integridad de la copia | `cmp` byte a byte sobre los 106 archivos: **0 diferencias** |

## 2. Repositorio de destino

La copia vive sobre un **repositorio Git independiente y preexistente**, no sobre una copia del
repositorio de origen.

- El directorio `.git` del destino se conservó intacto; no se copió el `.git` del origen.
- Remoto del destino (sin cambios): `origin → https://github.com/brayandurango-lgtm/Parqueadero-de-autos-en-colombia.git`
- Rama actual: `main`, **sin ningún commit** (`your current branch 'main' does not have any commits yet`).
- El repositorio remoto no devolvió ninguna rama en `git ls-remote --heads`: parece vacío. **No verificado
  en la interfaz de GitHub.**
- Consecuencia: los 106 archivos copiados aparecen como **sin rastrear** (`git status` muestra 9 entradas de
  nivel superior). No se ejecutó `git add`, `git commit`, `git push`, `git init`, `git reset` ni `git clean`.
- Estado del origen después de la copia: HEAD sigue en `c1f7919` y árbol limpio.

## 3. Archivos y directorios excluidos de la copia

Criterio de selección: lista de archivos rastreados por Git en `c1f7919` (`git ls-files`). Todo lo no
rastreado quedó fuera, lo que coincide con las exclusiones solicitadas.

| Excluido | Motivo | Existía en el origen |
|---|---|---|
| `target/` | Artefactos generados (`.class`, JAR, JaCoCo, Surefire) | Sí |
| `.git/` | Repositorio del origen; el destino conserva el propio | Sí |
| `.vscode/` | Configuración local del IDE | Sí |
| `.github/modernize/` | Artefactos locales de herramienta de modernización (scripts de hooks); no rastreados por Git | Sí |
| `logs_arranque.txt` | Registro de ejecución local | Sí |
| `S20_Evidencia_Aprendizaje_1_Autos_Colombia.pdf` | Entregable generado; excluido deliberadamente en `.gitignore` del propio proyecto | Sí |
| `.env` | No existe en el origen; no se creó | No |
| `node_modules/` | No existe (el frontend no usa empaquetador) | No |
| Volcados de base de datos / respaldos con datos personales | No se encontró ningún `.dump`, `.bak`, `.sqlite` ni `.db` en el origen | No |

Se copió `.env.example` tras revisar su contenido: las cuatro claves de infraestructura son valores de
entorno local y las dos claves sensibles contienen **marcadores de posición de ejemplo**, no secretos
reales. Por eso no se reproduce su contenido aquí.

## 4. Ajuste aplicado a `.gitignore` (único archivo modificado en esta fase)

El archivo ya cubría `target/`, `*.log`, IDE, `.env`/`.env.*` con excepción `!.env.example`,
`application-local.yml` y el PDF de la Iteración 1. Se añadieron bloques que faltaban y que son riesgo
real en este proyecto:

| Bloque añadido | Justificación |
|---|---|
| `*.pem`, `*.key`, `*.p12`, `*.jks`, `*.keystore`, `id_rsa` | Material clave que puede aparecer al configurar TLS o firmados |
| `docker-compose.override.yml` | Override local de Compose: lugar habitual donde se escriben claves reales de base de datos |
| `*.dump`, `*.bak`, `*.backup`, `pg_dump*`, `db_backup*`, `backup/`, `backups/` | Volcados de base de datos con datos personales. Con nota explícita de que `src/main/resources/db/migration/*.sql` **sí** se versiona |
| `*.csv`, `exports/`, `reportes/` | `ReporteController` exporta CSV con placas, documentos y nombres de propietarios |
| `node_modules/` | Evita una subida accidental si el frontend llega a usar npm |
| `docs/*.pdf` | Regla general: el proyecto ya excluye el PDF de Iteración 1 solo por nombre exacto |
| `*.mdj.backup` | Los respaldos automáticos de StarUML (herramienta pedida para los diagramas) |

Verificación posterior: **ninguno** de los 106 archivos copiados queda ahora ignorado por Git
(`git check-ignore` sobre la lista completa: 0 coincidencias).

## 5. Tecnologías detectadas (verificadas en los archivos copiados)

| Componente | Detalle | Evidencia |
|---|---|---|
| Lenguaje | Java 17 LTS (`java.version` 17) | `pom.xml` L22 |
| Framework backend | Spring Boot 3.2.5 (parent) | `pom.xml` L8-L13 |
| Artefacto | `com.autoscolombia:autos-colombia:1.0.0-iteracion1` | `pom.xml` L15-L17 |
| Persistencia | Spring Data JPA / Hibernate, `ddl-auto: validate` | `pom.xml` L33-L37, `application.yml` L9-L11 |
| Base de datos | PostgreSQL 16 (driver `runtime`), Flyway habilitado | `pom.xml` L54-L65, `application.yml` L16-L19, `docker-compose.yml` |
| Migraciones | Una sola: `src/main/resources/db/migration/V1__schema_inicial.sql` (PostgreSQL, 145 líneas, 7 tablas + semilla) | archivo citado |
| Seguridad | Spring Security 6 + JWT (jjwt 0.12.6), BCrypt, sin estado | `presentation/config/SecurityConfig.java`, `util/JwtUtil.java` |
| Otros | Validación, Actuator, Springdoc 2.5.0, ZXing 3.5.2 (QR), Lombok | `pom.xml` L38-L113 |
| Frontend | HTML/CSS/JS vanilla servido desde `src/main/resources/static/` (misma aplicación, sin separación de despliegue) | `index.html`, `app.js`, `styles.css` |
| Pruebas | JUnit 5 + Mockito + Spring Security Test; **H2 en memoria** en modo PostgreSQL; JaCoCo 0.8.11 | `pom.xml` L115-L132, `application-test.yml` |
| Pruebas de frontend | `node --test` sin dependencias npm | `src/test/js/app.test.js` |
| Contenedores y CI | Dockerfile multi-etapa, docker-compose (postgres + app), GitHub Actions (tests + imagen Docker a GHCR) | `Dockerfile`, `docker-compose.yml`, `.github/workflows/ci.yml` |

Estructura: monolito de tres capas `domain` / `infrastructure` / `presentation` (79 archivos `.java` de
producción, 71 clases analizadas por JaCoCo).

## 6. Entorno de verificación

| Herramienta | Versión | Nota |
|---|---|---|
| Java en `PATH` | 25.0.4.1 LTS | No es la versión del proyecto |
| `JAVA_HOME` | `C:\Program Files\Java\jdk-17` | Maven resuelve JDK 17 correctamente |
| JDK usado por Maven | 17.0.12 (Oracle) | Coincide con `java.version` 17 |
| Maven | 3.9.16 | **El proyecto no incluye Maven Wrapper** (`mvnw` / `.mvn` no existen), por eso se usó `mvn` |
| Node.js | v26.7.0 | Suficiente para `node --test` |
| npm | 11.19.0 | No requerido por el proyecto |
| Caché local | `~/.m2/repository` existente | Maven resolvió las dependencias declaradas sin fallo |

Riesgo de entorno detectado: si una máquina no define `JAVA_HOME`, `mvn` heredaría Java 25 del `PATH`,
para el que Spring Boot 3.2.5 y Lombok no están declarados. La reproducción de esta línea base exige
JDK 17.

## 7. Configuración de base de datos usada por las pruebas (verificación previa)

Antes de ejecutar se comprobó que **ninguna prueba toca PostgreSQL**:

- Las dos únicas clases con `@SpringBootTest` declaran `@ActiveProfiles("test")`
  (`AuthControllerIntegrationTest` L23, `RegistroControllerIntegrationTest` L34).
- `application-test.yml` define `jdbc:h2:mem:testdb;...;MODE=PostgreSQL`, driver H2,
  `ddl-auto: create-drop` y `flyway.enabled: false`.
- No existe `src/test/resources` con configuración adicional; el perfil `test` se resuelve desde
  `src/main/resources`.
- No hay Testcontainers ni `spring.datasource.url` de PostgreSQL en las pruebas.
- Consecuencia conocida y relevante: **las pruebas no validan el DDL real de PostgreSQL**, solo su
  aproximación en modo H2.

No se conectó a PostgreSQL, no se ejecutó migración alguna sobre una base persistente, no se arrancó
Docker y no se modificó ninguna configuración de conexión.

## 8. Resultados reales de las verificaciones

### 8.1 Suite Java — ejecución de línea base

```
mvn -B clean verify          (desde la carpeta de destino)
```

| Métrica | Valor real |
|---|---|
| Pruebas ejecutadas | **136** |
| Aprobadas | **135** |
| Fallidas | **1** |
| Con error | 0 |
| Omitidas | 0 |
| Resultado del build | **BUILD FAILURE** (45,4 s) |
| Cobertura | **No se generó** en esta ejecución: `jacoco:report` no llegó a correr porque Surefire interrumpió el build (`target/site` no existía) |

Prueba fallida:

```
RegistroControllerIntegrationTest.testRegistrarEntrada_Exito  (línea 134)
java.lang.AssertionError: Status expected:<201> but was:<409>
```

Respuesta real obtenida por la prueba: `409 Conflict` con mensaje
"El vehículo con placa XYZ987 no puede circular el día **Viernes** por restricción de Pico y Placa".

Diagnóstico (verificado leyendo el código, no inferido del resultado):
`PicoYPlacaService.puedeTransitar` evalúa `LocalDateTime.now()` y restringe por el último dígito de la
placa (viernes: 7 y 8). `RegistroControllerIntegrationTest` fija la placa `"XYZ987"` (último dígito 7).
Por tanto **la suite es dependiente del calendario: falla todos los viernes** y pasa los demás días.
Esta dependencia es preexistente en la Iteración 1 —la copia es idéntica byte a byte al commit
`c1f7919`— y afecta a GitHub Actions: cualquier `push` en viernes deja el pipeline en rojo.
No se modificó ninguna prueba ni ningún código para obtener este resultado.

### 8.2 Suite Java — segunda ejecución, solo para medir cobertura

```
mvn -B clean verify -Dmaven.test.failure.ignore=true
```

Mismo resultado funcional (**136 pruebas, 1 fallida, 0 errores, 0 omitidas**); el flag permite continuar
hasta `jacoco:report`, que analizó **71 clases**. El build reporta SUCCESS solo porque se ignoró
deliberadamente el fallo conocido: **la línea base real es la de 8.1, que falló.**

Cobertura exacta medida en esta ejecución (`target/site/jacoco/jacoco.csv`, contrastada con la fila
"Total" de `index.html`):

| Métrica | Cubierta | Total | % |
|---|---|---|---|
| Instrucciones | 4.950 | 9.386 | **52,7 %** |
| Ramas | 133 | 890 | **14,9 %** |
| Líneas | 819 | 975 | **84,0 %** |
| Métodos | 496 | 765 | **64,8 %** |

Por paquete (líneas): `presentation.config` 97,7 %, `util` 96,0 %, `presentation.controllers` 92,9 %,
`infrastructure.exception` 90,2 %, `domain.enums` 86,7 %, `domain.service` 86,2 %,
`presentation.dto` 72,1 %, `domain.model` 69,1 %, raíz 33,3 %.

Dos observaciones honestas:
- El README de la Iteración 1 afirma "90,2 % líneas". El valor real medido hoy es **84,0 % líneas** y
  **52,7 % instrucciones**; el 90,2 % coincide exactamente con la cobertura de líneas del paquete de
  excepciones, no con el total. La cifra del README es inexacta.
- La cobertura de ramas (14,9 %) está dominada por el código generado por Lombok (`@Builder`, `@Data`),
  que multiplica las ramas de las entidades y DTO.

### 8.3 Pruebas del frontend estático

```
node --test src/test/js/app.test.js
```

| Métrica | Valor real |
|---|---|
| Pruebas | **4** |
| Aprobadas | **4** |
| Fallidas | 0 |
| Tiempo | 323 ms |

### 8.4 Comprobaciones pendientes (bloqueadas o fuera de esta fase)

Sin verificar a propósito: arranque de la aplicación, conexión a PostgreSQL real, nombre y estado actual
de la base, comportamiento de la interfaz en navegador, imagen Docker y pipeline de GitHub Actions.

## 9. Artefactos generados en la carpeta de destino

- `target/` (creado por las dos ejecuciones de Maven; ignorado por Git).
- `docs/iteracion2/00_LINEA_BASE.md` (este documento).

Ningún archivo de código fuente, recurso, prueba o documentación de la Iteración 1 fue modificado,
borrado ni renombrado en ninguna de las dos carpetas. El único cambio de contenido en la copia es
`.gitignore`, descrito en la sección 4.
