# Mockups y Arquitectura Visual de Interfaz (UI/UX)
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

## 1. Arquitectura de Información y Mapa de Navegación

Como diseño de la interfaz se define la estructura jerárquica de vistas y flujos orientada a una experiencia ágil en garita. Los wireframes de este documento son especificaciones textuales, no capturas de Figma ni un prototipo alojado allí.

```
[ Iniciar Sesión (/login) ]
            │
            ▼ (Autenticado con JWT)
┌─────────────────────────────────────────────────────────────────┐
│                      DASHBOARD PRINCIPAL                        │
│               (/dashboard - KPIs en tiempo real)                │
└───────┬─────────────────┬───────────────────┬───────────────────┘
        │                 │                   │
        ▼                 ▼                   ▼
┌──────────────┐  ┌──────────────┐    ┌──────────────┐
│  REGISTRO    │  │  REGISTRO    │    │  GESTIÓN DE  │
│  DE ENTRADA  │  │  DE SALIDA   │    │  NOVEDADES   │
│  (/entradas) │  │  (/salidas)  │    │ (/novedades) │
└───────┬──────┘  └───────┬──────┘    └───────┬──────┘
        │                 │                   │
        ▼                 ▼                   │
┌──────────────┐  ┌──────────────┐            │
│ COMPROBANTE  │  │ COMPROBANTE  │            │
│ ENTRADA (QR) │  │ SALIDA (QR)  │            │
└──────────────┘  └──────────────┘            │
        │                 │                   │
        └─────────────────┴───────────────────┘
                          │
                          ▼
        ┌───────────────────────────────────┐
        │       REPORTES Y AUDITORÍA        │
        │ (/reportes/ocupacion - CSV/Admin) │
        └───────────────────────────────────┘
```

---

## 2. Guía de Estilos y Sistema de Diseño (Design System)

Para guiar el trabajo del **Rol 4 (Desarrollador Frontend / QA)** en Figma y en el código frontend, se definen los siguientes tokens de diseño:

### 2.1 Paleta Cromática
* **Color Primario (Brand):** `#1E3A8A` (Azul Noche Institucional) – Representa seguridad, solidez y seriedad.
* **Color Secundario:** `#2563EB` (Azul Royal) – Botones de acción principal (CTA), enlaces activos.
* **Color Éxito:** `#16A34A` (Verde Esmeralda) – Celda disponible, ingreso exitoso, vehículo admitido.
* **Color Advertencia / Alerta:** `#EA580C` (Naranja Óxido) – Alerta de Pico y Placa, novedad pendiente.
* **Color Error:** `#DC2626` (Rojo Carmesí) – Celda ocupada, autenticación fallida, bloqueo de cuenta.
* **Fondos y Neutros:** `#F8FAFC` (Gris Pálido de Fondo), `#1E293B` (Texto Principal), `#64748B` (Texto Secundario/Placeholders).

### 2.2 Tipografía
* **Familia Tipográfica:** `Inter`, `Segoe UI`, `system-ui`, sans-serif.
* **Escala:**
  * H1 (Títulos de Módulo): `24px` / Semibold (600)
  * H2 (Subtítulos y Tarjetas): `18px` / Medium (500)
  * Body (Textos generales y formularios): `14px` / Regular (400)
  * Small/Badges (Placas, estados): `12px` / Bold (700)

---

## 3. Wireframes y Mockups Estructurados

**Estado de implementación:** se implementó una interfaz web adaptable en `src/main/resources/static/index.html`, `styles.css` y `app.js`, servida por Spring Boot y conectada a la API. Esta entrega no es un prototipo Figma; para acreditar el requisito específico de Figma aún se debe crear/publicar el archivo en esa plataforma y añadir aquí su enlace y capturas.

