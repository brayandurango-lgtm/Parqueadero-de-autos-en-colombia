# Autos Colombia — Entregables visuales de Iteración 1

**Estudiante:** Brayan Alejandro Durango Urrea · **Modalidad:** individual · **Fecha:** octubre de 2026
**Fuente editable de los diagramas:** Mermaid en este documento; **mockups:** HTML/CSS servidos por Spring Boot en `src/main/resources/static/`.

> Estos diagramas están renderizados por GitHub al visualizar este Markdown. Son evidencia visual versionada, además del modelo semántico `.mdj`; no son capturas/exportaciones de StarUML ni un archivo de Figma. No se afirma que existe un documento Figma cuando no se ha creado en la cuenta del estudiante.

---

## 1. Arquitectura de la aplicación

```mermaid
flowchart LR
    O[Operador de garita]
    A[Administrador]
    subgraph WEB[Cliente web · Spring Boot static]
        UI[HTML · CSS · JavaScript<br/>Responsive UI]
    end
    subgraph API[Servidor de aplicación · Java 17 / Spring Boot 3.2.5]
        AUTH[JWT / Spring Security]
        CTRL[Controladores REST<br/>/api/v1]
        SVC[Servicios de dominio<br/>Registros · Vehículos · Celdas<br/>Novedades · Reportes · Usuarios]
        REPO[Spring Data JPA repositories]
        FLY[Flyway migrations]
    end
    subgraph DB[Persistencia]
        PG[(PostgreSQL<br/>Garajeautomovil / postgres 16)]
        VOL[(Volumen Docker persistente)]
    end
    O --> UI
    A --> UI
    UI -->|HTTP + JSON + Bearer JWT| AUTH
    AUTH --> CTRL
    CTRL --> SVC
    SVC --> REPO
    FLY --> PG
    REPO --> PG
    PG --> VOL
```

## 2. Casos de uso de la Iteración 1

```mermaid
flowchart LR
    Operador([Operador])
    Admin([Administrador])
    subgraph Sistema[Autos Colombia]
        Login((Iniciar sesión))
        Entrada((Registrar entrada))
        Salida((Registrar salida))
        Pico((Validar Pico y Placa))
        Celda((Asignar / liberar celda))
        Ticket((Consultar comprobante QR))
        Novedad((Registrar novedad))
        Consulta((Consultar vehículos y celdas))
        Dashboard((Consultar ocupación))
        CSV((Exportar CSV))
        Usuarios((Gestionar usuarios))
    end
    Operador --> Login
    Operador --> Entrada
    Operador --> Salida
    Operador --> Novedad
    Operador --> Consulta
    Admin --> Login
    Admin --> Entrada
    Admin --> Salida
    Admin --> Novedad
    Admin --> Consulta
    Admin --> Dashboard
    Admin --> CSV
    Admin --> Usuarios
    Entrada -.->|<<include>>| Pico
    Entrada -.->|<<include>>| Celda
    Entrada -.->|<<include>>| Ticket
    Salida -.->|<<include>>| Celda
    Salida -.->|<<include>>| Ticket
```

### Flujo funcional verificado

- La API bloquea una entrada cuando el cálculo de Pico y Placa devuelve restricción (no es una simple advertencia). El rango horario/festivos no está configurado en la regla actual.
- Al confirmar entrada, se genera comprobante persistido con QR. El cliente consulta `GET /api/v1/comprobantes?registroId={id}` para visualizarlo.
- El egreso cierra el registro y libera la celda; la API de egreso devuelve `RegistroDTO`, no el QR. El flujo de QR de salida requiere verificación adicional.
- El CSV existe, pero en la configuración actual su endpoint requiere autenticación general y aún no tiene `ADMINISTRADOR` aplicado de forma explícita.

## 3. Diagrama de clases del dominio

