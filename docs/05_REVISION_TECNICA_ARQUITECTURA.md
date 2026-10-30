# Documento de Revisión Técnica y Auditoría de Arquitectura
## Sistema de Información "Autos Colombia" – Iteración 1
**Actividad:** S20 - Evidencia de Aprendizaje 1. Diseño Sistema de Información – Iteración 1  
**Institución:** Institución Universitaria Digital de Antioquia (IU Digital)  
**Asignatura:** Ingeniería de Software  
**Grupo:** [PREICA2602B020081](https://iudigital.instructure.com/courses/28333)  
**Profesor:** Esteban Montoya Molina  
**Rol Responsable:** 1 - Arquitecto Líder de Diseño  
**Estudiante:** Brayan Alejandro Durango Urrea  
**Fecha y Ubicación:** Octubre 2026, Medellín, Antioquia, Colombia  

---

## 1. Resumen Ejecutivo de la Auditoría Técnica

En mi calidad de **Arquitecto Líder de Diseño**, se ejecutó una revisión técnica y arquitectónica exhaustiva del código fuente del backend Java/Spring Boot implementado para la **Iteración 1**.

El objetivo de esta revisión fue:
1. Verificar la conformidad del código implementado contra los modelos de arquitectura (UML, MER, capas).
2. Evaluar el cumplimiento de principios SOLID, Clean Code y estándares de seguridad corporativa.
3. Identificar cuellos de botella, dependencias circulares o defectos de persistencia JPA y corregirlos directamente en el código.
4. Validar la calidad del software mediante la ejecución de la suite de pruebas automatizadas (unitarias e integración).
5. Construir la **Matriz de Trazabilidad Técnica** que conecta los requerimientos funcionales con cada artefacto de software.

**Resultado Global de la Auditoría:** **VERIFICADO LOCALMENTE CON PENDIENTES; NO CERTIFICADO PARA DESPLIEGUE**.
* Pruebas Java ejecutadas: **136 (0 fallos, 0 errores, 0 omitidas)**; pruebas de humo frontend ejecutadas por separado: **4 aprobadas**.
* Cobertura JaCoCo: **90,2% de líneas**, **56,3% de instrucciones** y **16,0% de ramas**. Se cumple RNF-09 de 70% en líneas; elevar instrucciones y ramas queda como mejora.
* Estado de dependencias cíclicas: no se reportaron ciclos de beans durante el arranque de pruebas.

---

## 2. Hallazgos Arquitectónicos y Correcciones Aplicadas

Durante la revisión técnica se detectaron tres (3) aspectos de diseño que requerían corrección arquitectónica inmediata para garantizar la robustez del sistema:

### Hallazgo 1: Dependencia Circular en el Contexto de Spring Security
* **Severidad:** Alta (Bloqueante para el arranque limpio y pruebas de integración).
* **Diagnóstico:** Se presentaba un ciclo cerrado de dependencias entre tres componentes:
  $$\text{AuthService} \longrightarrow \text{SecurityConfig (para PasswordEncoder)} \longrightarrow \text{JwtAuthenticationFilter} \longrightarrow \text{AuthService}$$
  Esto impedía la inicialización del `ApplicationContext` en entornos de prueba automatizados y forzaba la desactivación temporal (`@Disabled`) de las pruebas de integración de controladores.
* **Solución Arquitectónica Implementada:**
  Se aplicó el principio de Inversión de Dependencias y Segregación de Interfaces extrayendo el bean `PasswordEncoder` a una clase de configuración independiente: [`PasswordEncoderConfig.java`](file:///c:/Users/Administrador/Downloads/Ingenieria%20-%20Evidencia%20de%20aprendizaje%201/src/main/java/com/autoscolombia/presentation/config/PasswordEncoderConfig.java).
  Con esto, `AuthService` inyecta directamente `PasswordEncoder` sin tener que esperar a que `SecurityConfig` ni la cadena de filtros HTTP se inicialicen, rompiendo el ciclo de raíz.

---

### Hallazgo 2: Inconsistencia entre Lombok `@Builder` y el Ciclo de Vida JPA
* **Severidad:** Media-Alta (Violación de restricciones de validación en tiempo de ejecución).
* **Diagnóstico:** Las entidades JPA (`Usuario`, `Vehiculo`, `Registro`, `Celda`, `Comprobante`, `Novedad`, `AuditoriaLog`) definían campos obligatorios con valores por defecto (ej. `private LocalDateTime fechaRegistro = LocalDateTime.now();`). Sin embargo, al utilizar el patrón `@Builder` de Lombok sin la anotación `@Builder.Default`, Lombok ignoraba las inicializaciones de campo y seteaba los atributos en `null`. Al persistir la entidad, el motor de Bean Validation (`jakarta.validation`) lanzaba una excepción `ConstraintViolationException` con el mensaje: *"La fecha de registro es obligatoria"*.
* **Solución Arquitectónica Implementada:**
  1. Se agregó la anotación `@Builder.Default` en todos los campos que poseen valores predeterminados de negocio en todas las entidades.
  2. Se implementaron métodos `@PrePersist` en entidades de dominio como salvaguarda defensiva para evitar valores nulos al persistir en PostgreSQL o H2.

---

### Hallazgo 3: Código de Estado HTTP para Peticiones no Autenticadas
* **Severidad:** Menor (Cumplimiento de estándares REST).
* **Diagnóstico:** Por defecto en Spring Security 6, las peticiones sin cabecera `Authorization` a endpoints protegidos retornaban `403 Forbidden` en lugar de `401 Unauthorized`.
* **Solución Arquitectónica Implementada:**
  Se configuró un `AuthenticationEntryPoint` explícito en `SecurityConfig`:
  ```java
  .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) ->
          response.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED, "No autorizado")
  ))
  ```
  Garantizando que cualquier cliente reciba `401 Unauthorized` ante la ausencia o invalidez del token JWT.

---

### Hallazgo 4: Integridad Referencial en Suite de Pruebas de Integración
* **Severidad:** Media (Fallos intermitentes en la limpieza de base de datos de pruebas).
* **Diagnóstico:** Al ejecutarse operaciones que registraban auditorías (`AuditoriaLog`), el método `tearDown` o `setUp` de las pruebas ejecutaba `usuarioRepository.deleteAll()` antes de limpiar las tablas hijas, provocando violaciones de clave foránea (`FK_AUDITORIA_USUARIO`).
* **Solución Arquitectónica Implementada:**
  Se estandarizó el orden estricto de eliminación según las dependencias del MER:
  1. `comprobanteRepository.deleteAll()`
  2. `novedadRepository.deleteAll()`
  3. `registroRepository.deleteAll()`
  4. `auditoriaLogRepository.deleteAll()`
  5. `celdaRepository.deleteAll()`
  6. `vehiculoRepository.deleteAll()`
  7. `usuarioRepository.deleteAll()`
  La última ejecución local reporta 136 pruebas Java aprobadas; además pasan 4 pruebas Node del frontend. El resultado debe repetirse en CI para confirmar reproducibilidad.

---

## 3. Matriz de Trazabilidad Técnica Integral

La siguiente matriz certifica que cada requerimiento funcional cuenta con sus componentes de software correspondientes en todas las capas del sistema:

| ID Req. | Requerimiento Funcional | Controlador REST | Endpoint y Verbo | Servicio de Negocio | Repositorio JPA | Tabla en BD | Pruebas Automatizadas |
|---|---|---|---|---|---|---|---|
| **RF-01** | Autenticación con JWT | `AuthController` | `POST /api/v1/auth/login` | `AuthService` | `UsuarioRepository` | `usuario` | `AuthServiceTest`, `AuthControllerIntegrationTest` |
| **RF-02** | Registro Entrada Vehículo | `RegistroController` | `POST /api/v1/registros/entrada` | `RegistroService` | `RegistroRepository` | `registro`, `vehiculo` | `RegistroServiceTest`, `RegistroControllerIntegrationTest` |
| **RF-03** | Validación Pico y Placa | `RegistroController` | (Interno en entrada) | `PicoYPlacaService` | N/A (Lógica Dominio) | N/A | `PicoYPlacaServiceTest` (15 casos) |
| **RF-04** | Asignación Automática Celda | `RegistroController` | (Interno en entrada) | `CeldaService` | `CeldaRepository` | `celda` | `CeldaServiceTest` |
| **RF-05** | Comprobante Entrada con QR | `ComprobanteController` | `GET /api/v1/comprobantes/{codigo}` | `ComprobanteService` | `ComprobanteRepository` | `comprobante` | `RegistroServiceTest` |
| **RF-06** | Registro Salida Vehículo | `RegistroController` | `PUT /api/v1/registros/salida/{placa}` | `RegistroService` | `RegistroRepository` | `registro` | `RegistroServiceTest` |
| **RF-07** | Liberación de Celda | `RegistroController` | (Interno en salida) | `CeldaService` | `CeldaRepository` | `celda` | `CeldaServiceTest` |
| **RF-08** | Comprobante Salida con QR | `ComprobanteController` | `GET /api/v1/comprobantes/{codigo}` | `ComprobanteService` | `ComprobanteRepository` | `comprobante` | `RegistroServiceTest` |
| **RF-09** | Consulta de Vehículos | `VehiculoController` | `GET /api/v1/vehiculos` | `VehiculoService` | `VehiculoRepository` | `vehiculo` | `VehiculoServiceTest` |
| **RF-10** | Registro de Novedades | `NovedadController` | `POST /api/v1/novedades` | `NovedadService` | `NovedadRepository` | `novedad` | `RegistroServiceTest` |
| **RF-11** | Cancelación de Registro | `RegistroController` | `DELETE /api/v1/registros/{id}` | `RegistroService` | `RegistroRepository` | `registro`, `celda` | `RegistroServiceTest` |
| **RF-12** | Dashboard de Ocupación | `ReporteController` | `GET /api/v1/reportes/dashboard` | `ReporteService` | `CeldaRepository`, `RegistroRepository` | `celda`, `registro` | `CeldaServiceTest` |
| **RF-13** | Reporte Ocupación en CSV | `ReporteController` | `GET /api/v1/reportes/ocupacion` | `ReporteService` | `RegistroRepository` | `registro` | `RegistroServiceTest` |
| **RF-14** | Auditoría de Operaciones | Todos los servicios | (Transversal / Filtro) | `AuditoriaService` | `AuditoriaLogRepository` | `auditoria_log` | Integración general |

---

## 4. Análisis de Principios SOLID y Calidad de Código

1. **Single Responsibility Principle (SRP):**
   * Cada clase posee una única razón de cambio. `PicoYPlacaService` únicamente calcula restricciones; `CeldaService` gestiona la disponibilidad de celdas; `ComprobanteService` crea códigos y códigos QR con ZXing; `AuthService` gestiona credenciales y tokens.
2. **Open/Closed Principle (OCP):**
   * El sistema está abierto a la extensión y cerrado a la modificación. Por ejemplo, `PicoYPlacaService` permite incorporar nuevas reglas o ciudades extendiendo las validaciones sin modificar los controladores de entrada ni los repositorios.
3. **Liskov Substitution Principle (LSP):**
   * Las excepciones heredan de `RuntimeException` y son capturadas polimórficamente por `GlobalExceptionHandler`. Los repositorios extienden `JpaRepository` respetando los contratos de persistencia de Spring Data.
4. **Interface Segregation Principle (ISP):**
   * Interfaces de repositorios granulares y específicas (`UsuarioRepository`, `CeldaRepository`, etc.) sin obligar a los servicios a depender de operaciones que no utilizan.
5. **Dependency Inversion Principle (DIP):**
   * Los controladores dependen de abstracciones de servicios; los servicios dependen de interfaces de repositorio (`@Autowired` / constructor injection con Lombok `@RequiredArgsConstructor`). Se desacopló la configuración del cifrador de contraseñas mediante `PasswordEncoderConfig`.

---

## 5. Auditoría de Seguridad

* **Almacenamiento de Claves:** Implementación de `BCryptPasswordEncoder(10)`. Ninguna contraseña en texto plano se almacena ni se expone en logs ni en DTOs de salida.
* **Protección contra Fuerza Bruta:** Bloqueo temporal en memoria tras 3 intentos fallidos consecutivos por cuenta durante 15 minutos.
* **Tokens JWT:** Firma criptográfica HMAC-SHA256 con clave secreta exteriorizada por variable de entorno `JWT_SECRET`. Claims incluyen sujeto (correo) y rol de usuario.
* **Control de Autorización (RBAC):** Endpoints administrativos (creación de usuarios, exportación de reportes CSV) restringidos al rol `ADMINISTRADOR`.
* **Prevención de Inyección SQL:** Spring Data JPA/Hibernate parametriza las consultas de repositorio; se recomienda mantener parametrizados todos los filtros y consultas dinámicas.

---

## 6. Recomendaciones Técnicas para la Iteración 2

Para los siguientes ciclos de desarrollo a cargo del equipo de trabajo, el Arquitecto Líder recomienda:
1. **Módulo de Tarifas y Facturación:** Incorporar tabla de tarifas diferenciadas por tipo de vehículo y tiempo (cobro por minutos/fracciones y mensualidades recurrentes).
2. **Reconocimiento Óptico de Placas (LPR/OCR):** Evaluar la integración de cámaras de visión artificial en garita para prellenar la placa automáticamente en la interfaz.
3. **Mecanismo de Notificaciones Push/WhatsApp:** Envío automático del comprobante QR al teléfono del propietario registrado al ingresar o salir.
4. **Caché Distribuida con Redis:** Al escalar a múltiples parqueaderos o celdas (>1000 celdas), implementar Redis para cachear el estado de disponibilidad y evitar consultas recurrentes a PostgreSQL.
