# 01 — Estabilización de la línea base de la Iteración 2

**Proyecto:** Sistema de información del parqueadero Autos Colombia
**Fase:** Estabilización de la línea base (eliminación de la dependencia del calendario en pruebas)
**Carpeta de trabajo:** `C:\Users\Administrador\Downloads\Parqueadero de autos en colombia`
**Fecha de ejecución:** 2026-10-30, 15:47 -05:00 (viernes)
**Documento anterior:** `docs/iteracion2/00_LINEA_BASE.md` (registro histórico del estado fallido; se conserva sin modificar)

---

## 1. Problema original

La línea base registrada en `00_LINEA_BASE.md` arrojó **136 pruebas, 1 fallo, `BUILD FAILURE`**.

Prueba que fallaba: `com.autoscolombia.integration.RegistroControllerIntegrationTest.testRegistrarEntrada_Exito`

**Salida real de la corrida de línea base**, tomada del registro de consola de `mvn` capturado durante la
Fase B (el directorio `target/surefire-reports/` ya no conserva ese fallo: las corridas posteriores lo
sobrescribieron):

```
[ERROR] com.autoscolombia.integration.RegistroControllerIntegrationTest.testRegistrarEntrada_Exito -- Time elapsed: 0.476 s <<< FAILURE!
java.lang.AssertionError: Status expected:<201> but was:<409>
	at org.springframework.test.util.AssertionErrors.fail(AssertionErrors.java:59)
	at org.springframework.test.util.AssertionErrors.assertEquals(AssertionErrors.java:122)
	at com.autoscolombia.integration.RegistroControllerIntegrationTest.testRegistrarEntrada_Exito(RegistroControllerIntegrationTest.java:134)
```

> **Corrección documental.** Una versión anterior de este documento transcribió el fallo con el texto
> «No such head found: expected <201> but was <409>». Esa cadena **no existe** en la salida de Surefire y
> no debe atribuirse a la herramienta: fue un error de transcripción al redactar el informe. El texto
> verificado es el bloque de arriba. Los resultados numéricos de la corrida (136 / 1 fallida) no cambian.

**Causa raíz identificada:** `PicoYPlacaService` consultaba la hora del sistema de forma directa
(`LocalDateTime.now()`) dentro de la cadena de producción de la regla. El test de integración usaba la
placa fija `XYZ987` (último dígito **7**) y se ejecutaba un **viernes**. La regla codificada restringe
los dígitos 7 y 8 los viernes, así que la aplicación respondía `409 Conflict` con el mensaje de Pico y
Placa en lugar del `201 Created` esperado.

El fallo **no era un defecto de la regla de negocio**: era una prueba no determinista, cuyo resultado
dependía del día del calendario en que se ejecutaba el build.