### Mockup 1: Pantalla de Autenticación / Login
* **Ruta:** `/login` | **Actor:** Operador / Administrador  
* **Objetivo:** Identificación segura con credenciales institucionales y recepción del token JWT.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        AUTOS COLOMBIA S.A.S.                           │
│                   Control de Parqueadero - v1.0                        │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│                      ┌────────────────────────┐                        │
│                      │     INICIAR SESIÓN     │                        │
│                      │                        │                        │
│                      │ Correo Electrónico:    │                        │
│                      │ [ operador@autos.com ] │                        │
│                      │                        │                        │
│                      │ Contraseña:            │                        │
│                      │ [ **********         ] │                        │
│                      │                        │                        │
│                      │  [   INGRESAR AL S.I.  ]                        │
│                      │                        │                        │
│                      │ Máx. 3 intentos seguidos│                       │
│                      └────────────────────────┘                        │
│                                                                        │
│          IU Digital de Antioquia - Evidencia de Aprendizaje 1          │
└────────────────────────────────────────────────────────────────────────┘
```

---

### Mockup 2: Dashboard General de Control y Ocupación
* **Ruta:** `/dashboard` | **Actor:** Operador / Administrador  
* **Objetivo:** Vista panorámica de celdas disponibles, ocupación en porcentaje y accesos rápidos a las funciones de garita.

```
┌────────────────────────────────────────────────────────────────────────┐
│ AUTOS COLOMBIA  |  Dashboard   [+ Entrada]   [- Salida]   [Operador ▼] │
├────────────────────────────────────────────────────────────────────────┤
│  MÉTRICAS EN TIEMPO REAL                                               │
│ ┌────────────────┐ ┌────────────────┐ ┌────────────────┐ ┌───────────┐ │
│ │  TOTAL CELDAS  │ │ DISPONIBLES    │ │ OCUPADAS       │ │ OCUPACIÓN │ │
│ │      16        │ │      12        │ │       4        │ │   25.0%   │ │
│ └────────────────┘ └────────────────┘ └────────────────┘ └───────────┘ │
│                                                                        │
│  DISPONIBILIDAD POR TIPO DE VEHÍCULO                                   │
│  • CARROS:      [ 6 Libres /  8 Celdas ]   [======----] 25% Ocupado    │
│  • MOTOS:       [ 3 Libres /  4 Celdas ]   [===-------] 25% Ocupado    │
│  • CAMIONETAS:  [ 3 Libres /  4 Celdas ]   [===-------] 25% Ocupado    │
│                                                                        │
│  ÚLTIMOS INGRESOS (En curso)                                           │
│  ┌─────────┬────────────┬─────────────┬──────────────┬───────────────┐ │
│  │ PLACA   │ VEHÍCULO   │ CELDA ASIG. │ HORA ENTRADA │ ESTADO        │ │
│  ├─────────┼────────────┼─────────────┼──────────────┼───────────────┤ │
│  │ ABC-123 │ Mazda 3    │ A-01        │ 14:32:10     │ [ ACTIVO ]    │ │
│  │ XYZ-987 │ Pulsar 200 │ B-01        │ 14:50:00     │ [ ACTIVO ]    │ │
│  │ DEF-456 │ Toyota TXL │ C-01        │ 15:10:22     │ [ ACTIVO ]    │ │
│  └─────────┴────────────┴─────────────┴──────────────┴───────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

---

### Mockup 3: Módulo de Registro de Entrada Vehicular
* **Ruta:** `/registros/entrada` | **Actor:** Operador  
* **Objetivo:** Registro rápido en garita en menos de 10 segundos, con verificación automática de Pico y Placa y asignación de celda.

```
┌────────────────────────────────────────────────────────────────────────┐
│ AUTOS COLOMBIA  |  Registro de Entrada Vehicular                       │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  DATOS DEL VEHÍCULO                                                    │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │ Placa:              [ ABC123    ]  (Al escribir valida formato)   │ │
│  │ Tipo:               (•) Carro   ( ) Moto   ( ) Camioneta          │ │
│  │ Marca:              [ Chevrolet           ]                       │ │
│  │ Modelo:             [ Onix Sedan          ]                       │ │
│  │ Color:              [ Gris Platino        ]                       │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                        │
│  ESTADO DE RESTRICCIÓN (Bogotá D.C.)                                   │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │ [✓] SIN RESTRICCIÓN DE PICO Y PLACA PARA EL DÍA DE HOY            │ │
│  │ (Día hábil: Placas terminadas en 2 y 3 no tienen restricción hoy) │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                        │
│  DATOS DEL PROPIETARIO (Opcional - Clientes Mensualidad)               │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │ Cédula Propietario: [ 1018400123  ]   Nombre: [ Juan Pérez Gómez ]│ │
│  │ Teléfono Contacto:  [ 3001234567  ]                               │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                        │
│  ASIGNACIÓN DE CELDA AUTOMÁTICA                                        │
│  Celda sugerida por el sistema: [ A-02 - Piso 1 (DISPONIBLE) ]         │
│                                                                        │
│            [ CANCELAR ]           [ REGISTRAR E IMPRIMIR TICKET QR ]   │
└────────────────────────────────────────────────────────────────────────┘
```

---

### Mockup 4: Módulo de Registro de Salida Vehicular
* **Ruta:** `/registros/salida` | **Actor:** Operador  
* **Objetivo:** Búsqueda por placa, liquidación de permanencia, liberación automática de celda e impresión de comprobante de salida.

