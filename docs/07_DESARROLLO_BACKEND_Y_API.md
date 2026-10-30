# Documento de Desarrollo Backend y Especificación de API REST
## Sistema de Información "Autos Colombia" – Iteración 1
**Actividad:** S20 - Evidencia de Aprendizaje 1. Diseño Sistema de Información – Iteración 1
**Institución:** Institución Universitaria Digital de Antioquia (IU Digital)
**Asignatura:** Ingeniería de Software
**Grupo:** [PREICA2602B020081](https://iudigital.instructure.com/courses/28333)
**Profesor:** Esteban Montoya Molina
**Rol Responsable:** 3 - Desarrollador Backend (Modalidad Individual)
**Estudiante:** Brayan Alejandro Durango Urrea
**Fecha y Ubicación:** Octubre 2026, Medellín, Antioquia, Colombia

---

## 1. Resumen Ejecutivo del Rol de Desarrollador Backend

En mi rol como **Desarrollador Backend**, he implementado la totalidad de la lógica de negocio, persistencia relacional, seguridad de acceso y servicios REST para la **Iteración 1 (Gestión de Entrada y Salida de Vehículos)** del sistema **Autos Colombia**.

El backend está construido bajo el ecosistema **Java 17 LTS** y **Spring Boot 3.2.5**, adoptando una arquitectura en capas y persistencia en **PostgreSQL** (base local `Garajeautomovil`; PostgreSQL 16 en Docker), con migraciones de esquema mediante **Flyway**.

La interfaz web operativa se sirve desde `src/main/resources/static/` (HTML, CSS y JavaScript sin framework) y consume la API en el mismo origen. Es un frontend web implementado, pero no sustituye el prototipo exigido en Figma.

---

## 2. Arquitectura de Código Fuente y Organización en Capas

El código fuente se organiza dentro del paquete base `com.autoscolombia` con una estructura estrictamente modular:

```
com.autoscolombia/
├── AutosColombiaApplication.java         # Clase principal de arranque Spring Boot
├── domain/                               # Capa de Dominio y Lógica de Negocio
│   ├── enums/                            # Tipos enumerados de negocio
│   │   ├── EstadoCelda.java              # DISPONIBLE, OCUPADA, MANTENIMIENTO
│   │   ├── EstadoRegistro.java           # ACTIVO, CERRADO, CANCELADO
│   │   ├── RolUsuario.java               # ADMINISTRADOR, OPERADOR
│   │   ├── TipoNovedad.java              # RAYON, GOLPE, DANO_MECANICO, OBJETO_OLVIDADO, OTRO
│   │   └── TipoVehiculo.java             # CARRO, MOTO, CAMIONETA
│   ├── model/                            # Entidades de persistencia JPA
│   │   ├── AuditoriaLog.java             # Trazabilidad inmutable de eventos
│   │   ├── Celda.java                    # Bahías físicas de parqueo
│   │   ├── Comprobante.java              # Tickets emitidos con códigos QR
│   │   ├── Novedad.java                  # Reporte de incidencias y evidencias
│   │   ├── Registro.java                 # Transacciones de entrada y salida
│   │   ├── Usuario.java                  # Cuentas de operadores y administradores
│   │   └── Vehiculo.java                 # Automotores admitidos en el sistema
│   └── service/                          # Interfaces y servicios de aplicación
│       ├── AuditoriaLogService.java      # Registro de auditoría transversal
│       ├── AuthService.java              # Lógica de login, JWT y control de intentos
│       ├── CeldaService.java             # Asignación y liberación de celdas
│       ├── ComprobanteService.java       # Generación de tickets y QR con ZXing
│       ├── NovedadService.java           # Gestión de incidencias en vehículos
│       ├── PicoYPlacaService.java        # Validación algorítmica de restricción distrital
│       ├── RegistroService.java          # Entrada, salida, liquidación de permanencia
│       ├── ReporteService.java           # Métricas de ocupación y exportación CSV
│       └── VehiculoService.java          # Registro y búsqueda de vehículos
├── infrastructure/                       # Capa de Infraestructura y Datos
│   ├── exception/                        # Manejo global y excepciones personalizadas
│   │   ├── BadRequestException.java      # Errores 400 por violaciones de negocio
│   │   ├── ConflictException.java        # Errores 409 por registros activos duplicados
│   │   ├── ErrorResponse.java            # DTO uniforme de error (RFC 7807)
│   │   ├── GlobalExceptionHandler.java   # @RestControllerAdvice transversal
│   │   ├── ResourceNotFoundException.java# Errores 404 por recursos no encontrados
│   │   └── UnauthorizedException.java    # Errores 401 por credenciales inválidas
│   └── repository/                       # Repositorios Spring Data JPA
│       ├── AuditoriaLogRepository.java
│       ├── CeldaRepository.java
│       ├── ComprobanteRepository.java
│       ├── NovedadRepository.java
│       ├── RegistroRepository.java
│       ├── UsuarioRepository.java
│       └── VehiculoRepository.java
├── presentation/                         # Capa de Presentación y Exposición REST
│   ├── config/                           # Configuraciones de seguridad y utilitarias
│   │   ├── CorsConfig.java               # Políticas de Cross-Origin Resource Sharing
│   │   ├── JwtAuthenticationFilter.java  # Filtro HTTP para interceptar y validar JWT
│   │   ├── OpenApiConfig.java            # Metadata para Swagger / OpenAPI 3.0
│   │   ├── PasswordEncoderConfig.java    # Bean desacoplado de hashing BCrypt
│   │   └── SecurityConfig.java           # Configuración de Spring Security 6 (Stateless)
│   ├── controllers/                      # Controladores REST API
│   │   ├── AuthController.java           # /api/v1/auth
│   │   ├── CeldaController.java          # /api/v1/celdas
│   │   ├── ComprobanteController.java    # /api/v1/comprobantes
│   │   ├── NovedadController.java        # /api/v1/novedades
│   │   ├── RegistroController.java       # /api/v1/registros
│   │   ├── ReporteController.java        # /api/v1/reportes
│   │   └── VehiculoController.java       # /api/v1/vehiculos
│   └── dto/                              # Data Transfer Objects validados
│       ├── request/                      # DTOs de entrada con Bean Validation
│       └── response/                     # DTOs de salida enriquecidos
└── util/                                 # Utilidades generales
    └── JwtUtil.java                      # Generación, firma y parseo de tokens JWT
```

---

## 3. Catálogo Completo de la API REST

La API REST cumple con los principios de diseño pragmático, códigos de estado HTTP semánticos y versionado en la ruta (`/api/v1/`).

### 3.1 Módulo de Autenticación (`/api/v1/auth`)

| Verbo | Ruta | Permiso Requerido | Descripción | Códigos HTTP |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Público (`permitAll`) | Autentica un usuario con email y password, retornando un token JWT Bearer válido por 8 horas. | `200 OK`, `400 Bad Request`, `401 Unauthorized` |
| `GET` | `/api/v1/auth/me` | Autenticado (`OPERADOR`, `ADMIN`) | Retorna el perfil y rol del usuario actualmente autenticado. | `200 OK`, `401 Unauthorized` |

#### Ejemplo de Petición Login:
```json
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "operador@autoscolombia.com",
  "password": "clave123"
}
```

#### Ejemplo de Respuesta Exitosa:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJvcGVyYWRvckBhdXRvc2NvbG9tYmlhLmNvbSIsImlkIjoxLCJyb2wiOiJPUEVSQURPUiIsIm5vbWJyZSI6Ikp1YW4gT3BlcmFkb3IiLCJleHAiOjE3NjEwMzY4MDB9...",
  "tipo": "Bearer",
  "email": "operador@autoscolombia.com",
  "nombre": "Juan Operador",
  "rol": "OPERADOR",
  "expiracionMs": 28800000
}
```

---

### 3.2 Módulo de Registros de Entrada y Salida (`/api/v1/registros`)

| Verbo | Ruta | Permiso Requerido | Descripción | Códigos HTTP |
|---|---|---|---|---|
| `POST` | `/api/v1/registros/entrada` | `OPERADOR`, `ADMIN` | Admisión vehicular: valida placa, evalúa Pico y Placa, asigna celda física atómica, crea registro `ACTIVO` y emite ticket QR. | `201 Created`, `400 Bad Request`, `409 Conflict` |
| `PUT` | `/api/v1/registros/salida/{placa}` | `OPERADOR`, `ADMIN` | Egreso vehicular: busca registro activo, calcula tiempo de estancia, libera celda y marca registro `CERRADO`. | `200 OK`, `404 Not Found` |
| `GET` | `/api/v1/registros/activos` | `OPERADOR`, `ADMIN` | Lista todos los vehículos actualmente estacionados con su celda y hora de entrada. | `200 OK` |
| `GET` | `/api/v1/registros/ultimos` | `OPERADOR`, `ADMIN` | Retorna los últimos 10 movimientos registrados en garita. | `200 OK` |
| `DELETE` | `/api/v1/registros/{id}` | `ADMINISTRADOR` | Cancela un registro erróneo, liberando la celda asociada y dejando trazabilidad en auditoría. | `200 OK`, `404 Not Found` |

#### Ejemplo de Petición de Entrada:
```json
POST /api/v1/registros/entrada
Content-Type: application/json
Authorization: Bearer <TOKEN_JWT>

