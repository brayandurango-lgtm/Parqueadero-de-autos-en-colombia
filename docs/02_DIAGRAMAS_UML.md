# Especificación de Diagramas UML
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

## 1. Diagrama de Casos de Uso del Sistema

El diagrama de casos de uso modela los requisitos funcionales de la Iteración 1 desde la perspectiva de los actores que interactúan con el sistema:
* **Operador de Parqueadero:** Encargado de la operación en garita (ingreso, salida, novedades y consulta de celdas).
* **Administrador del Sistema:** Supervisión estratégica, gestión de usuarios, auditoría y métricas.
* **Sistema / Reloj del Sistema:** Actor secundario temporal para la validación automática de normativas distritales.

```mermaid
flowchart LR
    Operador((Operador))
    Admin((Administrador))
    Clock((Reloj Sistema))

    subgraph SistemaAutos ["Sistema de Parqueadero Autos Colombia (Iteración 1)"]
        CU01["CU-01: Iniciar Sesión (JWT)"]
        CU02["CU-02: Registrar Entrada de Vehículo"]
        CU03["CU-03: Validar Pico y Placa"]
        CU04["CU-04: Asignar Celda Automática"]
        CU05["CU-05: Emitir Comprobante con QR"]
        CU06["CU-06: Registrar Salida de Vehículo"]
        CU07["CU-07: Liberar Celda de Parqueo"]
        CU08["CU-08: Registrar Novedad / Daño"]
        CU09["CU-09: Cancelar Registro Activo"]
        CU10["CU-10: Consultar Celdas y Vehículos"]
        CU11["CU-11: Visualizar Dashboard de Ocupación"]
        CU12["CU-12: Exportar Reporte de Ocupación (CSV)"]
        CU13["CU-13: Auditar Operaciones Críticas"]
    end

    Operador --> CU01
    Operador --> CU02
    Operador --> CU06
    Operador --> CU08
    Operador --> CU09
    Operador --> CU10

    Admin --> CU01
    Admin --> CU11
    Admin --> CU12
    Admin --> CU13
    Admin --> CU10

    CU02 -.->|<<include>>| CU03
    CU02 -.->|<<include>>| CU04
    CU02 -.->|<<include>>| CU05
    CU06 -.->|<<include>>| CU07
    CU06 -.->|<<include>>| CU05

    CU02 -.->|<<include>>| CU13
    CU06 -.->|<<include>>| CU13
    CU08 -.->|<<include>>| CU13

    CU03 --- Clock
```

### Tabla de Especificación Resumida de Casos de Uso

| Código | Nombre del Caso de Uso | Actor Principal | Precondición | Postcondición |
|---|---|---|---|---|
| **CU-01** | Iniciar Sesión | Operador / Admin | Usuario registrado y activo | Token JWT retornado con rol asignado |
| **CU-02** | Registrar Entrada | Operador | Sesión activa, celda disponible | Registro ACTIVO creado, celda OCUPADA, comprobante QR generado |
| **CU-03** | Validar Pico y Placa | Sistema | Placa del vehículo enviada | Autorización de ingreso o advertencia si aplica restricción |
| **CU-04** | Asignar Celda Automática | Sistema | Tipo de vehículo identificado | Celda física asignada por tipo (CARRO, MOTO, CAMIONETA) |
| **CU-05** | Emitir Comprobante con QR | Sistema | Registro de entrada o salida completado | Código alfanumérico único e imagen QR en Base64 generados |
| **CU-06** | Registrar Salida | Operador | Vehículo con registro ACTIVO | Registro pasa a CERRADO, se registra fecha/hora salida |
| **CU-07** | Liberar Celda | Sistema | Salida confirmada | Celda pasa a estado DISPONIBLE |
| **CU-08** | Registrar Novedad | Operador | Registro activo o vehículo identificado | Novedad tipificada almacenada con descripción y evidencia |
| **CU-09** | Cancelar Registro | Operador / Admin | Registro erróneo activo | Registro pasa a CANCELADO y la celda se libera |
| **CU-11** | Visualizar Dashboard | Administrador | Rol ADMINISTRADOR autenticado | Métricas de ocupación, total celdas y vehículos activos |
| **CU-12** | Exportar Reporte CSV | Administrador | Rol ADMINISTRADOR autenticado | Archivo CSV estructurado con registros y tiempos de estancia |
| **CU-13** | Auditar Operación | Sistema | Transacción ejecutada | Registro inmutable en tabla `auditoria_log` |