```
┌────────────────────────────────────────────────────────────────────────┐
│ AUTOS COLOMBIA  |  Registro de Salida Vehicular                        │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  BÚSQUEDA DE VEHÍCULO EN INSTALACIONES                                 │
│  Ingrese Placa: [ ABC123       ]   [ BUSCAR REGISTRO ACTIVO ]          │
│                                                                        │
│  RESUMEN DE ESTANCIA ENCONTRADO                                        │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │ Placa:           ABC-123             Celda Ocupada:   A-01 (Piso 1)│
│  │ Tipo Vehículo:   CARRO               Propietario:     Carlos Gómez │ │
│  │ Hora de Entrada: 17/10/2026 14:00    Hora Actual:     16:45        │ │
│  │ Tiempo Estancia: 2 horas y 45 minutos (165 minutos)                │ │
│  │ Novedades:       Ninguna registrada                                │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                        │
│  Observaciones de Salida:                                              │
│  [ Retira sin incidentes.                                            ] │
│                                                                        │
│    [ VOLVER AL DASHBOARD ]       [ CONFIRMAR SALIDA Y LIBERAR CELDA ]  │
└────────────────────────────────────────────────────────────────────────┘
```

---

### Mockup 5: Módulo de Gestión de Novedades e Incidentes
* **Ruta:** `/novedades` | **Actor:** Operador  
* **Objetivo:** Dejar constancia formal e inmutable de cualquier anomalía o daño físico en el vehículo.

```
┌────────────────────────────────────────────────────────────────────────┐
│ AUTOS COLOMBIA  |  Reporte de Novedad / Daño                           │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  VINCULACIÓN DE ESTANCIA                                               │
│  Placa del Vehículo:   [ ABC123       ]  Registro ID: #1042            │
│                                                                        │
│  DETALLE DEL INCIDENTE                                                 │
│  Tipo de Novedad:      [ RAYÓN EN CARROCERÍA                ▼ ]        │
│                        - RAYON                                         │
│                        - DANO_MECANICO                                 │
│                        - OBJETO_CAIDO                                  │
│                        - ROBO_PARCIAL                                  │
│                        - OTRO                                          │
│                                                                        │
│  Descripción Detallada del Incidente:                                  │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │ Se detecta rayón de 15 cm en la puerta delantera derecha al      │ │
│  │ momento de la recepción del vehículo en garita.                   │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                        │
│  Soporte Fotográfico / URL:                                            │
│  [ https://storage.autoscolombia.com/evidencias/2026/abc123_01.jpg   ] │
│                                                                        │
│  Operador Responsable: Carlos Ramírez (ID: 1)                          │
│                                                                        │
│             [ DESCARTAR ]            [ GUARDAR NOVEDAD Y NOTIFICAR ]   │
└────────────────────────────────────────────────────────────────────────┘
```

---

### Mockup 6: Vista de Comprobante / Ticket con Código QR
* **Componente:** Modal / Impresión POS Térmica (80 mm)  
* **Objetivo:** Documento legal de entrega al cliente conductor y soporte de egreso.

```
┌────────────────────────────────────────────────────┐
│              AUTOS COLOMBIA S.A.S.                 │
│              NIT: 900.854.120-1                    │
│          Medellín, Antioquia, Colombia             │
│            Teléfono: (604) 444-0123                │
├────────────────────────────────────────────────────┤
│           COMPROBANTE DE ENTRADA                   │
│ Ticket Nº: TKT-ENT-20261017-0042                   │
│ Fecha / Hora: 17/10/2026 14:32:10                  │
│                                                    │
│ Placa:       ABC-123                               │
│ Tipo:        CARRO                                 │
│ Celda:       A-01 (Piso 1)                         │
│ Operador:    María López                           │
├────────────────────────────────────────────────────┤
│                                                    │
│               ██████████  ██  ██████████           │
│               ██      ██  ██  ██      ██           │
│               ██  ██  ██  ██  ██  ██  ██           │
│               ██████████  ██  ██████████           │
│               ██████████████████████████           │
│               ██  ██  ██  ██  ██  ██  ██           │
│               ██████████  ██  ██████████           │
│                                                    │
│             Escanee para validar ticket            │
├────────────────────────────────────────────────────┤
│ Conserve este ticket. En caso de pérdida se        │
│ requerirá acreditación de propiedad del vehículo.  │
│          ¡Gracias por su confianza!                │
└────────────────────────────────────────────────────┘
```

---

## 4. Lineamientos de Integración para el Desarrollador Frontend / QA

1. **Autenticación:**
   * El token JWT recibido en `/api/v1/auth/login` debe almacenarse en memoria segura o `sessionStorage` (evitar `localStorage` desprotegido contra XSS).
   * Adjuntar el encabezado HTTP: `Authorization: Bearer <TOKEN>` en cada petición.
2. **Estados Asíncronos:**
   * Mostrar componente *Skeleton Loading* o *Spinner* durante las consultas al backend.
   * Si la API retorna código `409 Conflict`, mostrar modal advirtiendo: *"El vehículo ya cuenta con una entrada activa"*.
   * Si la API retorna código `401 Unauthorized`, redirigir inmediatamente a la pantalla de `/login`.
3. **Impresión Térmica:**
   * Utilizar CSS `@media print` para ajustar el tamaño del comprobante al estándar de papel térmico de 80mm de ancho.