```mermaid
classDiagram
    class Usuario {
      +Long id
      +String nombre
      +String apellido
      +String documento
      +String correo
      +String claveHash
      +TipoUsuario tipo
      +Boolean activo
    }
    class Vehiculo {
      +Long id
      +String placa
      +String marca
      +String modelo
      +String color
      +TipoVehiculo tipo
      +String propietarioNombre
    }
    class Celda {
      +Long id
      +String numero
      +Integer piso
      +TipoVehiculo tipoVehiculo
      +EstadoCelda estado
    }
    class Registro {
      +Long id
      +LocalDateTime fechaHoraEntrada
      +LocalDateTime fechaHoraSalida
      +EstadoRegistro estado
      +String observaciones
    }
    class Comprobante {
      +Long id
      +String codigo
      +TipoComprobante tipo
      +String qrBase64
    }
    class Novedad {
      +Long id
      +TipoNovedad tipo
      +String descripcion
      +String evidenciaUrl
    }
    class AuditoriaLog {
      +Long id
      +String operacion
      +String entidadAfectada
      +String ipOrigen
      +String resultado
    }
    class TipoUsuario {
      <<enumeration>>
      ADMINISTRADOR
      OPERADOR
    }
    class TipoVehiculo {
      <<enumeration>>
      CARRO
      MOTO
      CAMIONETA
    }
    class EstadoCelda {
      <<enumeration>>
      DISPONIBLE
      OCUPADA
      MANTENIMIENTO
    }
    class EstadoRegistro {
      <<enumeration>>
      ACTIVO
      CERRADO
      CANCELADO
    }
    class TipoComprobante {
      <<enumeration>>
      ENTRADA
      SALIDA
    }
    class TipoNovedad {
      <<enumeration>>
      RAYON
      DANO_MECANICO
      OBJETO_CAIDO
      ROBO_PARCIAL
      OTRO
    }
    Usuario "1" --> "0..*" Registro : usuarioEntrada
    Usuario "0..1" --> "0..*" Registro : usuarioSalida
    Vehiculo "1" --> "0..*" Registro : estancias
    Celda "1" --> "0..*" Registro : asignaciones
    Registro "1" --> "1..2" Comprobante : comprobantes
    Registro "1" --> "0..*" Novedad : novedades
    Usuario "1" --> "0..*" Novedad : reporta
    Usuario "0..1" --> "0..*" AuditoriaLog : actor
    Usuario --> TipoUsuario
    Vehiculo --> TipoVehiculo
    Celda --> TipoVehiculo
    Celda --> EstadoCelda
    Registro --> EstadoRegistro
    Comprobante --> TipoComprobante
    Novedad --> TipoNovedad
```

## 4. Diagrama de paquetes

```mermaid
flowchart TB
    subgraph presentation[presentation · REST y cliente]
        UI[static · HTML/CSS/JS]
        Controllers[controllers]
        DTO[dto]
        Security[config · Spring Security/JWT]
    end
    subgraph domain[domain · reglas de negocio]
        Model[model · entidades JPA]
        Enums[enums]
        Services[service]
    end
    subgraph infrastructure[infrastructure · persistencia y soporte]
        Repositories[repository · Spring Data]
        Exceptions[exception · handler global]
    end
    Util[util · JwtUtil]
    DB[(PostgreSQL)]
    UI --> Controllers
    Controllers --> DTO
    Controllers --> Services
    Security --> Util
    Security --> Services
    Services --> Model
    Services --> Enums
    Services --> Repositories
    Services --> Exceptions
    Repositories --> DB
```

## 5. Mockups

Los mockups de fidelidad de implementación son accesibles desde la propia aplicación, en la raíz del servidor Spring Boot (`http://localhost:8080`):

1. **Acceso:** correo, contraseña, estado de error y sesión.
2. **Dashboard:** KPIs, ocupación y últimos registros.
3. **Entrada:** placa, tipo, datos opcionales del propietario, respuesta de asignación y QR cuando está disponible.
4. **Salida:** consulta por placa, resumen, observaciones, cierre de estancia.
5. **Novedades:** registro de incidencia por estancia activa.

La vista HTML incluye estilos responsive y controles conectados con la API. No debe llamarse «prototipo Figma»: la evidencia de la interfaz implementada es el frontend del repositorio. Para ver las pantallas, levanta la aplicación; para ver inmediatamente los diagramas, abre la pestaña **Preview** de este Markdown o la página del repositorio en GitHub.

**Verificación local reciente:** `mvn clean verify` · 136 pruebas Java · 0 fallos · 0 errores · 0 omitidas · JaCoCo líneas 90,2%. Pruebas de humo frontend: 4/4.