---

## 2. Diagrama de Clases UML (Modelo de Dominio y Capas)

Muestra las entidades del negocio, sus atributos, tipos de datos, cardinalidades y la estructura de servicios y controladores.

```mermaid
classDiagram
    %% ENUMS
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

    %% ENTIDADES JPA
    class Usuario {
        -Long id
        -String nombre
        -String apellido
        -String documento
        -String correo
        -String telefono
        -String claveHash
        -TipoUsuario tipo
        -Boolean activo
        -LocalDateTime fechaRegistro
        +prePersist() void
    }

    class Vehiculo {
        -Long id
        -String placa
        -String marca
        -String modelo
        -String color
        -TipoVehiculo tipo
        -String propietarioDocumento
        -String propietarioNombre
        -String propietarioTelefono
        -LocalDateTime fechaRegistro
        +prePersist() void
    }

    class Celda {
        -Long id
        -String numero
        -Integer piso
        -TipoVehiculo tipoVehiculo
        -EstadoCelda estado
        -String observaciones
        +prePersist() void
    }

    class Registro {
        -Long id
        -Vehiculo vehiculo
        -Celda celda
        -Usuario usuarioEntrada
        -Usuario usuarioSalida
        -LocalDateTime fechaHoraEntrada
        -LocalDateTime fechaHoraSalida
        -EstadoRegistro estado
        -String observaciones
        +prePersist() void
    }

    class Comprobante {
        -Long id
        -Registro registro
        -String codigo
        -TipoComprobante tipo
        -LocalDateTime fechaGeneracion
        -String qrBase64
        +prePersist() void
    }

    class Novedad {
        -Long id
        -Registro registro
        -TipoNovedad tipo
        -String descripcion
        -LocalDateTime fechaHora
        -Usuario reportadoPor
        -String evidenciaUrl
        +prePersist() void
    }

    class AuditoriaLog {
        -Long id
        -Usuario usuario
        -String operacion
        -String entidadAfectada
        -Long registroId
        -String ipOrigen
        -String resultado
        -LocalDateTime fechaHora
        -String detalle
        +prePersist() void
    }

    %% RELACIONES ENTRE ENTIDADES
    Usuario "1" <-- "0..*" Registro : usuarioEntrada
    Usuario "1" <-- "0..*" Registro : usuarioSalida
    Vehiculo "1" <-- "0..*" Registro : pertenece
    Celda "1" <-- "0..*" Registro : asignada_a
    Registro "1" <-- "1..2" Comprobante : genera
    Registro "1" <-- "0..*" Novedad : contiene
    Usuario "1" <-- "0..*" Novedad : reportadoPor
    Usuario "0..1" <-- "0..*" AuditoriaLog : ejecuta

    Usuario --> TipoUsuario
    Vehiculo --> TipoVehiculo
    Celda --> TipoVehiculo
    Celda --> EstadoCelda
    Registro --> EstadoRegistro
    Comprobante --> TipoComprobante
    Novedad --> TipoNovedad
```

---

## 3. Diagramas de Secuencia UML

### 3.1 Flujo 1: Autenticación y Generación de Token JWT
Representa la verificación de credenciales, control de fuerza bruta y expedición del token criptográfico.

```mermaid
sequenceDiagram
    autonumber
    actor Operador as Operador / Frontend
    participant AuthCtrl as AuthController
    participant AuthServ as AuthService
    participant UserRepo as UsuarioRepository
    participant PwdEnc as PasswordEncoder
    participant JwtUtl as JwtUtil
    participant AuditServ as AuditoriaService

    Operador->>AuthCtrl: POST /api/v1/auth/login {correo, clave}
    AuthCtrl->>AuthServ: login(loginRequest)
    
    AuthServ->>AuthServ: verificarBloqueo(correo)
    AuthServ->>UserRepo: findByCorreo(correo)
    UserRepo-->>AuthServ: Optional<Usuario>
    
    alt Usuario no encontrado o inactivo
        AuthServ->>AuthServ: incrementarIntentos(correo)
        AuthServ-->>AuthCtrl: throw AuthenticationException
        AuthCtrl-->>Operador: 401 Unauthorized / Credenciales inválidas
    else Usuario existe
        AuthServ->>PwdEnc: matches(clave, usuario.claveHash)
        alt Clave incorrecta
            AuthServ->>AuthServ: incrementarIntentos(correo)
            AuthServ-->>AuthCtrl: throw AuthenticationException
            AuthCtrl-->>Operador: 401 Unauthorized
        else Clave correcta
            AuthServ->>AuthServ: reiniciarIntentos(correo)
            AuthServ->>JwtUtl: generateToken(userDetails, usuario.tipo)
            JwtUtl-->>AuthServ: tokenJWT (Bearer)
            AuthServ->>AuditServ: registrarAuditoria(usuario, "LOGIN", "SUCCESS")
            AuthServ-->>AuthCtrl: AuthResponse {token, usuario, rol}
            AuthCtrl-->>Operador: 200 OK {token, tipoToken: "Bearer", ...}
        end
    end
```

