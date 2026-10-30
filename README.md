# S20 - Evidencia de Aprendizaje 1. Diseño Sistema de Información – Iteración 1
## Sistema de Gestión de Parqueadero "Autos Colombia"

[![Java](https://img.shields.io/badge/Java-17%20LTS-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Tests](https://img.shields.io/badge/Tests-Maven%20%2F%20JUnit-success.svg)]()

---

## 📋 Información Académica del Proyecto

* **Institución:** Institución Universitaria Digital de Antioquia (IU Digital)
* **Asignatura:** Ingeniería de Software
* **Actividad:** S20 - Evidencia de Aprendizaje 1. Diseño Sistema de Información – Iteración 1
* **Grupo:** [PREICA2602B020081](https://iudigital.instructure.com/courses/28333)
* **Profesor:** Esteban Montoya Molina
* **Fecha de Entrega y Ubicación:** Octubre 2026, Colombia, Antioquia, Medellín

### 👤 Modalidad individual y roles asumidos

| Estudiante | Roles asumidos | Alcance |
|---|---|---|
| **Brayan Alejandro Durango Urrea** | **Analista de Requerimientos, Desarrollador Backend y Desarrollador Frontend / QA** | Requerimientos e historias, API y persistencia, pruebas, wireframes y documentación. |

> **Estado honesto de entregables:** el backend y sus pruebas están en el repositorio; los mockups actualmente son wireframes documentados, no un archivo o enlace de Figma; el modelo `.mdj` debe abrirse y verificarse en StarUML antes de considerarlo evidencia visual completa. El PDF se conserva localmente y no se versiona.

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

> **Alcance real de la interfaz:** entrada, búsqueda/salida, dashboard y registro de novedades están conectados con la API. No se han implementado pantallas CRUD de usuarios/celdas, liquidación/cobro de pagos ni un prototipo externo en Figma; esos ítems no se deben dar por terminados.

---

## 🏛️ Entregables de documentación y diseño

Toda la documentación técnica y de diseño elaborada para esta iteración se encuentra disponible en la carpeta [`docs/`](./docs/):

* 📐 **[01. Documento de Arquitectura de Software (SAD)](./docs/01_ARQUITECTURA_DEL_SISTEMA.md):**  
   Arquitectura en capas, Modelo C4, atributos de calidad y decisiones de arquitectura; revisar las notas de coherencia con PostgreSQL.
* 📊 **[02. Especificación de Diagramas UML](./docs/02_DIAGRAMAS_UML.md):**  
   Especificación textual y diagramas Mermaid de casos de uso, clases y paquetes; archivo nativo en `docs/staruml/`.
* 🗄️ **[03. Modelo Entidad-Relación (MER / DER) y Diccionario de Datos](./docs/03_MODELO_ENTIDAD_RELACION_MER.md):**  
  Diagrama DER en sintaxis Mermaid, cardinalidades conceptuales, diccionario exhaustivo de las 7 tablas (`usuario`, `vehiculo`, `celda`, `registro`, `novedad`, `comprobante`, `auditoria_log`) e integridad referencial.
* 🎨 **[04. Mockups y Arquitectura Visual de Interfaz](./docs/04_MOCKUPS_ARQUITECTURA_VISUAL.md):**  
   Design system y wireframes de baja fidelidad en Markdown. No reemplazan un prototipo Figma ni incluyen un enlace Figma.
* 🔍 **[05. Revisión Técnica y Auditoría de Arquitectura](./docs/05_REVISION_TECNICA_ARQUITECTURA.md):**  
   Trazabilidad y observaciones técnicas. Los resultados de pruebas deben actualizarse desde la ejecución actual, no inferirse de cifras históricas.
* 📚 **[06. Requerimientos e Historias de Usuario](./docs/06_REQUERIMIENTOS_E_HISTORIAS_DE_USUARIO.md)** y **[07. Backend y API](./docs/07_DESARROLLO_BACKEND_Y_API.md)**.
* 🗺️ **[08. Entregables visuales de Iteración 1](./docs/08_ENTREGABLES_VISUALES.md):** arquitectura, casos de uso, clases y paquetes Mermaid renderizables en GitHub. **[Abrir diagramas visuales](./docs/08_ENTREGABLES_VISUALES.md)**.
* 📄 **[Documento final editable en HTML](./docs/EVIDENCIA_DE_APRENDIZAJE_1_DOCUMENTO_FINAL.html):** fuente del informe; el PDF generado se conserva localmente, fuera del control de versiones.
* 🗃️ **Esquema PostgreSQL ejecutable:** [migración Flyway](./src/main/resources/db/migration/V1__schema_inicial.sql). El DDL MySQL histórico de algunos documentos no debe ejecutarse.

---

## 📂 Estructura del Proyecto

```
Ingenieria - Evidencia de aprendizaje 1/
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
│   ├── staruml/                            # Proyecto UML editable
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
│   │       ├── db/migration/              # Scripts Flyway (V1__schema_inicial.sql)
│   │       ├── application.yml            # Configuración general
│   │       ├── application-dev.yml        # Perfil local / Docker
│   │       └── application-test.yml       # Perfil de pruebas H2 en memoria
│   └── test/
│       ├── java/com/autoscolombia/        # Pruebas unitarias y de integración
│       │   ├── integration/                # Pruebas de integración de controladores
│       │   ├── service/                    # Pruebas de negocio
│       │   ├── presentation/               # Pruebas de controladores y filtros
│       │   ├── infrastructure/             # Pruebas del manejador global
│       │   └── util/                       # Pruebas de JWT
│       └── js/app.test.js                  # Pruebas de humo del frontend
├── src/main/resources/static/              # Interfaz web HTML, CSS y JavaScript
├── Dockerfile                             # Empaquetado de imagen de producción
├── pom.xml                                # Dependencias Maven y configuración JaCoCo
└── README.md                              # Documentación general del proyecto
```

---

## 🛠️ Stack Tecnológico

| Capa / Componente | Tecnología | Versión / Detalle |
|---|---|---|
| **Lenguaje** | Java (OpenJDK) | 17 LTS |
| **Framework Backend** | Spring Boot | 3.2.5 |
| **Seguridad** | Spring Security 6 + JWT | JJWT 0.12.6 (Stateless) |
| **Persistencia** | Spring Data JPA / Hibernate | Jakarta Persistence 3.1 |
| **Base de Datos** | PostgreSQL | Versión 16 en Docker; base local `Garajeautomovil` |
| **Migraciones BD** | Flyway | Integrado con Spring Boot |
| **Códigos QR** | Google ZXing | 3.5.2 |
| **Documentación API**| Springdoc OpenAPI (Swagger) | 2.5.0 |
| **Pruebas Automatizadas**| JUnit 5 + Mockito + JaCoCo y Node.js | 136 pruebas Java; 4 pruebas de interfaz |
| **Cobertura actual** | JaCoCo | 90,2% líneas; meta 70% superada |
| **Contenedores** | Docker & Docker Compose | Contenedores para API y PostgreSQL |

---

## 🚀 Guía de Instalación y Ejecución

### Opción 1: Ejecución con Docker Compose

1. Clonar el repositorio y acceder al directorio:
   ```bash
   git clone https://github.com/brayandurango-lgtm/Ingenieria-1-disenio-del-sistema.git
   cd "Ingenieria - Evidencia de aprendizaje 1"
   ```
2. Definir secretos de desarrollo en el entorno (no los guardes en Git):
   ```powershell
   $env:DB_PASSWORD = "una-clave-local"
   $env:JWT_SECRET = "una-clave-de-firma-larga-para-desarrollo"
   ```
3. Levantar los servicios de base de datos y backend:
   ```bash
   docker-compose up --build
   ```
4. El sistema estará disponible en:
   * **API REST:** `http://localhost:8080`
   * **Documentación Swagger UI:** `http://localhost:8080/swagger-ui.html`
   * **OpenAPI Docs (JSON):** `http://localhost:8080/v3/api-docs`

---

### Opción 2: Ejecución local con Maven y PostgreSQL

1. Crear la base de datos PostgreSQL si aún no existe (usa el nombre configurado, por defecto `Garajeautomovil`):
   ```sql
   CREATE DATABASE "Garajeautomovil";
   ```
2. Configurar las variables de entorno en PowerShell:
   ```bash
   $env:DB_HOST="localhost"
   $env:DB_PORT="5432"
   $env:DB_NAME="Garajeautomovil"
   $env:DB_USER="postgres"
   $env:DB_PASSWORD="la-clave-local"
   $env:JWT_SECRET="una-clave-de-firma-larga-para-desarrollo"
   ```
3. Compilar el proyecto y ejecutar la aplicación:
   ```bash
   mvn clean spring-boot:run
   ```

---

## 🔐 Credenciales de Prueba (Semilla)

El script inicial de Flyway pre-carga dos usuarios con contraseñas encriptadas mediante BCrypt:

| Rol | Correo Electrónico | Contraseña | Permisos |
|---|---|---|---|
| **Administrador** | `admin@autoscolombia.com` | `clave123` | Acceso total, dashboard, reportes CSV y auditoría |
| **Operador** | `operador@autoscolombia.com` | `clave123` | Registro de entrada, salida, novedades y celdas |

---

## 📡 Endpoints Principales de la API REST

### 🔑 Autenticación
* `POST /api/v1/auth/login` - Inicio de sesión y expedición de Token JWT.
* `GET  /api/v1/auth/me` - Consulta de datos del usuario autenticado actual.

### 🚗 Registros de Entrada y Salida
* `POST /api/v1/registros/entrada` - Admisión de vehículo, validación de Pico y Placa, asignación de celda y ticket QR.
* `PUT  /api/v1/registros/salida/{placa}` - Cierre de estancia, cálculo de permanencia, liberación de celda y ticket salida.
* `GET  /api/v1/registros/activos` - Listado de vehículos actualmente dentro del parqueadero.
* `GET  /api/v1/registros/ultimos` - Consulta de los últimos ingresos registrados.
* `DELETE /api/v1/registros/{id}` - Cancelación de un registro activo erróneo.

### 🅿️ Celdas y Vehículos
* `GET  /api/v1/celdas` - Consulta del estado de todas las bahías de parqueo.
* `GET  /api/v1/celdas/disponibles` - Filtrado de celdas libres por tipo de vehículo.
* `GET  /api/v1/vehiculos` - Búsqueda de vehículos por placa, propietario o tipo.

### ⚠️ Novedades e Incidentes
* `POST /api/v1/novedades` - Reporte de novedad o daño en vehículo con soporte de evidencia.
* `GET  /api/v1/novedades/pendientes` - Listado de novedades abiertas para seguimiento.

### 📊 Reportes y Dashboard
* `GET  /api/v1/reportes/dashboard` - Métricas consolidadas de ocupación y porcentaje de celdas.
* `GET  /api/v1/reportes/ocupacion` - Descarga de reporte operacional consolidado en formato CSV.

---

## 🧪 Ejecución de Pruebas Automatizadas

Ejecuta las pruebas unitarias y de integración desde Maven:

```bash
# Ejecutar todas las pruebas unitarias y de integración
mvn clean test

# Generar reporte de cobertura de código con JaCoCo
mvn jacoco:report
```

> El número de pruebas y la cobertura cambian con el código. Verifica el resultado de `mvn clean verify`; el informe JaCoCo se genera en `target/site/jacoco/index.html` y `target/` no se versiona.

### Pruebas del frontend estático

El frontend no requiere empaquetador ni dependencias npm. Ejecuta las pruebas con Node.js:

```powershell
node --test src/test/js/app.test.js
```

Para probar la experiencia completa con la API, inicia Spring Boot, abre `http://localhost:8080` y autentícate con un usuario sembrado por Flyway. Comprueba inicio de sesión válido e inválido, registro de entrada, placa duplicada, búsqueda y salida, dashboard, novedad y cierre de sesión.

### Diagramas visuales

[Entregables visuales de Iteración 1](https://github.com/brayandurango-lgtm/Ingenieria-1-disenio-del-sistema/blob/main/docs/08_ENTREGABLES_VISUALES.md)

## 📄 Licencia y Derechos

Proyecto desarrollado con fines académicos en la **Institución Universitaria Digital de Antioquia (IU Digital)** como parte del programa de Ingeniería de Software (Semestre 6).  
**Medellín, Antioquia, Colombia - Octubre 2026.**

<!-- Fin del README -->