> **Corrección documental (segunda).** El borrador de esta sección afirmaba que la prueba fallaba
> «viernes, lunes o martes». **Es inexacto.** Contra el código vigente
> ([PicoYPlacaService.java:50-56](file:///C:/Users/Administrador/Downloads/Parqueadero%20de%20autos%20en%20colombia/src/main/java/com/autoscolombia/domain/service/PicoYPlacaService.java#L50)),
> el dígito **7** está restringido **únicamente el viernes**: lunes (6,9), martes (0,1), miércoles (2,3),
> jueves (4,5) y sábado/domingo (sin restricción) permitían que la prueba pasara. La dependencia del
> calendario es real, pero su frecuencia es **una corrida de cada siete días** (todo viernes), no tres
> días por semana. Eso sigue siendo inaceptable para CI —un push de viernes falla sin que nadie haya
> cambiado nada—, pero la magnitud descrita estaba inflada.


Restricciones impuestas para la corrección (todas respetadas):

- No reemplazar `PicoYPlacaService` completo por un mock en la prueba de integración, para conservar la
  verificación de su lógica real.
- No cambiar la placa del test simplemente para que pasara hoy.
- No modificar los dígitos restringidos, ni las condiciones que permiten el ingreso.
- No ampliar el alcance de la Iteración 2 a causa de este problema.

---

## 2. Solución aplicada

**Inyección de `java.time.Clock` por constructor en `PicoYPlacaService`, con reloj fijo en las pruebas
dependientes del calendario.**

Cambios:

1. `PicoYPlacaService` ahora recibe un `Clock` por constructor (`@RequiredArgsConstructor`, patrón ya
   usado en el resto de servicios). Se agregaron dos métodos de conveniencia que resuelven el instante
   contra el reloj inyectado:
   - `puedeTransitar(String placa)` → delega en `puedeTransitar(placa, LocalDateTime.now(clock))`
   - `obtenerMensajeRestriccion(String placa)` → delega en `obtenerMensajeRestriccion(placa, LocalDateTime.now(clock))`
2. La **lógica de la regla no se tocó**: los métodos de dos argumentos
   `puedeTransitar(String, LocalDateTime)` y `obtenerMensajeRestriccion(String, LocalDateTime)` conservan
   el mismo `switch` por día de semana y los mismos dígitos restringidos. Los nuevos métodos solo aportan
   la fecha/hora; delegan en la lógica existente.
3. Se creó `ClockConfig` como bean de producción con `Clock.systemDefaultZone()`, que es exactamente lo
   que hacía `LocalDateTime.now()` antes del cambio (ver sección 4).
4. `RegistroService.registrarEntrada` pasó a llamar la versión de un argumento, de modo que la fecha/hora
   de la validación proviene del reloj inyectado y no del reloj del sistema de forma dispersa.
5. En la prueba de integración se registró un reloj mutable de test como bean `@Primary` dentro de un
   `@TestConfiguration`, sustituyendo al `Clock` de producción sin borrar la regla real. La prueba fija
   ahora un miércoles para el caso de éxito y un viernes para el caso restringido. Se conservó la placa
   original `XYZ987`.
6. En `PicoYPlacaServiceTest` se construye el servicio con `Clock.fixed(...)`. Las 18 pruebas previas ya
   pasaban fechas explícitas, así que solo requirieron el argumento adicional del constructor.
7. En `RegistroServiceTest` se ajustaron los stubs del mock de la regla al nuevo firm de un argumento (si
   no, Mockito devolvía `false` por defecto y las pruebas de entrada se rompían).

**Por qué se evitó mockear el servicio completo:** la prueba de integración ahora ejercita el `PicoYPlacaService`
real (decisión de día, dígito de placa, mensaje de restricción, reserva de celda y persistencia encadenada);
solo el tiempo se controla. Mockear el servicio habría ocultado la regresión que se quería eliminar.

---

## 3. Archivos modificados

| # | Archivo | Tipo | Qué cambió |
|---|---------|------|-----------|
| 1 | `src/main/java/com/autoscolombia/presentation/config/ClockConfig.java` | **nuevo** | Bean `Clock` de producción con `Clock.systemDefaultZone()` (líneas 21-22). |
| 2 | `src/main/java/com/autoscolombia/domain/service/PicoYPlacaService.java` | modificado | Campo `private final Clock clock;` (l.29) + `@RequiredArgsConstructor`; sobrecargas `puedeTransitar(String)` (l.93) y `obtenerMensajeRestriccion(String)` (l.103) sobre `LocalDateTime.now(clock)`; eliminación de import `LocalTime` sin uso; Javadoc de limitaciones preexistentes. |
| 3 | `src/main/java/com/autoscolombia/domain/service/RegistroService.java` | modificado | Llamadas a la regla en `registrarEntrada` ahora contra el reloj inyectado (l.53-54). |
| 4 | `src/test/java/com/autoscolombia/integration/RegistroControllerIntegrationTest.java` | modificado | `RelojDePrueba` mutable (l.64) y `TestClockConfig` con bean `@Primary` (l.95-104); fechas constantes; caso de éxito fijado a miércoles (l.208); **nuevo** caso de restricción que verifica ausencia de efectos (l.227). De 4 a 5 pruebas. |
| 5 | `src/test/java/com/autoscolombia/service/PicoYPlacaServiceTest.java` | modificado | Factoría `conRelojEn(LocalDateTime)` con `Clock.fixed` (l.21); **2 nuevas** pruebas de las sobrecargas con reloj inyectado (l.33 y l.39). De 18 a 20 pruebas. |
| 6 | `src/test/java/com/autoscolombia/service/RegistroServiceTest.java` | modificado | Stubs del mock de la regla adaptados al firm de un argumento (3 lugares). Sin cambio en el número de pruebas (10). |

Archivos adicionales de documentación y configuración (de fases anteriores, sin efecto en compilación):
`.gitignore` (revisado en Fase A), `docs/iteracion2/00_LINEA_BASE.md` (nuevo, histórico) y este documento.

**No se modificó:** `src/main/resources/db/migration/V1__schema_inicial.sql` (verificado idéntico byte a byte
contra el proyecto de origen), ningún `pom.xml`, ninguna dependencia, ningún contrato de API, ningún archivo
del frontend, ni ningún documento de Isabella.

**Verificación de la huella del cambio:** al comparar los 106 archivos copiados contra el commit `c1f7919` del
proyecto de origen, **exactamente 5 archivos `.java`** difieren (los de la tabla) más `.gitignore`.
`ClockConfig.java` es el único archivo `.java` nuevo.

---

## 4. Zona horaria preservada

**No se cambió la zona horaria de producción.**

- Antes: `LocalDateTime.now()` sin zona explícita → usa `ZoneId.systemDefault()` del JVM.
- Después: `Clock.systemDefaultZone()` → documento oficial de la JDK: *obtains the system clock using the
  default time-zone*. `LocalDateTime.now(Clock.systemDefaultZone())` es **equivalente** a `LocalDateTime.now()`.
  Por tanto el comportamiento temporal de producción es idéntico; no se desplazó ni se fijó ninguna hora.
- **Zona usada en producción:** la zona por defecto de la máquina donde corre la aplicación. En el equipo de
  desarrollo es `America/Bogota` (UTC-05:00, verificada por `java.util.TimeZone.getDefault()` en esta máquina).
  **No está fijada en código**: en un runner de CI (`ubuntu-latest`) la zona por defecto sería `UTC`, y en un
  contenedor Docker depende del `TZ` asignado. Esto queda registrado como limitación pendiente (sección 8),
  no como un cambio introducido ahora.
- **Zona fijada en las pruebas:** `America/Bogota`, de forma explícita, tanto en `RelojDePrueba` como en
  `Clock.fixed(...)` de `PicoYPlacaServiceTest`. Se eligió Bogotá porque la regla está codificada para
  Bogotá D.C.; así el instante de la prueba se resuelve igual que se resolvería en operación local y el
  calendario de la prueba deja de depender del reloj real del ejecutor.

---

## 5. Casos de prueba añadidos o ajustados

### Añadidos

| Prueba | Archivo | Comportamiento verificado |
|--------|---------|---------------------------|
| `testRegistrarEntrada_PicoYPlacaRestringe_NoOcupaCeldaNiCreaRegistro` | `RegistroControllerIntegrationTest` | Con reloj fijado en **viernes 2026-10-30 10:00** y placa `XYZ987` (dígito 7 restringido ese día): responde `409` con mensaje que contiene `Pico y Placa`; y **sin efectos**: `registroRepository.count() == 0`, `comprobanteRepository.count() == 0`, ningún `ACTIVO` para `XYZ987`, y la celda `A-01` permanece `DISPONIBLE`. |
| `testRelojInyectado_DiaPermitido_ConPlacaTerminadaEn7` | `PicoYPlacaServiceTest` | Con `Clock.fixed` en **miércoles 2026-11-04 10:00**, la sobrecarga `puedeTransitar("XYZ987")` devuelve `true` y `obtenerMensajeRestriccion` devuelve `null`. |
| `testRelojInyectado_DiaRestringido_ConPlacaTerminadaEn7` | `PicoYPlacaServiceTest` | Con `Clock.fixed` en **viernes 2026-10-30 10:00**, `puedeTransitar("XYZ987")` devuelve `false` y el mensaje contiene la placa y `Viernes`. |

### Ajustados

| Prueba | Ajuste |
|--------|--------|
| `testRegistrarEntrada_Exito` | Expectativa **inalterada** (`201`) y **placa inalterada** (`XYZ987`). Se fijó el reloj a miércoles 2026-11-04 10:00 (día en que la regla vigente permite el dígito 7). Se conservaron las aserciones de la respuesta (`placa`, `estado ACTIVO`, `numeroCelda A-01`) y se añadieron las verificaciones directas en repositorio de esos mismos efectos persistidos: el `Registro ACTIVO` existe y la celda quedó `OCUPADA`. |
| Resto de `RegistroControllerIntegrationTest` | `@BeforeEach` fija el reloj al miércoles de referencia, para que ningún test dependa del calendario real. |
| `PicoYPlacaServiceTest` (18 pruebas previas) | Solo recibieron el `Clock` en el constructor; conservan sus fechas explícitas y sus expectativas intactas. |
| `RegistroServiceTest` | Stubs de `picoYPlacaService` adaptados al firm de un argumento. |

### Revisión solicitada: ¿las pruebas de Pico y Placa dependen de la fecha real?

**No.** Las 20 pruebas de `PicoYPlacaServiceTest` pasan o construyen una `LocalDateTime` explícita; ninguna
llama a `LocalDateTime.now()` del sistema. La cobertura por día es la que la regla existente distingue:
lunes (6,9), martes (0,1), miércoles (2,3), jueves (4,5), viernes (7,8), sábado y domingo sin restricción, más
los casos de límite (dígito permitido e inmediato restringido por día) y la placa no numérica. La regla no
distingue franjas horarias en su implementación, así que no se inventó ningún caso de horario.

### Estado de la regla de negocio

Sin cambios. Mismos dígitos restringidos por día, mismo tratamiento de sábado/domingo, mismo mensaje, misma
validación presente en `registrarEntrada` (no se eliminó ni se relajó). Ninguna expectativa se adaptó a un
resultado incorrecto.

---

## 6. Comandos ejecutados y resultados reales

Ejecutados desde la carpeta de destino `C:\Users\Administrador\Downloads\Parqueadero de autos en colombia`.
**Sin** `-Dmaven.test.failure.ignore=true`.

### Comando 1

```
mvn -B clean verify
```

| Resultado | Valor |
|-----------|-------|
| Código de salida | **0** |
| Build | **BUILD SUCCESS** |
| Tiempo total | 50,234 s |
| **Pruebas ejecutadas** | **139** |
| **Aprobadas** | **139** |
| **Fallidas** | **0** |
| **Con error** | **0** |
| **Omitidas** | **0** |
| Clases analizadas por JaCoCo | 72 |

Por clase (surefire, todas con 0 fallos / 0 errores / 0 omitidas):

```
GlobalExceptionHandlerTest              7      AuthControllerIntegrationTest        6
RegistroControllerIntegrationTest       5      JwtAuthenticationFilterTest          3
AuthControllerTest                      4      CeldaControllerTest                  7
ComprobanteControllerTest               4      NovedadControllerTest                4
ReporteControllerTest                   2      UsuarioControllerTest                4
VehiculoControllerTest                  5      AuthServiceTest                      7
CeldaServiceTest                        9      NovedadServiceTest                   8
PicoYPlacaServiceTest                  20      RegistroServiceTest                 10
ReporteServiceTest                      5      UsuarioServiceTest                  12
VehiculoServiceTest                    13      JwtUtilTest                          4
                                                            TOTAL = 139
```

**Prueba de que la dependencia del calendario quedó eliminada:** el build se ejecutó el **viernes 2026-10-30
a las 15:47 -05:00**, es decir el mismo día de la semana y la misma condición que provocaba el fallo original,
y `testRegistrarEntrada_Exito` pasó. Antes del cambio, esta misma corrida fallaba.

### Comando 2

```
node --test src/test/js/app.test.js
```

| Resultado | Valor |
|-----------|-------|
| Código de salida | 0 |
| Pruebas | 4 |
| suites | 0 |
| **pass** | **4** |
| **fail** | **0** |
| cancelled / skipped / todo | 0 / 0 / 0 |

**Versión de Node usada en esta corrida — verificación y corrección.** El borrador de esta línea decía
«Node versión v24.18.0». Ese valor **no se midió en la shell donde se ejecutó la prueba**: se copió del
metadato del entorno de la sesión (el runtime propio de la aplicación), por lo que **no hay evidencia de
que `node --test` haya corrido en v24.18.0**. Verificación hecha ahora, en la misma ruta de PATH que
resolvió el comando (`which -a node` → un único binario):

| Comprobación | Resultado |
|---|---|
| Binario que resuelve `node` | `C:\Program Files\nodejs\node` |
| `node --version` actual | **v26.7.0** |
| Versión registrada en `00_LINEA_BASE.md` (Fase B) | v26.7.0 (medida en su momento) |
| Otros binarios `node` en PATH | ninguno |

Conclusión honesta: la versión **corriente verificada es v26.7.0**, y coincide con la de la línea base, así
que **no hubo cambio de Node entre la Fase B y la estabilización**. La discrepancia v26.7.0 / v24.18.0 fue un
error documental de este informe, no un hecho de las corridas. No se puede afirmar cuál versión usó
exactamente la ejecución de las 4 pruebas de `app.test.js` en cada momento porque no se registró
`node --version` dentro del mismo comando; lo verificable hoy es que el único Node instalado en PATH es
v26.7.0.

Sin dependencias de npm (pruebas de humo del JS del frontend).

### Resultado comparado contra la línea base

| Métrica | `00_LINEA_BASE.md` (antes) | Esta ejecución (después) |
|---------|---------------------------|--------------------------|
| Pruebas ejecutadas | 136 | **139** (+3 nuevas) |
| Fallidas | 1 | **0** |
| Maven | `BUILD FAILURE` | **`BUILD SUCCESS`** |
| Node | 4/4 | 4/4 |

El documento `00_LINEA_BASE.md` **no se reemplazó**: conserva el resultado fallido como registro histórico.

---

## 7. Cobertura generada en esta ejecución

Cifras **nuevas**, leídas del reporte producido por esta corrida
(`target/site/jacoco/jacoco.csv` y `target/site/jacoco/jacoco.xml`, marca de tiempo 2026-10-30 15:47:37 -05:00;
JaCoCo 0.8.11). **No se reutilizó ninguna cifra del README ni de reportes anteriores.**

### Totales del proyecto (cada métrica por separado)

| Métrica | Cubierto | Total | % |
|---------|---------:|------:|--:|
| **Líneas** | 865 | 960 | **90,1 %** |
| **Instrucciones** | 5.313 | 9.406 | **56,5 %** |
| **Ramas** | 144 | 890 | **16,2 %** |
| **Métodos** | 546 | 769 | **71,0 %** |
| **Complejidad ciclomática** | 603 | 1.218 | 49,5 % |
| **Clases** | 72 | 72 | **100 %** |

Nota de consistencia: la métrica de líneas del reporte global (960) descuenta líneas atribuidas a varias clases
internas; si se suman las filas por clase del CSV se obtiene 884/979 (90,3 %). Ambas cifras corresponden a esta
misma ejecución; se reporta la del total del informe como cifra oficial del proyecto.

### Cobertura de las clases tocadas por este cambio

| Clase | Líneas | Ramas | Métodos |
|-------|-------:|------:|--------:|
| `PicoYPlacaService` | 22/26 (84,6 %) | 29/38 (76,3 %) | 5/5 (100 %) |
| `RegistroService` | 108/110 (98,2 %) | 11/14 (78,6 %) | 12/16 (75,0 %) |
| `ClockConfig` | 2/2 (100 %) | sin ramas | 2/2 (100 %) |

### Comparación con la línea base (ambas cifras propias de su corrida)

| Métrica | Antes | Después | Delta |
|---------|------:|--------:|------:|
| Líneas | 84,0 % | 90,1 % | +6,1 pp |
| Instrucciones | 52,7 % | 56,5 % | +3,8 pp |
| Ramas | 14,9 % | 16,2 % | +1,3 pp |
| Métodos | 64,8 % | 71,0 % | +6,2 pp |

La cobertura sube porque la corrida anterior abortó en el bloque de integración y esta completa las 139 pruebas
(3 pruebas de integración/unidad adicionales + la que ahora pasa).

### Lectura crítica de la métrica de ramas (16,2 %) — verificada, no atribuida por defecto

El borrador de esta sección presentaba la presencia masiva de ramas sin cubrir en `presentation.dto` como
consecuencia de Lombok, **sin haberlo comprobado**. Ahora está medido.

**Cómo se verificó:** se recorrió `target/site/jacoco/jacoco.xml` (reporte de esta misma corrida) leyendo los
contadores `BRANCH` a nivel de `<method>` dentro de cada `<class>`, y se agruparon las ramas perdidas por
nombre de método. La suma cuadra con el total del informe: 746 ramas perdidas.

| Origen de la rama perdida | Ramas | % de las 746 |
|---|---:|---:|
| Métodos generados por Lombok (`equals` 548, `hashCode` 156, `build` 5) | **709** | **95,0 %** |
| Métodos escritos a mano | **37** | **5,0 %** |

Las 37 ramas restantes sí son huecos reales de prueba y conviene nombrarlos:

| Método | Ramas perdidas | Naturaleza |
|---|---:|---|
| `prePersist` (7 entidades, `@PrePersist` con guarda de `null`) | 13 | Callback de JPA; se cubre una rama, falta la del campo ya poblado |
| `puedeTransitar` (`PicoYPlacaService`) | 5 | Faltan caminos de la regla (p. ej. placa cuyo último carácter no es dígito) |
| `obtenerMensajeRestriccion` | 4 | Rama de día sin restricción |
| `doFilterInternal` (`JwtAuthenticationFilter`) | 3 | Cabeceras de token ausentes/mal formadas |
| `estaBloqueado` | 3 | Estado de usuario |
| `actualizar`, `toDTO` | 2 + 2 | Ramas condicionales |
| `validateToken`, `registrarSalida`, `login`, `registrarIntentoFallido`, `lambda$listar$1` | 1 c/u | Ramas de borde |

**Conclusión corregida:** la métrica global de ramas está dominada por código generado (95 %), por lo que
**no** es un indicador utilizable de la calidad de prueba del dominio. La lógica de negocio escrita a mano
mantiene 87/106 ramas cubiertas en `domain.service`:

| Paquete | Líneas | Ramas |
|---------|-------:|------:|
| `domain.service` | 388/414 (93,7 %) | 87/106 (82,1 %) |
| `presentation.controllers` | 120/127 (94,5 %) | 17/18 (94,4 %) |
| `presentation.config` | 45/46 (97,8 %) | 7/10 (70,0 %) |
| `domain.model` | 99/123 (80,5 %) | 30/48 (62,5 %) |
| `presentation.dto` | 106/129 (82,2 %) | **0/658 (0 %)** |
| `infrastructure.exception` | 74/82 (90,2 %) | 0/46 (0 %) |

Se registra como deuda de medición (excluir Lombok en la configuración de JaCoCo), **no** como defecto de
prueba, y **no** se modifica `pom.xml` en esta fase.

### Qué afirma y qué NO afirma esta cobertura

Estos porcentajes miden **solo** qué líneas y ramas del bytecode fueron ejecutadas al menos una vez durante
la suite. **No** equivalen a calidad total, ni a ausencia de errores, ni a que el sistema funcione en
producción. En concreto, esta corrida **no** demuestra que:

- el DDL real de PostgreSQL sea correcto (las pruebas corren sobre H2);
- la aplicación arranque contra una base PostgreSQL verdadera;
- los flujos de UI respondan correctamente en un navegador;
- los defectos ya documentados de la Iteración 1 (concurrencia en asignación de celda, liberación
  incondicional, cambio manual de estado, usuario inactivo con token válido) estén resueltos. No lo están:
  ninguna prueba actual los ejercita en su condición de fallo.

Una métrica de cobertura alta con pruebas mal elegidas sigue siendo pruebas mal elegidas. El valor real de
esta corrida es otro: **las 139 pruebas son deterministas y pasaron en viernes**.

---

## 8. Limitaciones pendientes

### Derivadas de este cambio (quedan abiertas, no se corrigieron por estar fuera del alcance autorizado)

1. **Zona horaria de producción no fijada.** `Clock.systemDefaultZone()` conserva el comportamiento anterior,
   pero la zona sigue dependiendo del host (Bogotá en desarrollo, UTC en CI, `TZ` del contenedor en Docker).
   Fijar la zona operativa (`Clock.zone(ZONA_OPERATIVA)` + `TZ` en `Dockerfile`/CI) es una decisión de
   configuración que requiere autorización.
2. **Otros `LocalDateTime.now()` del dominio siguen leyendo el reloj del sistema.** En `RegistroService`
   quedan las asignaciones de `fechaHoraEntrada`, `fechaHoraSalida` y las de novedades/auditoría (aprox.
   l.90, 120, 159, 179). **No** forman parte de la regla de Pico y Placa, así que no se tocaron. Esto
   significa que los registros persistidos todavía no son deterministas en pruebas; se resuelve en una fase
   posterior con el mismo patrón.
3. **`PicoYPlacaService` no es reutilizable para otra ciudad** y su Javadoc convive con una regla que no
   valida horarios; ver puntos 4-6.

### Preexistentes, propias de la regla codificada (se documentan, **no** se corrigieron ni se presentan como norma vigente)

4. **La regla está codificada para Bogotá D.C.** Los dígitos restringidos por día corresponden a la medida
   que la implementación del Iteración 1 dejó escrita en su propio Javadoc. **No** se verificó contra la
   normatividad vigente en 2026 y **no** se debe asumir que sea la regla actual. **No** se extendió ni se
   adaptó a Medellín: cualquier cambio de ciudad o de dígitos requiere la definición de requerimiento por
   parte del equipo y una decisión explícita.
5. **No aplica festivos.** La regla solo distingue sábado y domingo; un festivo cae como día hábil y restringe.
6. **Bloquea el día completo.** El Javadoc propio menciona franja 6:00-20:00, pero la implementación ignora la
   hora: a un miércoles restringido no se puede entrar tampoco a las 22:00. Se dejó constancia en el Javadoc
   como limitación preexistente, sin cambiar el comportamiento.

### Alcance y verificación

7. **Las pruebas corren sobre H2 en memoria** (`MODE=PostgreSQL`, Flyway deshabilitado, `ddl-auto: create-drop`).
   Un `BUILD SUCCESS` **no** valida el DDL real de PostgreSQL ni la migración `V1`.
8. **PostgreSQL real sin verificar.** El nombre de la base (`Garajeautomovil` por defecto) sigue sin confirmar;
   no se creó, renombró, borró ni migró ninguna base. En esta máquina `psql` ni siquiera está instalado.
9. **Pendientes de la Iteración 2 que esta corrección no aborda** (por diseño): concurrencia en asignación de
   celda, liberación incondicional de celda, cambio manual de estado de celda, borrado lógico de celdas,
   handler de `DataIntegrityViolationException`, validación de usuario inactivo al validar token, CRUD de
   usuarios y celdas integrado con entrada/salida, entidad `Cliente`, pagos (fuera de esta iteración), y los
   diagramas de secuencia y componentes en StarUML.
10. **Sin operación Git ejecutada por este agente en la fase de estabilización:** no se corrió `add`,
    `commit` ni `push`. El repositorio ya no está sin commits: existe el commit `e701c75` (creado y publicado
    a `origin/main` después de esta corrida) y su push activó el workflow de CI/CD. Ver `02_PREPARACION_COMMIT.md`.

---

## 9. Registro de correcciones documentales

Aplicadas el 2026-10-30 sobre **este** documento, tras la revisión del contenido publicado. **Ninguna
modifica resultados de pruebas, código de producción, pruebas, `pom.xml` ni base de datos.** Las cifras de
las secciones 6 y 7 siguen siendo las de la corrida real del 2026-10-30 15:47 -05:00.

| # | Punto corregido | Qué se afirmó | Qué se corrigió |
|---|---|---|---|
| a | Versión de Node (§6, Comando 2) | «Node versión v24.18.0» | Valor tomado de un metadato de sesión, no medido. Verificado ahora: el único `node` en PATH es `C:\Program Files\nodejs\node` → **v26.7.0**, igual que en la línea base. No hubo cambio de versión entre fases. |
| b | Días en que fallaba la prueba (§1) | «ejecutada un viernes, lunes o martes fallaba» | **Falso.** En el código vigente el dígito 7 solo está restringido el **viernes**. La prueba fallaba un día de cada siete. |
| c | Texto del fallo (§1) | «No such head found: expected <201> but was <409>» | Cadena **inexistente** en la salida real. Se reemplazó por el extracto verificado de consola: `java.lang.AssertionError: Status expected:<201> but was:<409>` en `RegistroControllerIntegrationTest.java:134`. |
| d | Causa de la cobertura de ramas baja (§7) | Atribuida a Lombok **sin verificación** | Medido sobre `jacoco.xml` a nivel de método: **709 de 746 ramas perdidas (95,0 %)** pertenecen a `equals`/`hashCode`/`build` generados; **37 (5,0 %)** a métodos escritos a mano, ahora nombrados uno a uno. |
| e | Alcance de la métrica (§7) | Riesgo de leer la cobertura como calidad | Nueva subsección «Qué afirma y qué NO afirma esta cobertura». |
| f | Estado de Git (§8, punto 10) | «el repositorio … sin commits» | Desactualizado: hoy existe `e701c75` y está publicado en `origin/main` |

La evidencia vigente de cierre y validación se resume en [09_CIERRE_TECNICO_BRAYAN.md](09_CIERRE_TECNICO_BRAYAN.md).