### 3.2 Flujo 2: Registro de Entrada Vehicular
Representa el proceso completo de validación de Pico y Placa, alta/actualización del vehículo, asignación atómica de celda disponible y creación del comprobante con código QR.

```mermaid
sequenceDiagram
    autonumber
    actor Operador as Operador
    participant RegCtrl as RegistroController
    participant RegServ as RegistroService
    participant PyPServ as PicoYPlacaService
    participant VehServ as VehiculoService
    participant CeldaServ as CeldaService
    participant CompServ as ComprobanteService
    participant RegRepo as RegistroRepository
    participant AuditServ as AuditoriaService

    Operador->>RegCtrl: POST /api/v1/registros/entrada (RegistroEntradaDTO, Bearer JWT)
    RegCtrl->>RegServ: registrarEntrada(entradaDTO, usuarioCorreo)
    
    %% Validar si ya está adentro
    RegServ->>RegRepo: findByVehiculoPlacaAndEstado(placa, ACTIVO)
    alt Vehículo ya cuenta con registro activo
        RegRepo-->>RegServ: Optional<Registro> existente
        RegServ-->>RegCtrl: throw BusinessRuleException("Vehículo ya tiene registro activo")
        RegCtrl-->>Operador: 409 Conflict
    end

    %% Validar Pico y Placa
    RegServ->>PyPServ: validarPicoYPlaca(placa, tipoVehiculo)
    PyPServ-->>RegServ: boolean aplicaRestriccion
    
    %% Crear o actualizar vehículo
    RegServ->>VehServ: crearOActualizar(vehiculoDTO)
    VehServ-->>RegServ: Vehiculo guardado

    %% Asignar celda
    RegServ->>CeldaServ: asignarCeldaDisponible(tipoVehiculo)
    CeldaServ->>CeldaServ: buscar y marcar celda como OCUPADA
    CeldaServ-->>RegServ: Celda asignada (ej. A-01)

    %% Guardar Registro
    RegServ->>RegRepo: save(Registro {vehiculo, celda, ACTIVO, ...})
    RegRepo-->>RegServ: Registro persistido

    %% Generar Comprobante QR
    RegServ->>CompServ: generarComprobanteEntrada(registro)
    CompServ->>CompServ: Generar código ticket y QR Base64
    CompServ-->>RegServ: Comprobante generado

    %% Auditoría
    RegServ->>AuditServ: registrar(usuario, "REGISTRO_ENTRADA", "registro", registro.id)

    RegServ-->>RegCtrl: RegistroDTO con comprobante QR
    RegCtrl-->>Operador: 201 Created {placa, celda: "A-01", codigoComprobante, qrBase64}
```

### 3.3 Flujo 3: Registro de Salida y Liberación de Celda
Ilustra la búsqueda del vehículo, cierre de estancia, cálculo de permanencia, liberación física de la celda y comprobante de egreso.