{
  "placa": "ABC123",
  "tipoVehiculo": "CARRO",
  "marca": "Mazda",
  "modelo": "3 Grand Touring",
  "color": "Rojo",
  "observaciones": "Ingreso normal por garita principal",
  "nombrePropietario": "Carlos Gómez",
  "telefonoPropietario": "3001234567"
}
```

#### Ejemplo de Respuesta de Entrada:
```json
{
  "id": 1,
  "placa": "ABC123",
  "tipoVehiculo": "CARRO",
  "codigoCelda": "A-01",
  "pisoCelda": 1,
  "fechaEntrada": "2026-10-19T17:30:00",
  "estado": "ACTIVO",
  "tienePicoYPlaca": false,
  "alertaPicoYPlaca": "El vehículo NO tiene restricción de Pico y Placa hoy.",
  "comprobante": {
    "codigo": "CMP-20261019-ABC123",
    "tipo": "ENTRADA",
    "fechaEmision": "2026-10-19T17:30:00",
    "codigoQrBase64": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMgAAADIAQMAAACGbtxw..."
  }
}
```

---

### 3.3 Módulo de Celdas y Patios (`/api/v1/celdas`)

| Verbo | Ruta | Permiso Requerido | Descripción | Códigos HTTP |
|---|---|---|---|---|
| `GET` | `/api/v1/celdas` | `OPERADOR`, `ADMIN` | Retorna la totalidad de bahías de parqueo con su código, tipo, piso y estado (`DISPONIBLE`, `OCUPADA`, `MANTENIMIENTO`). | `200 OK` |
| `GET` | `/api/v1/celdas/disponibles` | `OPERADOR`, `ADMIN` | Filtra celdas libres por tipo de vehículo opcional (`?tipo=CARRO`). | `200 OK` |

---

### 3.4 Módulo de Vehículos (`/api/v1/vehiculos`)

| Verbo | Ruta | Permiso Requerido | Descripción | Códigos HTTP |
|---|---|---|---|---|
| `GET` | `/api/v1/vehiculos` | `OPERADOR`, `ADMIN` | Consulta el padrón vehicular con filtros por placa, propietario o tipo de vehículo. | `200 OK` |
| `GET` | `/api/v1/vehiculos/{placa}` | `OPERADOR`, `ADMIN` | Obtiene el historial y ficha técnica de un vehículo específico por placa. | `200 OK`, `404 Not Found` |

---

### 3.5 Módulo de Novedades e Incidencias (`/api/v1/novedades`)

| Verbo | Ruta | Permiso Requerido | Descripción | Códigos HTTP |
|---|---|---|---|---|
| `POST` | `/api/v1/novedades` | `OPERADOR`, `ADMIN` | Registra una novedad física sobre un vehículo con estancia activa (tipo de daño, descripción, foto URL). | `201 Created`, `400 Bad Request`, `404 Not Found` |
| `GET` | `/api/v1/novedades/pendientes` | `OPERADOR`, `ADMIN` | Lista todas las incidencias abiertas sin resolver. | `200 OK` |
| `GET` | `/api/v1/novedades/registro/{registroId}` | `OPERADOR`, `ADMIN` | Retorna todas las novedades asociadas a una estancia específica. | `200 OK` |

---

### 3.6 Módulo de Reportes y Métricas (`/api/v1/reportes`)

| Verbo | Ruta | Permiso Requerido | Descripción | Códigos HTTP |
|---|---|---|---|---|
| `GET` | `/api/v1/reportes/dashboard` | `OPERADOR`, `ADMIN` | Estadísticas consolidadas: total de celdas, celdas ocupadas, disponibles, ocupación porcentual global y por tipología. | `200 OK` |
| `GET` | `/api/v1/reportes/ocupacion?desde=AAAA-MM-DD&hasta=AAAA-MM-DD` | Requiere autenticación | Descarga de reporte operacional CSV para el intervalo indicado. En el estado actual de `SecurityConfig`, no hay restricción explícita de rol administrador para esta ruta. | `200 OK` (archivo `text/csv`) |

**Notas de alcance verificadas en código:** `RegistroDTO` no incluye el comprobante ni el resultado de Pico y Placa; el QR de entrada se obtiene en una segunda llamada `GET /api/v1/comprobantes?registroId={id}`. El endpoint de comprobantes `/api/v1/comprobantes/{codigo}/pdf` actualmente retorna texto Base64 simulado, no un PDF. La documentación no debe interpretarlo como generación de PDF real.

---

## 4. Servicios Transaccionales y Reglas de Negocio Implementadas

### 4.1 Lógica de Pico y Placa Distrital (`PicoYPlacaService`)
Se implementó un algoritmo robusto que evalúa la normativa de restricción vehicular:
1. Extrae el último carácter numérico de la placa (manejando tanto placas de carro `ABC123` como de moto `ABC12D`).
2. Determina el día de la semana (`DayOfWeek`).
3. En fines de semana (`SATURDAY`, `SUNDAY`) y festivos dictamina `tieneRestriccion = false`.
4. De lunes a viernes verifica la regla de días calendario pares/impares:
   * **Días pares:** Placas terminadas en `0, 2, 4, 6, 8` tienen restricción.
   * **Días impares:** Placas terminadas en `1, 3, 5, 7, 9` tienen restricción.
5. Permite parametrización por horario (6:00 a.m. a 9:00 p.m.).

### 4.2 Asignación Atómica y Liberación de Celdas (`CeldaService`)
Para prevenir condiciones de carrera cuando dos operadores registran vehículos al mismo milisegundo:
* Se utiliza búsqueda con ordenamiento por `id ASC` filtrando por `estado = 'DISPONIBLE'` y `tipoVehiculo = :tipo`.
* La operación se encapsula en `@Transactional` asegurando que la celda cambie a `OCUPADA` dentro de la misma transacción en que se inserta el `Registro`.
* Al registrar la salida, la celda se actualiza a `DISPONIBLE` y se desvincula del registro activo.

### 4.3 Generación de Comprobantes con Códigos QR (`ComprobanteService`)
* Se integra la librería **Google ZXing (`core` y `javase`)**.
* Los códigos QR se generan en formato PNG con una matriz de $250 \times 250$ píxeles.
* La imagen se convierte a **Base64** (`data:image/png;base64,...`) para permitir su renderizado instantáneo en interfaces web, aplicaciones móviles o tickets térmicos sin requerir almacenamiento en disco ni dependencias externas.

---

## 5. Seguridad y Control de Acceso (Spring Security 6)

1. **Arquitectura Sin Estado (Stateless):** `SessionCreationPolicy.STATELESS`. El servidor no almacena sesiones HTTP en memoria.
2. **Tokens JWT Criptográficos:** Firmados mediante algoritmo HMAC-SHA256 con llave secreta configurable por variable de entorno `JWT_SECRET`.
3. **Filtro `JwtAuthenticationFilter`:** Intercepta cada petición HTTP, extrae el token de la cabecera `Authorization: Bearer`, valida firma y expiración, y establece la identidad en el `SecurityContextHolder`.
4. **Protección de Contraseñas:** Algoritmo `BCryptPasswordEncoder` con factor de trabajo 10, garantizando que ninguna contraseña viaje ni se almacene en texto claro.
5. **Manejo Centralizado de Excepciones de Seguridad:** Retorno estricto de `401 Unauthorized` para peticiones sin token o con token vencido, y `403 Forbidden` para accesos con rol insuficiente.

---

## 6. Persistencia de Datos y Control de Versiones con Flyway

El esquema de base de datos se encuentra versionado mediante el script Flyway [`V1__schema_inicial.sql`](file:///c:/Users/Administrador/Downloads/Ingenieria%20-%20Evidencia%20de%20aprendizaje%201/src/main/resources/db/migration/V1__schema_inicial.sql).

### Características Técnicas del Esquema:
* Motor relacional **PostgreSQL** con soporte transaccional ACID.
* Codificación de caracteres `utf8mb4` con cotejamiento `utf8mb4_unicode_ci` para soporte de acentos, tildes y caracteres especiales.
* Claves foráneas con integridad referencial estricta (`ON DELETE RESTRICT`, `ON UPDATE CASCADE`).
* Índices de rendimiento estratégicos:
  * `idx_usuario_email` en tabla `usuario`.
  * `idx_vehiculo_placa` en tabla `vehiculo`.
  * `idx_celda_codigo` y `idx_celda_tipo_estado` en tabla `celda`.
  * `idx_registro_placa_estado` en tabla `registro`.
  * `idx_comprobante_codigo` en tabla `comprobante`.
* Datos semilla precargados:
  * 2 usuarios del sistema (`admin@autoscolombia.com` y `operador@autoscolombia.com`).
  * 30 celdas de parqueo distribuidas en 3 pisos (20 para carros, 6 para motos, 4 para camionetas).

---

## 7. Despliegue con Docker y Docker Compose

### 7.1 Dockerfile Multi-Stage Optimizado
El archivo [`Dockerfile`](file:///c:/Users/Administrador/Downloads/Ingenieria%20-%20Evidencia%20de%20aprendizaje%201/Dockerfile) utiliza construcción en múltiples etapas:
1. **Etapa 1 (Build):** Imagen `maven:3.9-eclipse-temurin-17-alpine` que compila el código fuente y genera el archivo JAR sin pruebas para optimizar velocidad.
2. **Etapa 2 (Runtime):** Imagen ultraligera `eclipse-temurin:17-jre-alpine` que contiene únicamente el binario JAR ejecutable y el JRE mínimo, reduciendo la superficie de ataque y el peso final a menos de 200 MB.

### 7.2 Orquestación con `docker-compose.yml`
Define dos servicios interconectados a través de una red privada virtual:
1. **`db`:** Contenedor oficial PostgreSQL 16 con volumen persistente `db_data` y comprobación de salud (`healthcheck`).
2. **`api`:** Contenedor de la aplicación Spring Boot configurado con el perfil `dev` y conectado a `db:3306`.

Comando de despliegue con un solo clic:
```bash
docker-compose up --build -d
```
