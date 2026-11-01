package com.autoscolombia.integration;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.EstadoRegistro;
import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.AuditoriaLog;
import com.autoscolombia.domain.model.Celda;
import com.autoscolombia.domain.model.Registro;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.domain.model.Vehiculo;
import com.autoscolombia.domain.service.CeldaService;
import com.autoscolombia.domain.service.RegistroService;
import com.autoscolombia.infrastructure.repository.AuditoriaLogRepository;
import com.autoscolombia.infrastructure.repository.CeldaRepository;
import com.autoscolombia.infrastructure.repository.ClienteRepository;
import com.autoscolombia.infrastructure.repository.ComprobanteRepository;
import com.autoscolombia.infrastructure.repository.NovedadRepository;
import com.autoscolombia.infrastructure.repository.RegistroRepository;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import com.autoscolombia.infrastructure.repository.VehiculoRepository;
import com.autoscolombia.presentation.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Celdas, su integración con entrada/salida/cancelación y la integridad de la ocupación (bloque B3).
 *
 * <p>Corre sobre H2 en modo PostgreSQL con {@code ddl-auto: create-drop} y Flyway apagado: las columnas y
 * restricciones se generan desde las entidades. Por eso {@code V3__gestion_celdas_e_integridad_ocupacion.sql}
 * no se interpreta aquí, y en particular <b>los índices únicos parciales no se dan por probados con H2</b>:
 * lo que estas pruebas demuestran son las reglas del servicio, los códigos de la API y que dos
 * transacciones concurrentes no dejan doble ocupación. La aplicación de V3 sobre PostgreSQL queda pendiente
 * de autorización.
 *
 * <p>Los tokens son JWT reales obtenidos en {@code /api/v1/auth/login}; ninguna prueba sustituye la cadena de
 * seguridad por un usuario simulado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CeldasIntegridadIntegrationTest {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    /** Sábado: Pico y Placa no aplica y las pruebas no dependen del día real del runner. */
    private static final LocalDateTime SABADO_SIN_RESTRICCION =
            LocalDateTime.of(2026, Month.NOVEMBER, 7, 10, 0);

    private static final String CLAVE_ADMIN = "Clave administrativa de prueba B3";
    private static final String CLAVE_OPERADOR = "Clave de operador válida B3";

    private static final String RUTA_CELDAS = "/api/v1/celdas";

    private static final AtomicLong SECUENCIA = new AtomicLong();

    static class RelojDePrueba extends Clock {

        private final ZoneId zone;
        private Instant instant;

        RelojDePrueba(LocalDateTime inicial, ZoneId zone) {
            this.zone = zone;
            this.instant = inicial.atZone(zone).toInstant();
        }

        void fijar(LocalDateTime nuevaFechaHora) {
            this.instant = nuevaFechaHora.atZone(zone).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zonaAlternativa) {
            return new RelojDePrueba(LocalDateTime.ofInstant(instant, zonaAlternativa), zonaAlternativa);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class TestClockConfig {

        @Bean
        public RelojDePrueba relojDePruebaCeldas() {
            return new RelojDePrueba(SABADO_SIN_RESTRICCION, ZONA_BOGOTA);
        }

        @Bean
        @Primary
        public Clock relojPrimarioCeldas(RelojDePrueba relojDePruebaCeldas) {
            return relojDePruebaCeldas;
        }
    }

    @Autowired private RelojDePrueba reloj;
    @Autowired private MockMvc mockMvc;
    @Autowired private RegistroService registroService;
    @Autowired private CeldaRepository celdaRepository;
    @Autowired private RegistroRepository registroRepository;
    @Autowired private VehiculoRepository vehiculoRepository;
    @Autowired private ComprobanteRepository comprobanteRepository;
    @Autowired private NovedadRepository novedadRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private AuditoriaLogRepository auditoriaLogRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private String correoAdmin;
    private String correoOperador;
    private String tokenAdmin;
    private String tokenOperador;

    @BeforeEach
    void setUp() throws Exception {
        reloj.fijar(SABADO_SIN_RESTRICCION);

        comprobanteRepository.deleteAll();
        novedadRepository.deleteAll();
        registroRepository.deleteAll();
        vehiculoRepository.deleteAll();
        clienteRepository.deleteAll();
        auditoriaLogRepository.deleteAll();
        celdaRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario admin = seed(TipoUsuario.ADMINISTRADOR, CLAVE_ADMIN);
        Usuario operador = seed(TipoUsuario.OPERADOR, CLAVE_OPERADOR);
        correoAdmin = admin.getCorreo();
        correoOperador = operador.getCorreo();
        tokenAdmin = login(correoAdmin, CLAVE_ADMIN);
        tokenOperador = login(correoOperador, CLAVE_OPERADOR);
    }

    // ------------------------------------------------------------------ utilidades

    private long secuencia() {
        return SECUENCIA.incrementAndGet();
    }

    private String correo(long n) {
        return "cuenta" + n + ".b3@test.com";
    }

    private Usuario seed(TipoUsuario tipo, String clave) {
        long n = secuencia();
        return usuarioRepository.save(Usuario.builder()
                .nombre("Personal").apellido("De prueba")
                .documento("93" + String.format("%08d", n))
                .correo(correo(n))
                .telefono("3001112233")
                .claveHash(passwordEncoder.encode(clave))
                .tipo(tipo)
                .activo(true)
                .build());
    }

    private String login(String correo, String clave) throws Exception {
        String cuerpo = objectMapper.writeValueAsString(
                LoginRequest.builder().correo(correo).clave(clave).build());
        String respuesta = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(respuesta).get("token").asText();
    }

    /** Código único por prueba, en minúsculas y rodeado de espacios: el servicio debe normalizarlo. */
    private String codigoEnBlancos(String prefijo) {
        return "  " + prefijo + secuencia() + "  ";
    }

    private static String enMayusculas(String codigo) {
        return codigo.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private Celda nuevaCelda(String numero, TipoVehiculo tipo, EstadoCelda estado, boolean activo) {
        return celdaRepository.save(Celda.builder()
                .numero(numero).piso(1).tipoVehiculo(tipo).estado(estado).activo(activo).build());
    }

    private Celda carroLibre(String numero) {
        return nuevaCelda(numero, TipoVehiculo.CARRO, EstadoCelda.DISPONIBLE, true);
    }

    private String cuerpoCelda(String numero, int piso, String tipo, String observaciones) {
        return "{\"numero\":" + jsonTexto(numero) + ",\"piso\":" + piso + ",\"tipoVehiculo\":\"" + tipo
                + "\",\"observaciones\":" + jsonTexto(observaciones) + "}";
    }

    private String jsonTexto(String valor) {
        if (valor == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ResultActions crearCelda(String cuerpoJson, String token) throws Exception {
        return mockMvc.perform(post(RUTA_CELDAS)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoJson.getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions editarCelda(long id, String cuerpoJson, String token) throws Exception {
        return mockMvc.perform(put(RUTA_CELDAS + "/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoJson.getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions cambiarActivoCelda(long id, boolean activo, String token) throws Exception {
        return mockMvc.perform(patch(RUTA_CELDAS + "/" + id + "/activo")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"activo\":" + activo + "}").getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions cambiarEstadoCelda(long id, String estado, String token) throws Exception {
        return mockMvc.perform(put(RUTA_CELDAS + "/" + id + "/estado")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"estado\":\"" + estado + "\"}").getBytes(StandardCharsets.UTF_8)));
    }

    /** Entrada por la API real: la ocupación de la celda tiene que llegar de aquí, no de una escritura a mano. */
    private MvcResult entrar(String placa, TipoVehiculo tipo, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/registros/entrada")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entradaJson(placa, tipo).getBytes(StandardCharsets.UTF_8)))
                .andReturn();
    }

    private String entradaJson(String placa, TipoVehiculo tipo) {
        return "{\"placa\":\"" + placa + "\",\"tipo\":\"" + tipo + "\",\"marca\":\"Mazda\",\"modelo\":\"3\","
                + "\"color\":\"Rojo\"}";
    }

    private ResultActions salir(String placa, String observaciones, String token) throws Exception {
        return mockMvc.perform(put("/api/v1/registros/salida/" + placa)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"observaciones\":" + jsonTexto(observaciones) + "}")
                        .getBytes(StandardCharsets.UTF_8)));
    }

    private Vehiculo vehiculo(String placa, TipoVehiculo tipo) {
        return vehiculoRepository.save(Vehiculo.builder()
                .placa(placa).marca("Mazda").modelo("3").color("Rojo").tipo(tipo)
                .propietarioNombre("Prueba").propietarioDocumento("9000000" + secuencia()).build());
    }

    private Registro registroActivo(Vehiculo vehiculo, Celda celda) {
        return registroRepository.save(Registro.builder()
                .vehiculo(vehiculo).celda(celda)
                .usuarioEntrada(usuarioRepository.findByCorreo(correoOperador).orElseThrow())
                .fechaHoraEntrada(SABADO_SIN_RESTRICCION)
                .estado(EstadoRegistro.ACTIVO)
                .build());
    }

    private Celda recargar(long id) {
        return celdaRepository.findById(id).orElseThrow();
    }

    private long contadoresDeAuditoriaDeCelda() {
        return auditoriaLogRepository.findAll().stream()
                .filter(log -> "Celda".equals(log.getEntidadAfectada()))
                .count();
    }

    // ------------------------------------------------------------------ 1, 2 y 3: alta

    @Test
    void case01_crearCeldaValidaResponde201NaceActivaYDisponible() throws Exception {
        String conBlancos = codigoEnBlancos("cc");
        String codigo = enMayusculas(conBlancos);

        crearCelda(cuerpoCelda(conBlancos, 2, "CAMIONETA", "junta a la rampa"), tokenAdmin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(codigo))
                .andExpect(jsonPath("$.piso").value(2))
                .andExpect(jsonPath("$.tipoVehiculo").value("CAMIONETA"))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.observaciones").value("junta a la rampa"))
                .andExpect(jsonPath("$.id").isNumber());

        assertTrue(celdaRepository.findByNumero(codigo).isPresent());
    }

    @Test
    void case02_crearCeldaConCodigoDuplicadoDevuelve409YNoDejaFila() throws Exception {
        carroLibre("A-101");

        crearCelda(cuerpoCelda("a-101", 1, "CARRO", "repetida"), tokenAdmin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CeldaService.MENSAJE_CODIGO_DUPLICADO));

        assertEquals(1L, celdaRepository.count());
    }

    @Test
    void case03_camposInvalidosDevuelven400YSinEscritura() throws Exception {
        crearCelda(cuerpoCelda("", 1, "CARRO", null), tokenAdmin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.numero").exists());

        crearCelda(cuerpoCelda("A-202", 0, "CARRO", null), tokenAdmin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.piso").exists());

        crearCelda("{\"numero\":\"A-203\",\"piso\":1,\"tipoVehiculo\":\"SUBMARINO\"}", tokenAdmin)
                .andExpect(status().isBadRequest());

        crearCelda("{\"numero\":\"A-204\",\"piso\":1}", tokenAdmin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.tipoVehiculo").exists());

        assertEquals(0L, celdaRepository.count());
    }

    // ------------------------------------------------------------------ 4, 5: edición

    @Test
    void case04_editarCeldaLibreCambiaCodigoPisoYTipo() throws Exception {
        Celda celda = carroLibre("A-301");

        editarCelda(celda.getId(), cuerpoCelda("d-9", 5, "MOTO", "re asignada"), tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("D-9"))
                .andExpect(jsonPath("$.piso").value(5))
                .andExpect(jsonPath("$.tipoVehiculo").value("MOTO"))
                .andExpect(jsonPath("$.observaciones").value("re asignada"));
    }

    @Test
    void case05_editarCeldaOcupadaRechazaCambiarIdentidadFisica() throws Exception {
        Celda celda = carroLibre("A-302");
        entrar("DEF302", TipoVehiculo.CARRO, tokenOperador);

        editarCelda(celda.getId(), cuerpoCelda("Z-1", 9, "MOTO", "sin cambios"), tokenAdmin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CeldaService.MENSAJE_CELDA_OCUPADA_NO_EDITABLE));

        Celda despues = recargar(celda.getId());
        assertEquals("A-302", despues.getNumero());
        assertEquals(1, despues.getPiso());
        assertEquals(TipoVehiculo.CARRO, despues.getTipoVehiculo());
    }

    @Test
    void case05b_editarCeldaOcupadaPermiteObservacionesYNoLiberaLaCelda() throws Exception {
        Celda celda = carroLibre("A-303");
        entrar("DEF303", TipoVehiculo.CARRO, tokenOperador);

        editarCelda(celda.getId(), cuerpoCelda("A-303", 1, "CARRO", "rayón reportado en la salida"), tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observaciones").value("rayón reportado en la salida"))
                .andExpect(jsonPath("$.estado").value("OCUPADA"));
    }

    @Test
    void case05c_editarConCodigoDeOtraCeldaDevuelve409() throws Exception {
        carroLibre("A-304");
        Celda segunda = carroLibre("A-305");

        editarCelda(segunda.getId(), cuerpoCelda("A-304", 1, "CARRO", null), tokenAdmin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CeldaService.MENSAJE_CODIGO_DUPLICADO));

        assertEquals("A-305", recargar(segunda.getId()).getNumero());
    }

    // ------------------------------------------------------------------ 6, 7, 8, 9: estado lógico

    @Test
    void case06_desactivarCeldaConHistorialCerradoConservaElEstadoOperativo() throws Exception {
        Celda celda = carroLibre("A-401");
        entrar("DEF401", TipoVehiculo.CARRO, tokenOperador);
        salir("DEF401", null, tokenOperador).andExpect(status().isOk());

        cambiarActivoCelda(celda.getId(), false, tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.disponible").value(false));

        assertEquals(EstadoCelda.DISPONIBLE, recargar(celda.getId()).getEstado());
        assertFalse(recargar(celda.getId()).getActivo());
    }

    @Test
    void case07_desactivarCeldaOcupadaSeRechazaPor409() throws Exception {
        Celda celda = carroLibre("A-402");
        entrar("DEF402", TipoVehiculo.CARRO, tokenOperador);

        cambiarActivoCelda(celda.getId(), false, tokenAdmin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CeldaService.MENSAJE_CELDA_OCUPADA_NO_DESACTIVABLE));

        assertTrue(recargar(celda.getId()).getActivo());
        assertEquals(EstadoCelda.OCUPADA, recargar(celda.getId()).getEstado());
    }

    @Test
    void case08_reactivarNoQuitaElMantenimientoPendiente() throws Exception {
        Celda celda = carroLibre("A-403");

        cambiarEstadoCelda(celda.getId(), "MANTENIMIENTO", tokenAdmin).andExpect(status().isOk());
        cambiarActivoCelda(celda.getId(), false, tokenAdmin).andExpect(status().isOk());

        cambiarActivoCelda(celda.getId(), true, tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.estado").value("MANTENIMIENTO"))
                .andExpect(jsonPath("$.disponible").value(false));

        Celda reactivada = recargar(celda.getId());
        assertEquals(EstadoCelda.MANTENIMIENTO, reactivada.getEstado());
    }

    @Test
    void case09_mantenimientoSoloConCeldaActivaYSinOcupacion() throws Exception {
        Celda inactiva = nuevaCelda("A-404", TipoVehiculo.CARRO, EstadoCelda.DISPONIBLE, false);

        cambiarEstadoCelda(inactiva.getId(), "MANTENIMIENTO", tokenAdmin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CeldaService.MENSAJE_CELDA_INACTIVA));

        Celda ocupada = carroLibre("A-405");
        entrar("DEF405", TipoVehiculo.CARRO, tokenOperador);

        // La coherencia se cumple (OCUPADA con su registro ACTIVO), pero ocupar no es una transición manual.
        cambiarEstadoCelda(ocupada.getId(), "MANTENIMIENTO", tokenAdmin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CeldaService.MENSAJE_TRANSICION_NO_PERMITIDA));

        assertEquals(EstadoCelda.OCUPADA, recargar(ocupada.getId()).getEstado());
    }

    // ------------------------------------------------------------------ 10: OCUPADA no es manual

    @Test
    void case10_endpointManualDeEstadoRechazaOCUPADASinTocarLaBase() throws Exception {
        Celda celda = carroLibre("A-501");

        cambiarEstadoCelda(celda.getId(), "OCUPADA", tokenAdmin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CeldaService.MENSAJE_OCUPADA_NO_ASIGNABLE_MANUAL));

        assertEquals(EstadoCelda.DISPONIBLE, recargar(celda.getId()).getEstado());

        cambiarEstadoCelda(celda.getId(), "RELLERENA", tokenAdmin)
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 11 y 12: permisos

    @Test
    void case11_operadorRecibe403EnTodaLaAdministracionDeCeldas() throws Exception {
        Celda celda = carroLibre("A-601");

        crearCelda(cuerpoCelda("A-602", 1, "CARRO", null), tokenOperador)
                .andExpect(status().isForbidden());
        editarCelda(celda.getId(), cuerpoCelda("A-601", 2, "CARRO", null), tokenOperador)
                .andExpect(status().isForbidden());
        cambiarActivoCelda(celda.getId(), false, tokenOperador)
                .andExpect(status().isForbidden());
        cambiarEstadoCelda(celda.getId(), "MANTENIMIENTO", tokenOperador)
                .andExpect(status().isForbidden());

        assertEquals(1L, celdaRepository.count());
        assertTrue(recargar(celda.getId()).getActivo());
    }

    @Test
    void case11b_sinTokenODatosInvalidosLasRutasDeCeldasQuedanProtegidas() throws Exception {
        mockMvc.perform(get(RUTA_CELDAS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(RUTA_CELDAS + "/pagina")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(RUTA_CELDAS).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCelda("A-999", 1, "CARRO", null).getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(RUTA_CELDAS).header("Authorization", "Bearer token.roto"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void case12_operadorConsultaYPuedeUsarLasCeldasEnEntradaYSalida() throws Exception {
        carroLibre("A-603");

        mockMvc.perform(get(RUTA_CELDAS).header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numero").value("A-603"));

        mockMvc.perform(get(RUTA_CELDAS + "/pagina").header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        entrar("DEF603", TipoVehiculo.CARRO, tokenOperador);
        mockMvc.perform(get(RUTA_CELDAS + "/disponibles").header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ------------------------------------------------------------------ 13, 14, 15, 16, 17: flujos

    @Test
    void case13_laEntradaOcupoUnaCeldaAssignableYDejaUnRegistroActivo() throws Exception {
        Celda celda = carroLibre("A-701");

        entrar("DEF701", TipoVehiculo.CARRO, tokenOperador);

        Celda despues = recargar(celda.getId());
        assertEquals(EstadoCelda.OCUPADA, despues.getEstado());
        assertTrue(despues.getActivo());
        assertEquals(1L, registroRepository.countByEstado(EstadoRegistro.ACTIVO));
        assertEquals(1L, comprobanteRepository.count());
    }

    @Test
    void case14_laEntradaNoUsaCeldasInactivasNiEnMantenimiento() throws Exception {
        nuevaCelda("A-702", TipoVehiculo.CARRO, EstadoCelda.DISPONIBLE, false);
        nuevaCelda("A-703", TipoVehiculo.CARRO, EstadoCelda.MANTENIMIENTO, true);

        MvcResult resultado = entrar("DEF702", TipoVehiculo.CARRO, tokenOperador);

        assertEquals(409, resultado.getResponse().getStatus());
        assertTrue(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8)
                .contains("No hay celdas disponibles"));
        assertEquals(0L, registroRepository.count());
        assertEquals(EstadoCelda.DISPONIBLE, recargar(idDeCelda("A-702")).getEstado());
    }

    @Test
    void case14b_entradaUsaLaActivaYDejaLaInactivaIntacta() throws Exception {
        nuevaCelda("A-704", TipoVehiculo.CARRO, EstadoCelda.DISPONIBLE, false);
        Celda activa = carroLibre("A-705");

        entrar("DEF705", TipoVehiculo.CARRO, tokenOperador);

        assertEquals(EstadoCelda.OCUPADA, recargar(activa.getId()).getEstado());
        assertEquals(EstadoCelda.DISPONIBLE, recargar(idDeCelda("A-704")).getEstado());
        assertFalse(recargar(idDeCelda("A-704")).getActivo());
    }

    @Test
    void case15_laSalidaLiberaUnicamenteLaCeldaDelRegistro() throws Exception {
        Celda primera = carroLibre("A-801");
        Celda segunda = carroLibre("A-802");
        entrar("DEF801", TipoVehiculo.CARRO, tokenOperador);
        entrar("DEF802", TipoVehiculo.CARRO, tokenOperador);

        assertEquals(EstadoCelda.OCUPADA, recargar(primera.getId()).getEstado());
        assertEquals(EstadoCelda.OCUPADA, recargar(segunda.getId()).getEstado());

        salir("DEF801", "salió por la rampa", tokenOperador)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADO"));

        assertEquals(EstadoCelda.DISPONIBLE, recargar(primera.getId()).getEstado());
        assertEquals(EstadoCelda.OCUPADA, recargar(segunda.getId()).getEstado());
        assertEquals(1L, registroRepository.countByEstado(EstadoRegistro.ACTIVO));
    }

    @Test
    void case16_cancelacionDejaCeldaLibreRegistroCanceladoYSinComprobanteDeSalida() throws Exception {
        Celda celda = carroLibre("A-803");
        MvcResult entrada = entrar("DEF803", TipoVehiculo.CARRO, tokenOperador);
        long registroId = objectMapper.readTree(entrada.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("id").asLong();
        long comprobantesDeEntrada = comprobanteRepository.count();

        mockMvc.perform(delete("/api/v1/registros/" + registroId)
                        .header("Authorization", "Bearer " + tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"ingreso duplicado por error\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.observaciones").value("ingreso duplicado por error"));

        assertEquals(EstadoCelda.DISPONIBLE, recargar(celda.getId()).getEstado());
        assertEquals(0L, registroRepository.countByEstado(EstadoRegistro.ACTIVO));
        assertEquals(comprobantesDeEntrada, comprobanteRepository.count());
    }

    @Test
    void case17_repetirLaSalidaNoGeneraLiberacionNIComprobanteDuplicado() throws Exception {
        Celda celda = carroLibre("A-804");
        entrar("DEF804", TipoVehiculo.CARRO, tokenOperador);
        salir("DEF804", null, tokenOperador).andExpect(status().isOk());

        long comprobantes = comprobanteRepository.count();
        long auditoriaCeldas = contadoresDeAuditoriaDeCelda();

        salir("DEF804", null, tokenOperador).andExpect(status().isNotFound());

        assertEquals(comprobantes, comprobanteRepository.count());
        assertEquals(auditoriaCeldas, contadoresDeAuditoriaDeCelda());
        assertEquals(EstadoCelda.DISPONIBLE, recargar(celda.getId()).getEstado());
    }

    @Test
    void case17b_repetirDesactivacionYNinguneoDeEstadoNoVuelvenAEscribirNiAuditar() throws Exception {
        Celda celda = carroLibre("A-805");
        cambiarActivoCelda(celda.getId(), false, tokenAdmin).andExpect(status().isOk());
        long trasDesactivar = contadoresDeAuditoriaDeCelda();

        cambiarActivoCelda(celda.getId(), false, tokenAdmin).andExpect(status().isOk());
        assertEquals(trasDesactivar, contadoresDeAuditoriaDeCelda());

        cambiarEstadoCelda(celda.getId(), "MANTENIMIENTO", tokenAdmin)
                .andExpect(status().isConflict());
        assertEquals(trasDesactivar, contadoresDeAuditoriaDeCelda());
    }

    // ------------------------------------------------------------------ 18: conflicto y rollback

    @Test
    void case18_conflictoDeCeldaCompartidaRevierteTodoYNoDejaRegistroParcial() throws Exception {
        Celda celda = carroLibre("A-901");
        Registro primero = registroActivo(vehiculo("DEF901", TipoVehiculo.CARRO), celda);
        celda.setEstado(EstadoCelda.OCUPADA);
        celdaRepository.save(celda);
        Registro segundo = registroActivo(vehiculo("XYZ901", TipoVehiculo.CARRO), recargar(celda.getId()));

        salir("XYZ901", "debería rechazarce", tokenOperador)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CeldaService.MENSAJE_CELDA_EN_OTRO_REGISTRO));

        // Rollback completo: el segundo sigue ACTIVO, la celda sigue OCUPADA y no hay comprobante de salida.
        assertEquals(EstadoRegistro.ACTIVO, registroRepository.findById(segundo.getId())
                .orElseThrow().getEstado());
        assertEquals(EstadoRegistro.ACTIVO, registroRepository.findById(primero.getId())
                .orElseThrow().getEstado());
        assertEquals(EstadoCelda.OCUPADA, recargar(celda.getId()).getEstado());
        assertEquals(0L, comprobanteRepository.count());
    }

    @Test
    void case18b_celdaOcupadaSinRegistroActivoSeRechazaComoInconsistencia() throws Exception {
        Celda inconsistente = nuevaCelda("A-902", TipoVehiculo.CARRO, EstadoCelda.OCUPADA, true);

        cambiarActivoCelda(inconsistente.getId(), false, tokenAdmin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CeldaService.MENSAJE_INCONSISTENCIA));

        editarCelda(inconsistente.getId(), cuerpoCelda("A-902", 2, "CARRO", null), tokenAdmin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CeldaService.MENSAJE_INCONSISTENCIA));

        cambiarEstadoCelda(inconsistente.getId(), "DISPONIBLE", tokenAdmin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CeldaService.MENSAJE_INCONSISTENCIA));

        assertEquals(EstadoCelda.OCUPADA, recargar(inconsistente.getId()).getEstado());
        assertTrue(recargar(inconsistente.getId()).getActivo());
    }

    // ------------------------------------------------------------------ 19: filtros y paginación

    @Test
    void case19_filtrosYPaginacionDeLaListaAdministrativa() throws Exception {
        carroLibre("B-01");
        nuevaCelda("B-02", TipoVehiculo.MOTO, EstadoCelda.MANTENIMIENTO, true);
        nuevaCelda("B-03", TipoVehiculo.MOTO, EstadoCelda.DISPONIBLE, false);
        nuevaCelda("C-01", TipoVehiculo.CAMIONETA, EstadoCelda.DISPONIBLE, true);

        mockMvc.perform(get(RUTA_CELDAS + "/pagina?size=2&page=0").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content[0].numero").value("B-01"));

        mockMvc.perform(get(RUTA_CELDAS + "/pagina?activo=false").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].numero").value("B-03"));

        mockMvc.perform(get(RUTA_CELDAS + "/pagina?estado=MANTENIMIENTO").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].numero").value("B-02"))
                .andExpect(jsonPath("$.content[0].disponible").value(false));

        mockMvc.perform(get(RUTA_CELDAS + "/pagina?tipo=MOTO&activo=true").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Búsqueda sin distinguir mayúsculas: "b-0" aparece en B-01, B-02 y B-03, no en C-01.
        mockMvc.perform(get(RUTA_CELDAS + "/pagina?criterio=b-0").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get(RUTA_CELDAS + "/pagina?piso=1&size=100").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));

        mockMvc.perform(get(RUTA_CELDAS + "/pagina?size=1000").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void case19b_elArrayHeredadoSeConservaYDisponiblesExcluyeInactivasYMantenimiento() throws Exception {
        carroLibre("B-10");
        nuevaCelda("B-11", TipoVehiculo.CARRO, EstadoCelda.MANTENIMIENTO, true);
        nuevaCelda("B-12", TipoVehiculo.CARRO, EstadoCelda.DISPONIBLE, false);

        mockMvc.perform(get(RUTA_CELDAS).header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)));

        mockMvc.perform(get(RUTA_CELDAS + "/disponibles?tipo=CARRO").header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("B-10")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("B-11"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("B-12"))));
    }

    // ------------------------------------------------------------------ 20: auditoría

    @Test
    void case20_lasCuatroOperacionseAdministrativasDejanAuditoriaSinDatosPersonales() throws Exception {
        Celda celda = carroLibre("B-20");

        crearCelda(cuerpoCelda("B-21", 1, "CARRO", "observación con <script>alert(1)</script>"), tokenAdmin)
                .andExpect(status().isCreated());
        editarCelda(celda.getId(), cuerpoCelda("B-20", 3, "CARRO", "editada"), tokenAdmin)
                .andExpect(status().isOk());
        cambiarEstadoCelda(celda.getId(), "MANTENIMIENTO", tokenAdmin).andExpect(status().isOk());
        cambiarEstadoCelda(celda.getId(), "DISPONIBLE", tokenAdmin).andExpect(status().isOk());
        cambiarActivoCelda(celda.getId(), false, tokenAdmin).andExpect(status().isOk());
        cambiarActivoCelda(celda.getId(), true, tokenAdmin).andExpect(status().isOk());

        List<String> operaciones = new ArrayList<>();
        for (AuditoriaLog log : auditoriaLogRepository.findAll()) {
            if (!"Celda".equals(log.getEntidadAfectada())) {
                continue;
            }
            operaciones.add(log.getOperacion());
            assertNotNull(log.getUsuario());
            assertNotNull(log.getFechaHora());
            assertFalse(log.getDetalle().contains("<script>"),
                    "el detalle no debe copiar el cuerpo de la solicitud");
        }

        assertTrue(operaciones.contains("CELDA_CREAR"), operaciones.toString());
        assertTrue(operaciones.contains("CELDA_EDITAR"), operaciones.toString());
        assertTrue(operaciones.contains("CELDA_ESTADO"), operaciones.toString());
        assertTrue(operaciones.contains("CELDA_DESACTIVAR"), operaciones.toString());
        assertTrue(operaciones.contains("CELDA_ACTIVAR"), operaciones.toString());
    }

    @Test
    void case20b_lasOperacionesFallidasDeCeldaNoDejanAuditoria() throws Exception {
        Celda celda = carroLibre("B-22");
        cambiarActivoCelda(celda.getId(), false, tokenAdmin).andExpect(status().isOk());
        long trasLaUnicaOperacionValida = auditoriaLogRepository.count();

        // 404 sobre una celda inexistente: el registro no existe, no hay nada que auditar.
        cambiarActivoCelda(999999L, false, tokenAdmin).andExpect(status().isNotFound());
        assertEquals(trasLaUnicaOperacionValida, auditoriaLogRepository.count());

        // 409 por regla de negocio: la transacción revierte y el evento tampoco queda escrito.
        cambiarEstadoCelda(celda.getId(), "MANTENIMIENTO", tokenAdmin).andExpect(status().isConflict());
        crearCelda(cuerpoCelda("B-22", 1, "CARRO", "código repetido"), tokenAdmin)
                .andExpect(status().isConflict());
        assertEquals(trasLaUnicaOperacionValida, auditoriaLogRepository.count());

        // La salida rechazada por conflicto de celda compartida tampoco agrega eventos (caso 18).
        Celda compartida = carroLibre("B-23");
        registroActivo(vehiculo("DEF923", TipoVehiculo.CARRO), compartida);
        registroActivo(vehiculo("XYZ923", TipoVehiculo.CARRO), recargar(compartida.getId()));
        salir("XYZ923", null, tokenOperador).andExpect(status().isConflict());
        assertEquals(trasLaUnicaOperacionValida, auditoriaLogRepository.count());
    }

    // ------------------------------------------------------------------ 21: regresión

    @Test
    void case21_regresionDeClientesYDeCuentasDelPersonalConCeldasActivas() throws Exception {
        carroLibre("B-30");

        long primerCliente = crearCliente("100000001", "Ana Pérez", tokenOperador);
        long segundoCliente = crearCliente("100000003", "Cira Linares", tokenAdmin);

        entrar("DEF930", TipoVehiculo.CARRO, tokenOperador);

        // Contrato heredado de B2: la primera vinculación está permitida aunque el vehículo esté adentro.
        mockMvc.perform(put("/api/v1/vehiculos/DEF930/cliente")
                        .header("Authorization", "Bearer " + tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"clienteId\":\"" + primerCliente + "\"}").getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        // Cambiar un titular ya vinculado: el operador no puede (403) y el administrador tampoco con ingreso
        // activo (409). Ninguno de los dos deja el vehículo con otro dueño.
        mockMvc.perform(put("/api/v1/vehiculos/DEF930/cliente")
                        .header("Authorization", "Bearer " + tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"clienteId\":\"" + segundoCliente + "\"}").getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/vehiculos/DEF930/cliente")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"clienteId\":\"" + segundoCliente + "\"}").getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v1/clientes/" + primerCliente + "/activo")
                        .header("Authorization", "Bearer " + tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/clientes/" + primerCliente + "/activo")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isForbidden());

        assertEquals(EstadoCelda.OCUPADA, recargar(idDeCelda("B-30")).getEstado());
        assertEquals(1L, registroRepository.countByEstado(EstadoRegistro.ACTIVO));
    }

    private long crearCliente(String documento, String nombre, String token) throws Exception {
        MvcResult respuesta = mockMvc.perform(post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"tipoDocumento\":\"CC\",\"documento\":\"" + documento + "\","
                                + "\"nombreCompleto\":\"" + nombre + "\",\"telefono\":\"3001112233\"}")
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(respuesta.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("id").asLong();
    }

    @Test
    void case21b_salidaDeVehiculoDeClienteInactivoSiguePermitidaYPuedoVolverseaOcuparLaCelda()
            throws Exception {
        Celda celda = carroLibre("B-31");
        long clienteId = crearCliente("100000002", "Beto Ruiz", tokenOperador);

        entrar("DEF931", TipoVehiculo.CARRO, tokenOperador);
        mockMvc.perform(put("/api/v1/vehiculos/DEF931/cliente")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"clienteId\":\"" + clienteId + "\"}").getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        cambiarActivoCeldaCliente(clienteId);

        // El cliente inactivo bloquea un nuevo ingreso, pero nunca deja un vehículo adentro sin salida.
        salir("DEF931", null, tokenOperador).andExpect(status().isOk());
        assertEquals(EstadoCelda.DISPONIBLE, recargar(celda.getId()).getEstado());
        assertEquals(0L, registroRepository.countByEstado(EstadoRegistro.ACTIVO));

        entrar("DEF932", TipoVehiculo.CARRO, tokenOperador);
        mockMvc.perform(post("/api/v1/registros/entrada")
                        .header("Authorization", "Bearer " + tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entradaJson("DEF931", TipoVehiculo.CARRO).getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isConflict());
        assertEquals(1L, registroRepository.countByEstado(EstadoRegistro.ACTIVO));
    }

    private void cambiarActivoCeldaCliente(long clienteId) throws Exception {
        mockMvc.perform(patch("/api/v1/clientes/" + clienteId + "/activo")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ dashboard

    @Test
    void elDashboardDistingueTotalActivasDisponiblesOcupadasYMantenimiento() throws Exception {
        Celda ocupada = carroLibre("D-01");
        Celda libre = carroLibre("D-02");
        nuevaCelda("D-03", TipoVehiculo.CARRO, EstadoCelda.MANTENIMIENTO, true);
        nuevaCelda("D-04", TipoVehiculo.CARRO, EstadoCelda.DISPONIBLE, false);

        entrar("DEF001", TipoVehiculo.CARRO, tokenOperador);

        mockMvc.perform(get("/api/v1/reportes/dashboard").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.celdasTotal").value(4))
                .andExpect(jsonPath("$.celdasActivas").value(3))
                .andExpect(jsonPath("$.celdasDisponibles").value(1))
                .andExpect(jsonPath("$.celdasOcupadas").value(1))
                .andExpect(jsonPath("$.celdasMantenimiento").value(1))
                .andExpect(jsonPath("$.porcentajeOcupacion").value(25.0));

        assertNotNull(libre.getId());
        assertNotNull(ocupada.getId());
    }

    // ------------------------------------------------------------------ concurrencia

    @Test
    void dosIngresosConcurrentesPorUnaUnicaCeldaDejanUnSoloRegistroActivo() throws Exception {
        Celda unica = carroLibre("E-01");
        assertEquals(1L, celdaRepository.contarAsignables());

        List<Throwable> errores = new ArrayList<>();
        List<String> placasExitosas = new ArrayList<>();
        ExecutorService hilo = Executors.newFixedThreadPool(2);
        CountDownLatch lista = new CountDownLatch(1);
        CountDownLatch terminados = new CountDownLatch(2);

        try {
            for (String placa : List.of("EFE001", "EFE002")) {
                hilo.submit(() -> {
                    try {
                        lista.await();
                        var entrada = new com.autoscolombia.presentation.dto.RegistroEntradaDTO();
                        entrada.setPlaca(placa);
                        entrada.setTipo(TipoVehiculo.CARRO);
                        entrada.setMarca("Mazda");
                        entrada.setModelo("3");
                        entrada.setColor("Rojo");
                        Registro registro = registroService.registrarEntrada(entrada, correoOperador);
                        synchronized (placasExitosas) {
                            placasExitosas.add(registro.getVehiculo().getPlaca());
                        }
                    } catch (Throwable falla) {
                        synchronized (errores) {
                            errores.add(falla);
                        }
                    } finally {
                        terminados.countDown();
                    }
                });
            }

            lista.countDown();
            terminados.await(25, TimeUnit.SECONDS);
            hilo.shutdown();
            assertTrue(hilo.awaitTermination(25, TimeUnit.SECONDS), "las hebras no terminaron a tiempo");
        } finally {
            hilo.shutdownNow();
        }

        assertEquals(1, placasExitosas.size(), "una sola entrada debía prosperar: " + placasExitosas);
        assertEquals(1, errores.size(), "la otra debía rechazarse: " + errores);
        assertEquals(1L, registroRepository.countByEstado(EstadoRegistro.ACTIVO));
        assertEquals(EstadoCelda.OCUPADA, recargar(unica.getId()).getEstado());
        assertEquals(0L, celdaRepository.contarAsignables());

        // La respuesta perdedora es controlada y no filtra SQL: ni la sentencia ni el nombre de un índice.
        Throwable perdedora = errores.get(0);
        String mensaje = String.valueOf(perdedora.getMessage()).toLowerCase(java.util.Locale.ROOT);
        assertFalse(mensaje.contains("select"), mensaje);
        assertFalse(mensaje.contains("index"), mensaje);
        assertFalse(mensaje.contains("constraint"), mensaje);
        assertTrue(perdedora instanceof com.autoscolombia.infrastructure.exception.BusinessRuleException
                        || perdedora instanceof org.springframework.dao.PessimisticLockingFailureException,
                "el perdedor debía recibir un conflicto controlado, no un error interno: " + perdedora);

        assertEquals(1L, comprobanteRepository.count());
        // El perdedor tampoco dejó su vehículo escrito: la transacción revirtió entera.
        assertEquals(1L, vehiculoRepository.count());
        assertTrue(registroRepository.listarActivosDuplicadosPorCelda().isEmpty());
        assertTrue(registroRepository.listarActivosDuplicadosPorVehiculo().isEmpty());
    }

    @Test
    void elMismoVehiculoIngresadoALaVezNoQuedaConDosRegistrosActivos() throws Exception {
        carroLibre("E-02");
        carroLibre("E-03");
        // La fila del vehículo tiene que existir para que el bloqueo de la entrada sea la que serialice:
        // una placa nueva todavía no tiene fila que bloquear y ahí la defensa real es el índice de V3.
        vehiculo("FFF001", TipoVehiculo.CARRO);

        List<Throwable> errores = new ArrayList<>();
        List<String> confirmadas = new ArrayList<>();
        ExecutorService hilo = Executors.newFixedThreadPool(2);
        CountDownLatch lista = new CountDownLatch(1);
        CountDownLatch terminados = new CountDownLatch(2);

        try {
            for (int intento = 0; intento < 2; intento++) {
                hilo.submit(() -> {
                    try {
                        lista.await();
                        var entrada = new com.autoscolombia.presentation.dto.RegistroEntradaDTO();
                        entrada.setPlaca("FFF001");
                        entrada.setTipo(TipoVehiculo.CARRO);
                        entrada.setMarca("Mazda");
                        entrada.setModelo("3");
                        entrada.setColor("Rojo");
                        Registro registro = registroService.registrarEntrada(entrada, correoOperador);
                        synchronized (confirmadas) {
                            confirmadas.add(String.valueOf(registro.getId()));
                        }
                    } catch (Throwable falla) {
                        synchronized (errores) {
                            errores.add(falla);
                        }
                    } finally {
                        terminados.countDown();
                    }
                });
            }

            lista.countDown();
            terminados.await(25, TimeUnit.SECONDS);
            hilo.shutdown();
            assertTrue(hilo.awaitTermination(25, TimeUnit.SECONDS), "las hebras no terminaron a tiempo");
        } finally {
            hilo.shutdownNow();
        }

        assertEquals(1, confirmadas.size(), "solo una instancia del vehículo debía quedar adentro");
        assertEquals(1, errores.size());
        assertEquals(1L, registroRepository.countByEstado(EstadoRegistro.ACTIVO));
        assertEquals(1L, registroRepository.count());
        assertTrue(registroRepository.listarActivosDuplicadosPorVehiculo().isEmpty());
        assertEquals(1L, celdaRepository.countByEstado(EstadoCelda.OCUPADA));
        assertEquals(1L, comprobanteRepository.count());
        assertTrue(errores.get(0) instanceof com.autoscolombia.infrastructure.exception.BusinessRuleException,
                "el perdedor debía recibir un conflicto controlado: " + errores.get(0));
    }

    private long idDeCelda(String numero) {
        return celdaRepository.findByNumero(numero).orElseThrow().getId();
    }
}