```mermaid
sequenceDiagram
    autonumber
    actor Operador as Operador
    participant RegCtrl as RegistroController
    participant RegServ as RegistroService
    participant CeldaServ as CeldaService
    participant CompServ as ComprobanteService
    participant RegRepo as RegistroRepository
    participant AuditServ as AuditoriaService

    Operador->>RegCtrl: PUT /api/v1/registros/salida/{placa} (RegistroSalidaDTO)
    RegCtrl->>RegServ: registrarSalida(placa, salidaDTO, usuarioCorreo)

    RegServ->>RegRepo: findByVehiculoPlacaAndEstado(placa, ACTIVO)
    alt Registro no existe
        RegRepo-->>RegServ: Optional.empty()
        RegServ-->>RegCtrl: throw ResourceNotFoundException("No se encontró registro activo")
        RegCtrl-->>Operador: 404 Not Found
    else Registro activo encontrado
        RegServ->>RegServ: calcularDuracion(registro)
        RegServ->>RegServ: setEstado(CERRADO), setFechaHoraSalida(now)
        RegServ->>RegRepo: save(registro)
        
        %% Liberar Celda
        RegServ->>CeldaServ: liberarCelda(registro.celda.id)
        CeldaServ->>CeldaServ: celda.setEstado(DISPONIBLE)
        CeldaServ-->>RegServ: Celda liberada

        %% Generar Comprobante Salida
        RegServ->>CompServ: generarComprobanteSalida(registro)
        CompServ-->>RegServ: Comprobante salida QR

        %% Auditar
        RegServ->>AuditServ: registrar(usuario, "REGISTRO_SALIDA", "registro", registro.id)

        RegServ-->>RegCtrl: RegistroDTO (CERRADO)
        RegCtrl-->>Operador: 200 OK {placa, minutosPermanencia, celdaLiberada, comprobanteSalida}
    end
```

---

## 4. Diagrama de Estados UML

### 4.1 Ciclo de Vida de una Celda de Parqueadero
Describe las transiciones de estado de las bahías de estacionamiento.

```mermaid
stateDiagram-v2
    [*] --> DISPONIBLE : Celda creada en el sistema
    
    DISPONIBLE --> OCUPADA : Ingreso de vehículo / Asignación automática (CU-02)
    OCUPADA --> DISPONIBLE : Registro de salida (CU-06) o Cancelación (CU-09)
    
    DISPONIBLE --> MANTENIMIENTO : Daño físico / Pintura / Bloqueo preventivo
    MANTENIMIENTO --> DISPONIBLE : Mantenimiento finalizado y habilitada por Administrador
```

### 4.2 Ciclo de Vida de un Registro Vehicular

```mermaid
stateDiagram-v2
    [*] --> ACTIVO : Registro de entrada vehicular con celda asignada
    
    ACTIVO --> CERRADO : Registro de salida confirmado y celda liberada
    ACTIVO --> CANCELADO : Anulación de registro por error de digitación u operador
    
    CERRADO --> [*] : Estancia finalizada con histórico auditado
    CANCELADO --> [*] : Anulación archivada con justificación
```

---

## 5. Diagrama de Paquetes UML

Estructura de organización de paquetes Java en el proyecto Spring Boot:

```mermaid
classDiagram
    package com_autoscolombia {
        class AutosColombiaApplication
    }

    package domain {
        package enums {
            class TipoUsuario
            class TipoVehiculo
            class EstadoCelda
            class EstadoRegistro
            class TipoNovedad
            class TipoComprobante
        }
        package model {
            class Usuario
            class Vehiculo
            class Celda
            class Registro
            class Comprobante
            class Novedad
            class AuditoriaLog
        }
        package service {
            class AuthService
            class RegistroService
            class CeldaService
            class VehiculoService
            class PicoYPlacaService
            class ComprobanteService
            class NovedadService
            class ReporteService
            class AuditoriaService
        }
    }

    package infrastructure {
        package repository {
            class UsuarioRepository
            class VehiculoRepository
            class CeldaRepository
            class RegistroRepository
            class ComprobanteRepository
            class NovedadRepository
            class AuditoriaLogRepository
        }
        package exception {
            class GlobalExceptionHandler
            class ResourceNotFoundException
            class BusinessRuleException
            class AuthenticationException
        }
    }

    package presentation {
        package controllers {
            class AuthController
            class RegistroController
            class VehiculoController
            class CeldaController
            class NovedadController
            class ReporteController
            class UsuarioController
        }
        package dto {
            class AuthResponse
            class LoginRequest
            class RegistroEntradaDTO
            class RegistroSalidaDTO
            class RegistroDTO
            class VehiculoDTO
            class CeldaDTO
            class ComprobanteDTO
            class DashboardDTO
        }
        package config {
            class SecurityConfig
            class PasswordEncoderConfig
            class JwtAuthenticationFilter
        }
    }

    package util {
        class JwtUtil
    }

    presentation ..> domain : utiliza
    presentation ..> infrastructure : maneja excepciones
    domain ..> infrastructure : implementa persistencia
    presentation ..> util : valida tokens
```
