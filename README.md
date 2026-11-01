# S25 - Evidencia de Aprendizaje 2. Diseño Sistema de Información – Iteración 2
## Sistema de Gestión de Parqueadero "Autos Colombia"

[![Java](https://img.shields.io/badge/Java-17%20LTS-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-blue.svg)](https://www.postgresql.org/)
[![Tests](https://img.shields.io/badge/Tests-Maven%20%2F%20JUnit-success.svg)]()

---

## 📋 Información Académica del Proyecto

* **Institución:** Institución Universitaria Digital de Antioquia (I.U.D.A.)
* **Asignatura:** Ingeniería de Software
* **Actividad:** S25 - Evidencia de Aprendizaje 2. Diseño Sistema de Información – Iteración 2
* **Grupo:** [PREICA2602B020081](https://iudigital.instructure.com/courses/28333)
* **Profesor:** Esteban Montoya Molina
* **Fecha de Entrega y Ubicación:** Noviembre 2026, Colombia, Antioquia, Medellín

### 👤 Modalidad de trabajo en pareja (Pair Programming)

| Estudiante | Roles asumidos | Alcance |
|---|---|---|
| **Brayan Alejandro Durango Urrea** | **Desarrollo e integración técnica** | Backend, API, persistencia, frontend, pruebas técnicas y diagramas de secuencia/componentes |
| **Isabella** | **Análisis funcional, diseño de interfaces y documentación** | Requerimientos, historias de usuario, casos de uso, mockups y plan de pruebas |

> **Estado honesto de entregables:** el backend (Iteraciones 1 y 2) y sus pruebas están en el repositorio; los mockups son wireframes documentados en Markdown; los diagramas StarUML (`.mdj`) deben abrirse y verificarse en StarUML antes de considerarse evidencia visual completa. El PDF se conserva localmente y no se versiona.

---

## 📖 Descripción del Sistema

**Autos Colombia** es una plataforma de software orientada a la gestión y control operativo de parqueaderos comerciales bajo modalidad de mensualidad y rotación.

La **Iteración 1** se enfoca exclusivamente en el núcleo transaccional de **ENTRADA y SALIDA de vehículos**:
1. **Control de Acceso Seguro:** Autenticación de operadores y administradores mediante JSON Web Tokens (JWT) sin estado.
2. **Registro Ágil de Entrada:** Captura de placas, marcas y colores; validación automática de la normativa distrital de **Pico y Placa** (Bogotá D.C.); asignación automática de celda física por tipo de vehículo (`CARRO`, `MOTO`, `CAMIONETA`); y emisión de comprobante de ingreso con código QR en Base64.
3. **Registro de Salida y Liquidación:** Búsqueda rápida por placa, cálculo automático del tiempo de permanencia, liberación transaccional de la celda de parqueo y emisión de comprobante de salida.
4. **Gestión de Novedades e Incidencias:** Registro de daños previos, rayones o novedades en vehículos durante su estancia.
5. **Dashboard Operativo y Reportes:** Indicadores clave de ocupación en tiempo real y exportación de reportes en formato CSV.
6. **Auditoría Transversal:** Pista de auditoría para operaciones sensibles registradas por la aplicación.

La **Iteración 2** extiende el sistema con **GESTIÓN DE USUARIOS Y CELDAS**:
1. **Gestión de Clientes:** CRUD completo, búsqueda por documento/nombre, baja lógica (activo/inactivo), vinculación de vehículos.
2. **Gestión de Cuentas del Personal:** CRUD de administradores y operadores, protección del último administrador, auditoría de cambios de estado.
3. **Gestión de Celdas:** CRUD completo, filtros por tipo/estado/piso, transiciones de estado operativo (DISPONIBLE ↔ MANTENIMIENTO), baja lógica, integridad de ocupación con índices únicos parciales.
4. **Vinculación Vehículo ↔ Cliente:** Asignación de titularidad, reglas de conflicto, validación de cliente activo.
5. **Entrada con Cliente:** Opción para vincular cliente durante el registro de entrada, validación de cliente activo.
6. **Integridad de Ocupación:** Garantía de una sola estancia activa por celda y por vehículo mediante restricciones de base de datos.

> **Alcance real de la interfaz:** entrada, búsqueda/salida, dashboard, clientes, celdas, usuarios y registro de novedades están conectados con la API. No se han implementado pagos, reservas, tarifas ni cambios de contraseña por el usuario; esos ítems no se deben dar por terminados.

---

## 🏛️ Entregables de documentación y diseño

Toda la documentación técnica y de diseño elaborada para estas iteraciones se encuentra disponible en la carpeta [`docs/`](./docs/):

* 📐 **[01. Documento de Arquitectura de Software (SAD)](./docs/01_ARQUITECTURA_DEL_SISTEMA.md):**
   Arquitectura en capas, Modelo C4, atributos de calidad y decisiones de arquitectura; revisar las notas de coherencia con PostgreSQL.
* 📊 **[02. Especificación de Diagramas UML](./docs/02_DIAGRAMAS_UML.md):**
   Especificación textual y diagramas Mermaid de casos de uso, clases y paquetes; archivo nativo en `docs/staruml/`.
* 🗄️ **[03. Modelo Entidad-Relación (MER / DER) y Diccionario de Datos](./docs/03_MODELO_ENTIDAD_RELACION_MER.md):**
  Diagrama DER en sintaxis Mermaid, cardinalidades conceptuales, diccionario exhaustivo de las tablas e integridad referencial.
* 🎨 **[04. Mockups y Arquitectura Visual de Interfaz](./docs/04_MOCKUPS_ARQUITECTURA_VISUAL.md):**
   Design system y wireframes de baja fidelidad en Markdown. No reemplazan un prototipo Figma ni incluyen un enlace Figma.
* 🔍 **[05. Revisión Técnica y Auditoría de Arquitectura](./docs/05_REVISION_TECNICA_ARQUITECTURA.md):**
   Trazabilidad y observaciones técnicas. Los resultados de pruebas deben actualizarse desde la ejecución actual.
* 📚 **[06. Requerimientos e Historias de Usuario](./docs/06_REQUERIMIENTOS_E_HISTORIAS_DE_USUARIO.md)** y **[07. Backend y API](./docs/07_DESARROLLO_BACKEND_Y_API.md)**.
* 🗺️ **[08. Entregables visuales de Iteración 1](./docs/08_ENTREGABLES_VISUALES.md):** arquitectura, casos de uso, clases y paquetes Mermaid renderizables en GitHub.
* 📄 **[Documento final editable en HTML](./docs/EVIDENCIA_DE_APRENDIZAJE_1_DOCUMENTO_FINAL.html):** fuente del informe; el PDF generado se conserva localmente, fuera del control de versiones.
* 🗃️ **Esquema PostgreSQL ejecutable:** migraciones Flyway en `src/main/resources/db/migration/`.
* 📋 **[09. Cierre Técnico - Rol de Brayan (Iteración 2)](./docs/iteracion2/09_CIERRE_TECNICO_BRAYAN.md):** documento técnico con estado de implementación, migraciones, pruebas y evidencias pendientes.

---

## 📂 Estructura del Proyecto

```
Parqueadero de autos en colombia/
├── docs/                                  # Entregables de Arquitectura y Diseño
│   ├── 01_ARQUITECTURA_DEL_SISTEMA.md     # Documento de Arquitectura (C4, ADRs)
│   ├── 02_DIAGRAMAS_UML.md                # Diagramas UML (Clases, Secuencia, Casos de Uso)
│   ├── 03_MODELO_ENTIDAD_RELACION_MER.md  # DER, MER y Diccionario de Datos
│   ├── 04_MOCKUPS_ARQUITECTURA_VISUAL.md  # Wireframes y Mockups de Interfaz
│   ├── 05_REVISION_TECNICA_ARQUITECTURA.md # Revisión y trazabilidad
│   ├── 06_REQUERIMIENTOS_E_HISTORIAS_DE_USUARIO.md
│   ├── 07_DESARROLLO_BACKEND_Y_API.md
│   ├── 08_ENTREGABLES_VISUALES.md          # Mermaid de arquitectura y UML
│   ├── EVIDENCIA_DE_APRENDIZAJE_1_DOCUMENTO_FINAL.html # Fuente editable del informe
│   ├── iteracion2/                         # Documentación Iteración 2
│   │   └── 09_CIERRE_TECNICO_BRAYAN.md    # Cierre técnico del rol de Brayan
│   ├── staruml/                            # Proyectos UML editables
│   │   ├── AutosColombia_Iteracion1.mdj    # Diagramas Iteración 1
│   │   └── AutosColombia_Iteraciones1y2.mdj # Diagramas secuencia y componentes
│   └── postman/                           # Colección de pruebas de API REST
│       └── autos-colombia.postman_collection.json
├── src/
│   ├── main/
│   │   ├── java/com/autoscolombia/
│   │   │   ├── domain/                    # Capa de Dominio y Negocio
│   │   │   │   ├── enums/                 # Enumeraciones (Estados, Tipos)
│   │   │   │   ├── model/                 # Entidades JPA (Usuario, Vehiculo, etc.)
│   │   │   │   └── service/               # Servicios de Lógica de Negocio
│   │   │   ├── infrastructure/            # Capa de Infraestructura
│   │   │   │   ├── repository/            # Repositorios Spring Data JPA
│   │   │   │   └── exception/             # Manejadores y Excepciones Globales
│   │   │   ├── presentation/              # Capa de Presentación
│   │   │   │   ├── controllers/           # Controladores REST API
│   │   │   │   ├── dto/                   # Data Transfer Objects con validación
│   │   │   │   └── config/                # Seguridad, CORS y PasswordEncoder
│   │   │   ├── util/                      # Utilidades de Tokens JWT (JwtUtil)
│   │   │   └── AutosColombiaApplication.java
│   │   └── resources/
│   │       ├── db/migration/              # Scripts Flyway (V1, V2, V3)
│   │       ├── application.yml            # Configuración general
│   │       ├── application-dev.yml        # Perfil local / Docker
│   │       └── application-test.yml       # Perfil de pruebas H2 en memoria
│   ├── test/
│   │   ├── java/com/autoscolombia/        # Pruebas unitarias y de integración
│   │   │   ├── integration/                # Pruebas de integración de controladores
│   │   │   ├── service/                    # Pruebas de negocio
│   │   │   ├── presentation/               # Pruebas de controladores y filtros
│   │   │   ├── infrastructure/             # Pruebas del manejador global
│   │   │   └── util/                       # Pruebas de JWT
│   │   └── js/app.test.js                  # Pruebas de humo del frontend
├── src/main/resources/static/              # Interfaz web HTML, CSS y JavaScript
├── Dockerfile                             # Empaquetado de imagen de producción
├── pom.xml                                # Dependencias Maven y configuración JaCoCo
└── README.md                              # Documentación general del proyecto
```

---

## 🛠️ Stack Tecnológico

|| Capa / Componente | Tecnología | Versión / Detalle |
||---|---|---|
|| **Lenguaje** | Java (OpenJDK) | 17 LTS |
|| **Framework Backend** | Spring Boot | 3.2.5 |
|| **Seguridad** | Spring Security 6 + JWT | JJWT 0.12.6 (Stateless) |
|| **Persistencia** | Spring Data JPA / Hibernate | Jakarta Persistence 3.1 |
|| **Base de Datos** | PostgreSQL | Versión 18 en Windows; base `Parqueadero de autos en colombia` |
|| **Migraciones BD** | Flyway | Integrado con Spring Boot (V1, V2, V3) |
|| **Códigos QR** | Google ZXing | 3.5.2 |
|| **Documentación API**| Springdoc OpenAPI (Swagger) | 2.5.0 |
|| **Pruebas Automatizadas**| JUnit 5 + Mockito + JaCoCo y Node.js | Pruebas Java y frontend |
|| **Cobertura objetivo** | JaCoCo | Meta 70% líneas |
|| **Contenedores** | Docker & Docker Compose | Contenedores para API y PostgreSQL (opcional) |

---

## 🚀 Guía de Instalación y Ejecución

### Requisitos Previos

- Java 17 LTS o superior
- Maven 3.6+
- PostgreSQL 18 (o compatible)
- Node.js (para pruebas de frontend)

### Configuración de Variables de Entorno

**IMPORTANTE:** Configure las siguientes variables de entorno localmente. No las pegue en el chat ni las sube al repositorio.

```powershell
# Configuración de base de datos
$env:DB_HOST    = "localhost"
$env:DB_PORT    = "5432"
$env:DB_NAME    = "Parqueadero de autos en colombia"
$env:DB_USER    = "postgres"
$env:DB_PASSWORD = "<CONFIGURAR_LOCALMENTE>"  # Contraseña de PostgreSQL

# Configuración de seguridad
$env:JWT_SECRET  = "<GENERAR_Y_CONFIGURAR_LOCALMENTE>"  # Clave secreta para JWT (mínimo 256 bits)

# Perfil de Spring (opcional)
$env:SPRING_PROFILES_ACTIVE = "dev"
```

> **Nota:** `DB_NAME` indica el nombre exacto de la base de datos en PostgreSQL. Verifique que exista antes de iniciar la aplicación.

### Creación de la Base de Datos

Si la base de datos no existe, créela en PostgreSQL:

```sql
CREATE DATABASE "Parqueadero de autos en colombia";
```

Las migraciones Flyway (V1, V2, V3) crearán automáticamente las tablas al iniciar la aplicación.

### Ejecución con Maven

1. **Compilar y ejecutar:**
   ```bash
   mvn clean spring-boot:run
   ```

2. **El sistema estará disponible en:**
   * **API REST:** `http://localhost:8080`
   * **Documentación Swagger UI:** `http://localhost:8080/swagger-ui.html`
   * **OpenAPI Docs (JSON):** `http://localhost:8080/v3/api-docs`
   * **Interfaz web:** `http://localhost:8080`

### Ejecución con Docker Compose (Opcional)

1. **Levantar servicios:**
   ```bash
   docker-compose up --build
   ```

2. **Acceder:** mismo que ejecución local.

---

## 🔐 Credenciales de demostración

La migración inicial puede crear cuentas semilla para desarrollo local. Este README no publica sus correos ni contraseñas. Antes de exponer una instalación fuera del equipo local, cambie o desactive esas cuentas mediante un procedimiento privado y verifique las credenciales de forma segura.

Las cuentas creadas desde la pantalla de personal requieren una contraseña inicial propia; no se usa una contraseña compartida para las cuentas nuevas.

---

## 📡 Endpoints Principales de la API REST

### 🔑 Autenticación
* `POST /api/v1/auth/login` - Inicio de sesión y expedición de Token JWT.
* `GET  /api/v1/auth/me` - Consulta de datos del usuario autenticado actual.

### 👥 Gestión de Usuarios (Solo Administradores)
* `GET    /api/v1/usuarios` - Listar todas las cuentas del personal.
* `POST   /api/v1/usuarios` - Crear cuenta con contraseña inicial.
* `PUT    /api/v1/usuarios/{id}` - Actualizar cuenta sin modificar contraseña.
* `PATCH  /api/v1/usuarios/{id}/activo` - Activar o desactivar cuenta (protege último administrador).
* `DELETE /api/v1/usuarios/{id}` - Eliminar lógicamente (desactiva).

### 👤 Gestión de Clientes
* `GET    /api/v1/clientes` - Listar clientes con paginación y filtros.
* `POST   /api/v1/clientes` - Crear nuevo cliente.
* `PUT    /api/v1/clientes/{id}` - Actualizar cliente.
* `PATCH  /api/v1/clientes/{id}/activo` - Activar o desactivar cliente.
* `GET    /api/v1/clientes/{id}/vehiculos` - Consultar vehículos de un cliente.

### 🚗 Gestión de Vehículos
* `GET    /api/v1/vehiculos/{placa}` - Consultar vehículo por placa.
* `PUT    /api/v1/vehiculos/{placa}/cliente` - Vincular vehículo a cliente (cambio de titularidad).

### 🅿️ Gestión de Celdas
* `GET    /api/v1/celdas/pagina` - Listar celdas con paginación y filtros.
* `POST   /api/v1/celdas` - Crear nueva celda.
* `PUT    /api/v1/celdas/{id}` - Actualizar celda.
* `PUT    /api/v1/celdas/{id}/estado` - Cambiar estado operativo (DISPONIBLE ↔ MANTENIMIENTO).
* `PATCH  /api/v1/celdas/{id}/activo` - Activar o desactivar celda.

### 🚗 Registros de Entrada y Salida
* `POST /api/v1/registros/entrada` - Admisión de vehículo, validación de Pico y Placa, asignación de celda y ticket QR. Opcionalmente incluye `clienteId` para vincular.
* `PUT  /api/v1/registros/salida/{placa}` - Cierre de estancia, cálculo de permanencia, liberación de celda y ticket salida.
* `GET  /api/v1/registros/activos` - Listado de vehículos actualmente dentro del parqueadero.
* `GET  /api/v1/registros/{placa}` - Consultar registro activo por placa.
* `GET  /api/v1/registros/ultimos` - Consulta de los últimos ingresos registrados.
* `DELETE /api/v1/registros/{id}` - Cancelación de un registro activo erróneo.

### ⚠️ Novedades e Incidentes
* `POST /api/v1/novedades` - Reporte de novedad o daño en vehículo con soporte de evidencia.
* `GET  /api/v1/novedades/pendientes` - Listado de novedades abiertas para seguimiento.

### 📊 Reportes y Dashboard
* `GET /api/v1/reportes/dashboard` - Métricas consolidadas de ocupación y porcentaje de celdas.
* `GET /api/v1/reportes/ocupacion` - Descarga de reporte operacional consolidado en formato CSV.

---

## 🧪 Ejecución de Pruebas Automatizadas

### Pruebas Unitarias y de Integración (Java)

Ejecuta todas las pruebas con Maven:

```bash
# Ejecutar todas las pruebas unitarias y de integración
mvn clean test

# Ejecutar pruebas y generar reporte de cobertura
mvn clean verify

# Generar reporte de cobertura de código con JaCoCo
mvn jacoco:report
```

> El informe JaCoCo se genera en `target/site/jacoco/index.html`. `target/` no se versiona en Git.

### Pruebas del Frontend (Node.js)

El frontend no requiere empaquetador ni dependencias npm. Ejecuta las pruebas con Node.js:

```powershell
node --test src/test/js/app.test.js
```

### Pruebas de Integración Manual

Para probar la experiencia completa con la API:

1. Inicia Spring Boot: `mvn spring-boot:run`
2. Abre `http://localhost:8080` en el navegador
3. Autentícate con un usuario sembrado por Flyway
4. Verifica:
   - Login válido e inválido
   - Registro de entrada con y sin cliente
   - Búsqueda y salida
   - Gestión de clientes
   - Gestión de celdas
   - Gestión de usuarios (solo administrador)
   - Dashboard
   - Novedad
   - Cierre de sesión

---

## 📦 Migraciones de Base de Datos (Flyway)

El proyecto usa Flyway para versionar el esquema de base de datos. Las migraciones están en `src/main/resources/db/migration/`:

| Versión | Archivo | Descripción |
|---------|---------|-------------|
| V1 | `V1__schema_inicial.sql` | Esquema base: usuario, vehiculo, celda, registro, novedad, comprobante, auditoria_log |
| V2 | `V2__clientes_y_vinculo_vehiculos.sql` | Tabla cliente y FK vehiculo.cliente_id (nullable, compatible con Iteración 1) |
| V3 | `V3__gestion_celdas_e_integridad_ocupacion.sql` | Baja lógica de celda, índices únicos parciales para ocupación activa |

**Características de V3:**
- Comprueba anomalías antes de crear índices (aborta si hay inconsistencias)
- No repara automáticamente: deja que un operador decida qué registros conservar
- Estrictamente aditiva: no reescribe V1 ni V2

Flyway se ejecuta automáticamente al iniciar la aplicación si `flyway.enabled=true` (configuración por defecto).

---

## 🗺️ Diagramas StarUML

Los diagramas de secuencia y componentes de las Iteraciones 1 y 2 están disponibles en formato nativo de StarUML:

- **Iteración 1:** `docs/staruml/AutosColombia_Iteracion1.mdj` (casos de uso, clases, paquetes, despliegue)
- **Iteraciones 1 y 2:** `docs/staruml/AutosColombia_Iteraciones1y2.mdj` (secuencia y componentes)

**Para abrir estos archivos:**
1. Descarga e instala [StarUML](https://staruml.io/)
2. Abre el archivo `.mdj` desde StarUML
3. Exporta a PNG/PDF según necesites para el PDF de la actividad

> **Estado de validación:** Los archivos `.mdj` han sido creados con la estructura JSON correcta de StarUML. Deben abrirse y verificarse visualmente en StarUML antes de considerarse evidencia completa.

---

## 📄 Limitaciones del Proyecto

### Funcionales (Fuera del Alcance)
- **Pagos:** No implementados (tarifas, liquidación, cobro)
- **Reservas:** No implementadas
- **Cambios de contraseña por el usuario:** No implementados (solo contraseña inicial en creación por administrador)
- **Recuperación de contraseña:** No implementada
- **Notificaciones:** No implementadas

### Técnicas
- **Autenticación:** Solo JWT sin estado. No hay soporte para OAuth2, SAML ni otros proveedores.
- **Base de datos:** Solo PostgreSQL. No hay soporte para MySQL ni otros motores.
- **Frontend:** Estático (HTML/CSS/JS vanilla). No usa React, Vue ni Angular.
- **Contenedores:** Docker Compose es opcional. La aplicación puede ejecutarse directamente con Maven.

---

## 📄 Licencia y Derechos

Proyecto desarrollado con fines académicos en la **Institución Universitaria Digital de Antioquia (IU Digital)** como parte del programa de Ingeniería de Software (Semestre 6).
**Medellín, Antioquia, Colombia - Octubre 2026.**

---

## 🔗 Enlaces Útiles

- **Repositorio GitHub:** https://github.com/brayandurango-lgtm/Parqueadero-de-autos-en-colombia.git
- **Documentación técnica Iteración 2:** [docs/iteracion2/09_CIERRE_TECNICO_BRAYAN.md](./docs/iteracion2/09_CIERRE_TECNICO_BRAYAN.md)
- **StarUML:** https://staruml.io/

<!-- Fin del README -->
