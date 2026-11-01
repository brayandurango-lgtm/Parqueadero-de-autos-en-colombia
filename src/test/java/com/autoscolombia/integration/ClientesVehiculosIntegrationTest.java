package com.autoscolombia.integration;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.EstadoRegistro;
import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.AuditoriaLog;
import com.autoscolombia.domain.model.Celda;
import com.autoscolombia.domain.model.Cliente;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.domain.model.Vehiculo;
import com.autoscolombia.domain.service.ClienteService;
import com.autoscolombia.infrastructure.repository.AuditoriaLogRepository;
import com.autoscolombia.infrastructure.repository.CeldaRepository;
import com.autoscolombia.infrastructure.repository.ClienteRepository;
import com.autoscolombia.infrastructure.repository.ComprobanteRepository;
import com.autoscolombia.infrastructure.repository.NovedadRepository;
import com.autoscolombia.infrastructure.repository.RegistroRepository;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import com.autoscolombia.infrastructure.repository.VehiculoRepository;
import com.autoscolombia.presentation.dto.LoginRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Clientes, relación cliente-vehículo y su efecto sobre la entrada y la salida (bloque B2).
 *
 * <p>Corre sobre H2 en modo PostgreSQL con {@code ddl-auto: create-drop} y Flyway apagado, así que la tabla y
 * sus restricciones se generan desde las entidades y no desde {@code V2__clientes_y_vinculo_vehiculos.sql}.
 * Estas pruebas demuestran las reglas del servicio y de la API; la sintaxis de la migración se comprueba por
 * separado y queda pendiente de ejecutar contra PostgreSQL.
 *
 * <p>Los tokens son JWT reales obtenidos en {@code /api/v1/auth/login}: ninguna prueba sustituye la cadena de
 * seguridad por un usuario simulado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientesVehiculosIntegrationTest {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    /** Sábado: Pico y Placa no aplica y las pruebas de ingreso no dependen del día real del runner. */
    private static final LocalDateTime SABADO_SIN_RESTRICCION =
            LocalDateTime.of(2026, Month.NOVEMBER, 7, 10, 0);

    private static final String CLAVE_ADMIN = "Clave administrativa de prueba I2";
    private static final String CLAVE_OPERADOR = "Clave de operador válida I2";

    private static final String RUTA_CLIENTES = "/api/v1/clientes";
    private static final String RUTA_VEHICULOS = "/api/v1/vehiculos";

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
        public RelojDePrueba relojDePruebaClientes() {
            return new RelojDePrueba(SABADO_SIN_RESTRICCION, ZONA_BOGOTA);
        }

        @Bean
        @Primary
        public Clock relojPrimarioClientes(RelojDePrueba relojDePruebaClientes) {
            return relojDePruebaClientes;
        }
    }

    @Autowired
    private RelojDePrueba reloj;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private VehiculoRepository vehiculoRepository;

    @Autowired
    private CeldaRepository celdaRepository;

    @Autowired
    private RegistroRepository registroRepository;

    @Autowired
    private ComprobanteRepository comprobanteRepository;

    @Autowired
    private NovedadRepository novedadRepository;

    @Autowired
    private AuditoriaLogRepository auditoriaLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private String correoAdmin;
    private String correoOperador;
    private String tokenAdmin;
    private String tokenOperador;

    @BeforeEach
    void setUp() throws Exception {
        reloj.fijar(SABADO_SIN_RESTRICCION);

        // Orden inverso a las claves foráneas: comprobante → registro → vehículo → cliente.
        comprobanteRepository.deleteAll();
        novedadRepository.deleteAll();
        registroRepository.deleteAll();
        vehiculoRepository.deleteAll();
        clienteRepository.deleteAll();
        auditoriaLogRepository.deleteAll();
        celdaRepository.deleteAll();
        usuarioRepository.deleteAll();

        celdaRepository.save(Celda.builder()
                .numero("A-01").piso(1).tipoVehiculo(TipoVehiculo.CARRO)
                .estado(EstadoCelda.DISPONIBLE).build());

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
        return "cuenta" + n + ".b2@test.com";
    }

    private Usuario seed(TipoUsuario tipo, String clave) {
        long n = secuencia();
        return usuarioRepository.save(Usuario.builder()
                .nombre("Personal")
                .apellido("De prueba")
                .documento("92" + String.format("%08d", n))
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

    /** Documento de ocho dígitos único por prueba; el prefijo permite forzar ceros iniciales. */
    private String documento(String prefijo) {
        return prefijo + String.format("%06d", secuencia() % 1_000_000);
    }

    private String jsonTexto(String valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String cuerpoCliente(String tipo, String documento, String nombre, String telefono) {
        return "{\"tipoDocumento\":\"" + tipo + "\",\"documento\":" + jsonTexto(documento)
                + ",\"nombreCompleto\":" + jsonTexto(nombre) + ",\"telefono\":" + jsonTexto(telefono) + "}";
    }

    private ResultActions crearCliente(String cuerpoJson, String token) throws Exception {
        return mockMvc.perform(post(RUTA_CLIENTES)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoJson.getBytes(StandardCharsets.UTF_8)));
    }

    /** Crea por API y devuelve el ID respondido: la prueba depende del alta real, no de un objeto simulado. */
    private long crearClienteConId(String tipo, String documento, String nombre, String telefono, String token)
            throws Exception {
        MvcResult resultado = crearCliente(cuerpoCliente(tipo, documento, nombre, telefono), token)
                .andExpect(status().isCreated())
                .andReturn();
        return cuerpo(resultado).get("id").asLong();
    }

    private ResultActions actualizarCliente(long id, String cuerpoJson, String token) throws Exception {
        return mockMvc.perform(put(RUTA_CLIENTES + "/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoJson.getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions cambiarEstadoCliente(long id, boolean activo, String token) throws Exception {
        return mockMvc.perform(patch(RUTA_CLIENTES + "/" + id + "/activo")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"activo\":" + activo + "}").getBytes(StandardCharsets.UTF_8)));
    }

    private String placaUnica() {
        long n = secuencia();
        return "K" + (char) ('A' + (int) (n % 26)) + "S" + String.format("%02d", (n / 100) % 100) + (n % 10);
    }

    private ResultActions crearVehiculo(String placa, String nombrePropietario, String token) throws Exception {
        String cuerpo = "{\"placa\":\"" + placa + "\",\"marca\":\"Mazda\",\"modelo\":\"3\",\"color\":\"Azul\","
                + "\"tipo\":\"CARRO\",\"propietarioDocumento\":\"87654321\",\"propietarioNombre\":"
                + jsonTexto(nombrePropietario) + ",\"propietarioTelefono\":\"3009876543\"}";
        return mockMvc.perform(post(RUTA_VEHICULOS)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo.getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions vincular(String placa, long clienteId, String token) throws Exception {
        return mockMvc.perform(put(RUTA_VEHICULOS + "/" + placa + "/cliente")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"clienteId\":" + clienteId + "}").getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions entrada(String placa, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/registros/entrada")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"placa\":\"" + placa + "\",\"marca\":\"Mazda\",\"modelo\":\"3\","
                        + "\"color\":\"Azul\",\"tipo\":\"CARRO\"}").getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions salida(String placa, String token) throws Exception {
        return mockMvc.perform(put("/api/v1/registros/salida/" + placa)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"observaciones\":\"Salida de prueba\"}".getBytes(StandardCharsets.UTF_8)));
    }

    private Cliente porId(long id) {
        return clienteRepository.findById(id).orElseThrow();
    }

    private Vehiculo vehiculo(String placa) {
        return vehiculoRepository.findByPlaca(placa).orElseThrow();
    }

    private JsonNode cuerpo(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private List<AuditoriaLog> auditoriaDe(String operacion) {
        // La relación usuario del log es LAZY: se trae con `join fetch` dentro de esta lectura para poder
        // comprobar el actor fuera de la transacción de la petición.
        return entityManager.createQuery(
                        "select a from AuditoriaLog a join fetch a.usuario where a.operacion = :operacion order by a.id",
                        AuditoriaLog.class)
                .setParameter("operacion", operacion)
                .getResultList();
    }

    // ------------------------------------------------------------------ 1. alta válida

    @Test
    void case01_crearClienteValido_devuelve201ConActivoVerdaderoYFechaDeRegistro() throws Exception {
        String documento = documento("10");

        crearCliente(cuerpoCliente("CC", documento, "Ana Pérez", "3001112233"), tokenOperador)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.tipoDocumento").value("CC"))
                .andExpect(jsonPath("$.documento").value(documento))
                .andExpect(jsonPath("$.nombreCompleto").value("Ana Pérez"))
                .andExpect(jsonPath("$.telefono").value("3001112233"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.fechaRegistro").isString());
    }

    // ------------------------------------------------------------------ 2. obligatorios y límites

    @Test
    void case02_camposObligatoriosYLongoitudes_devuelven400SinPersistirNada() throws Exception {
        long antes = clienteRepository.count();

        crearCliente("{\"tipoDocumento\":\"CC\",\"documento\":\"\",\"nombreCompleto\":\"Ana\","
                + "\"telefono\":\"300\"}", tokenOperador).andExpect(status().isBadRequest());
        crearCliente("{\"documento\":\"123456\",\"nombreCompleto\":\"Ana\",\"telefono\":\"300\"}", tokenOperador)
                .andExpect(status().isBadRequest());
        crearCliente(cuerpoCliente("CC", documento("11"), "   ", "3001112233"), tokenOperador)
                .andExpect(status().isBadRequest());
        crearCliente(cuerpoCliente("CC", documento("12"), "Ana", "   "), tokenOperador)
                .andExpect(status().isBadRequest());
        crearCliente(cuerpoCliente("CC", "1".repeat(21), "Ana", "3001112233"), tokenOperador)
                .andExpect(status().isBadRequest());
        crearCliente(cuerpoCliente("CC", "123456", "A".repeat(121), "3001112233"), tokenOperador)
                .andExpect(status().isBadRequest());
        crearCliente(cuerpoCliente("CC", "123456", "Ana", "3".repeat(21)), tokenOperador)
                .andExpect(status().isBadRequest());
        crearCliente(cuerpoCliente("NUMERO_DE_DOCUMENTO", "123456", "Ana", "3001112233"), tokenOperador)
                .andExpect(status().isBadRequest());

        assertEquals(antes, clienteRepository.count(), "Ninguna entrada inválida deja cliente persistido");
    }

    // ------------------------------------------------------------------ 3 y 4. unicidad compuesta

    @Test
    void case03_mismoTipoYDocumento_devuelve409ConMensajeUtilYSinFiltrarSQL() throws Exception {
        String documento = documento("13");
        crearCliente(cuerpoCliente("CC", documento, "Ana", "3001112233"), tokenOperador)
                .andExpect(status().isCreated());

        long antes = clienteRepository.count();
        String respuesta = crearCliente(cuerpoCliente("CC", documento, "Otra persona", "3009998877"), tokenOperador)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(ClienteService.MENSAJE_DOCUMENTO_DUPLICADO))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertFalse(respuesta.contains("uk_cliente_tipo_documento"),
                "El nombre de la restricción no debe aparecer en la respuesta");
        assertFalse(respuesta.toLowerCase().contains("constraint"),
                "La respuesta no filtra detalles internos de la base");
        assertEquals(antes, clienteRepository.count());
    }

    @Test
    void case04_elMismoNumeroConDistintoTipoDeDocumento_esUnClienteDiferente() throws Exception {
        String documento = documento("14");

        crearCliente(cuerpoCliente("CC", documento, "Ana", "3001112233"), tokenOperador)
                .andExpect(status().isCreated());
        crearCliente(cuerpoCliente("CE", documento, "Beto", "3001112233"), tokenOperador)
                .andExpect(status().isCreated());
        crearCliente(cuerpoCliente("PASAPORTE", documento, "Carla", "3001112233"), tokenOperador)
                .andExpect(status().isCreated());

        assertEquals(3, clienteRepository.count());
    }

    // ------------------------------------------------------------------ 5. normalización

    @Test
    void case05_cerosInicialesSeConservanYLaNormalizacionNoCreaUnDuplicadoVisible() throws Exception {
        long id = crearClienteConId("CC", "  00123456ab  ", "  Ana Pérez  ", "  3001112233  ", tokenOperador);

        Cliente guardado = porId(id);
        assertEquals("00123456AB", guardado.getDocumento(),
                "El documento sigue siendo texto: los ceros iniciales no se pierden");
        assertEquals("Ana Pérez", guardado.getNombreCompleto());
        assertEquals("3001112233", guardado.getTelefono());

        crearCliente(cuerpoCliente("CC", "00123456ab", "Ana", "3001112233"), tokenOperador)
                .andExpect(status().isConflict());
        mockMvc.perform(get(RUTA_CLIENTES + "/" + id).header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documento").value("00123456AB"));
    }

    // ------------------------------------------------------------------ 6 y 7. edición

    @Test
    void case06_actualizarConservandoLaPropiaIdentidadCambiaLosDemasDatos() throws Exception {
        String documento = documento("15");
        long id = crearClienteConId("CC", documento, "Ana Pérez", "3001112233", tokenOperador);

        actualizarCliente(id, cuerpoCliente("CC", documento, "Ana Pérez de Ruiz", "3009998877"), tokenOperador)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCompleto").value("Ana Pérez de Ruiz"))
                .andExpect(jsonPath("$.telefono").value("3009998877"))
                .andExpect(jsonPath("$.documento").value(documento));

        assertEquals(1, clienteRepository.count());
        assertTrue(porId(id).getActivo(), "Actualizar los datos no cambia el estado");
    }

    @Test
    void case07_actualizarAdoptandoLaIdentidadDeOtroCliente_es409YNoDejaCambios() throws Exception {
        String documentoA = documento("16");
        String documentoB = documento("17");
        long a = crearClienteConId("CC", documentoA, "Ana", "3001112233", tokenOperador);
        crearClienteConId("CC", documentoB, "Beto", "3002223344", tokenOperador);

        actualizarCliente(a, cuerpoCliente("CC", documentoB, "Ana cambiada", "3001112233"), tokenOperador)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ClienteService.MENSAJE_DOCUMENTO_DUPLICADO));

        Cliente sinCambiar = porId(a);
        assertEquals(documentoA, sinCambiar.getDocumento());
        assertEquals("Ana", sinCambiar.getNombreCompleto());
        assertEquals(2, clienteRepository.count());
    }

    // ------------------------------------------------------------------ 8, 9 y permisos de acceso

    @Test
    void case08_operadorPuedeCrearConsultarBuscarActualizarYVincular() throws Exception {
        String documento = documento("18");
        long id = crearClienteConId("CC", documento, "Ana", "3001112233", tokenOperador);

        mockMvc.perform(get(RUTA_CLIENTES + "/" + id).header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isOk());
        mockMvc.perform(get(RUTA_CLIENTES).header("Authorization", "Bearer " + tokenOperador)
                        .param("criterio", documento))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].documento").value(documento))
                .andExpect(jsonPath("$.totalElements").value(1));

        actualizarCliente(id, cuerpoCliente("CC", documento, "Ana Ruiz", "3001112233"), tokenOperador)
                .andExpect(status().isOk());

        String placa = placaUnica();
        crearVehiculo(placa, "Ana", tokenOperador).andExpect(status().isCreated());
        vincular(placa, id, tokenOperador)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(id));
    }

    @Test
    void case09_operadorRecibe403AlDesactivarClienteOAlCambiarUnTitularYaVinculado() throws Exception {
        long id = crearClienteConId("CC", documento("19"), "Ana", "3001112233", tokenAdmin);
        long otro = crearClienteConId("CC", documento("20"), "Beto", "3002223344", tokenAdmin);
        String placa = placaUnica();
        crearVehiculo(placa, "Ana", tokenAdmin).andExpect(status().isCreated());
        vincular(placa, id, tokenAdmin).andExpect(status().isOk());

        cambiarEstadoCliente(id, false, tokenOperador).andExpect(status().isForbidden());
        vincular(placa, otro, tokenOperador)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertTrue(porId(id).getActivo(), "El 403 no desactivó al cliente");
        assertEquals(id, vehiculo(placa).getCliente().getId(), "El 403 no cambió la titularidad");
    }

    @Test
    void sinTokenODosTokensInvalidosLasRutasDeClientesQuedanProtegidas() throws Exception {
        mockMvc.perform(get(RUTA_CLIENTES)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(RUTA_CLIENTES).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCliente("CC", "123456", "Ana", "3001112233")
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(RUTA_CLIENTES).header("Authorization", "Bearer no.es.un.token"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ 10. baja lógica

    @Test
    void case10_desactivarConservaElClienteSUSVehiculosYElHistorial() throws Exception {
        long id = crearClienteConId("CC", documento("21"), "Ana", "3001112233", tokenAdmin);
        String placa = placaUnica();
        crearVehiculo(placa, "Ana", tokenAdmin).andExpect(status().isCreated());
        vincular(placa, id, tokenAdmin).andExpect(status().isOk());
        entrada(placa, tokenOperador).andExpect(status().isCreated());
        long registrosAntes = registroRepository.count();
        long vehiculosAntes = vehiculoRepository.count();

        cambiarEstadoCliente(id, false, tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        Cliente inactivo = porId(id);
        assertFalse(inactivo.getActivo());
        assertEquals(documento("21").length(), inactivo.getDocumento().length());
        assertEquals(vehiculosAntes, vehiculoRepository.count(), "La baja lógica no borra vehículos");
        assertEquals(registrosAntes, registroRepository.count(), "La baja lógica no borra historial");
        assertEquals(id, vehiculo(placa).getCliente().getId(), "El vehículo sigue apuntando al cliente inactivo");
        assertTrue(registroRepository.findByVehiculoPlacaAndEstado(placa, EstadoRegistro.ACTIVO).isPresent(),
                "El ingreso activo sigue vigente");
    }

    // ------------------------------------------------------------------ 11 y 12. vinculación

    @Test
    void case11_vincularClienteActivoLoProyectaComoTitularActualDelVehiculo() throws Exception {
        String documento = documento("22");
        long id = crearClienteConId("CC", documento, "Ana Pérez", "3001112233", tokenOperador);
        String placa = placaUnica();
        crearVehiculo(placa, "Nombre histórico", tokenOperador).andExpect(status().isCreated());

        vincular(placa, id, tokenOperador)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(id))
                .andExpect(jsonPath("$.propietarioNombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.propietarioDocumento").value(documento))
                .andExpect(jsonPath("$.propietarioTelefono").value("3001112233"));

        mockMvc.perform(get(RUTA_CLIENTES + "/" + id + "/vehiculos")
                        .header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].placa").value(placa))
                .andExpect(jsonPath("$[0].clienteId").value(id));
    }

    @Test
    void case11_vincularClienteOVehiculoInexistenteDevuelve404SinCambiarNada() throws Exception {
        String placa = placaUnica();
        crearVehiculo(placa, "Nombre histórico", tokenOperador).andExpect(status().isCreated());

        vincular(placa, 987654L, tokenOperador).andExpect(status().isNotFound());
        assertNull(vehiculo(placa).getCliente());

        String otraPlaca = placaUnica();
        long id = crearClienteConId("CC", documento("23"), "Ana", "3001112233", tokenOperador);
        mockMvc.perform(put(RUTA_VEHICULOS + "/" + otraPlaca + "/cliente")
                        .header("Authorization", "Bearer " + tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"clienteId\":" + id + "}").getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isNotFound());
    }

    @Test
    void case12_vincularUnClienteInactivo_es409YLaVinculacionQuedaComoEstaba() throws Exception {
        long id = crearClienteConId("CC", documento("24"), "Ana", "3001112233", tokenAdmin);
        cambiarEstadoCliente(id, false, tokenAdmin).andExpect(status().isOk());

        String placa = placaUnica();
        crearVehiculo(placa, "Nombre histórico", tokenOperador).andExpect(status().isCreated());

        MvcResult rechazado = vincular(placa, id, tokenOperador)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("cliente inactivo")))
                .andReturn();
        assertFalse(cuerpo(rechazado).toString().contains("uk_cliente"),
                "El 409 no filtra el nombre de la restricción");
        assertNull(vehiculo(placa).getCliente(), "Un cliente inactivo no empieza a figurar como titular");
    }

    // ------------------------------------------------------------------ 13. cambio de titular con ingreso activo

    @Test
    void case13_cambiarElTitularDeUnVehiculoConIngresoActivo_es409HastaComoAdministrador() throws Exception {
        long anterior = crearClienteConId("CC", documento("25"), "Ana", "3001112233", tokenAdmin);
        long nuevo = crearClienteConId("CC", documento("26"), "Beto", "3002223344", tokenAdmin);
        String placa = placaUnica();
        crearVehiculo(placa, "Ana", tokenAdmin).andExpect(status().isCreated());
        vincular(placa, anterior, tokenAdmin).andExpect(status().isOk());
        entrada(placa, tokenOperador).andExpect(status().isCreated());

        vincular(placa, nuevo, tokenAdmin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("ingreso activo")));

        assertEquals(anterior, vehiculo(placa).getCliente().getId(),
                "El titular no cambió aunque la petición evaluó al nuevo cliente");
    }

    @Test
    void case13_sinIngresoActivoElAdministradorPuedeCambiarElTitular() throws Exception {
        long anterior = crearClienteConId("CC", documento("27"), "Ana", "3001112233", tokenAdmin);
        long nuevo = crearClienteConId("CC", documento("28"), "Beto", "3002223344", tokenAdmin);
        String placa = placaUnica();
        crearVehiculo(placa, "Ana", tokenAdmin).andExpect(status().isCreated());
        vincular(placa, anterior, tokenAdmin).andExpect(status().isOk());

        vincular(placa, nuevo, tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(nuevo))
                .andExpect(jsonPath("$.propietarioNombre").value("Beto"));

        assertEquals(nuevo, vehiculo(placa).getCliente().getId());
    }

    // ------------------------------------------------------------------ 14. compatibilidad con la Iteración 1

    @Test
    void case14_vehiculoAntiguoSinClienteSigueOperandoYMuestraVinculacionPendiente() throws Exception {
        String placa = placaUnica();
        vehiculoRepository.save(Vehiculo.builder()
                .placa(placa).marca("Mazda").modelo("3").color("Azul").tipo(TipoVehiculo.CARRO)
                .propietarioDocumento("87654321").propietarioNombre("Pedro Gómez").propietarioTelefono("3009876543")
                .build());

        mockMvc.perform(get(RUTA_VEHICULOS + "/" + placa).header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propietarioNombre").value("Pedro Gómez"))
                .andExpect(jsonPath("$.clienteId").value(nullValue()));

        entrada(placa, tokenOperador).andExpect(status().isCreated());
        salida(placa, tokenOperador).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADO"));
    }

    @Test
    void case14_actualizarUnVehiculoVinculado_noCambiaLaTitularidadConTextoLibre() throws Exception {
        long id = crearClienteConId("CC", documento("29"), "Ana Pérez", "3001112233", tokenAdmin);
        String placa = placaUnica();
        crearVehiculo(placa, "Nombre histórico", tokenAdmin).andExpect(status().isCreated());
        vincular(placa, id, tokenAdmin).andExpect(status().isOk());
        long vehiculoId = vehiculo(placa).getId();

        mockMvc.perform(put(RUTA_VEHICULOS + "/" + vehiculoId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"placa\":\"" + placa + "\",\"marca\":\"Honda\",\"modelo\":\"CB1\","
                                + "\"color\":\"Negro\",\"tipo\":\"CARRO\",\"propietarioDocumento\":\"111\","
                                + "\"propietarioNombre\":\"Suplantado\",\"propietarioTelefono\":\"222\"}")
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propietarioNombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.clienteId").value(id));

        Vehiculo persistido = vehiculo(placa);
        assertEquals(id, persistido.getCliente().getId(), "La titularidad sigue siendo el cliente");
        assertEquals("Nombre histórico", persistido.getPropietarioNombre(),
                "El texto libre contradictorio no reescribió la columna de la Iteración 1");
        assertEquals("Honda", persistido.getMarca(), "Los datos propios del vehículo sí se actualizaron");
    }

    // ------------------------------------------------------------------ 15 y 16. salida permitida, ingreso bloqueado

    @Test
    void case15_laSalidaDeUnVehiculoConClienteInactivoEstaPermitida() throws Exception {
        long id = crearClienteConId("CC", documento("30"), "Ana", "3001112233", tokenAdmin);
        String placa = placaUnica();
        crearVehiculo(placa, "Ana", tokenAdmin).andExpect(status().isCreated());
        vincular(placa, id, tokenAdmin).andExpect(status().isOk());
        entrada(placa, tokenOperador).andExpect(status().isCreated());

        cambiarEstadoCliente(id, false, tokenAdmin).andExpect(status().isOk());

        salida(placa, tokenOperador)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADO"));
    }

    @Test
    void case16_unNuevoIngresoDeVehiculoDeClienteInactivo_es409SinRegistroNiCeldaOcupada() throws Exception {
        long id = crearClienteConId("CC", documento("31"), "Ana", "3001112233", tokenAdmin);
        String placa = placaUnica();
        crearVehiculo(placa, "Ana", tokenAdmin).andExpect(status().isCreated());
        vincular(placa, id, tokenAdmin).andExpect(status().isOk());
        cambiarEstadoCliente(id, false, tokenAdmin).andExpect(status().isOk());

        long registrosAntes = registroRepository.count();
        long comprobantesAntes = comprobanteRepository.count();

        entrada(placa, tokenOperador)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ClienteService.MENSAJE_CLIENTE_INACTIVO));

        assertEquals(registrosAntes, registroRepository.count(), "El ingreso rechazado no deja registro");
        assertEquals(comprobantesAntes, comprobanteRepository.count(), "El ingreso rechazado no emite comprobante");
        assertEquals(EstadoCelda.DISPONIBLE, celdaRepository.findAll().get(0).getEstado(),
                "El ingreso rechazado no ocupó la celda");
    }

    // ------------------------------------------------------------------ 17. persistencia de relaciones y respuesta sin datos sensibles

    @Test
    void case17_laRelacionPersisteYLasRespuestasSoloTraenLosCamposDelContrato() throws Exception {
        String documento = documento("32");
        long id = crearClienteConId("CC", documento, "Ana Pérez", "3001112233", tokenOperador);
        String placa = placaUnica();
        crearVehiculo(placa, "Ana", tokenOperador).andExpect(status().isCreated());
        vincular(placa, id, tokenOperador).andExpect(status().isOk());

        String respuesta = mockMvc.perform(get(RUTA_VEHICULOS + "/" + placa)
                        .header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode json = objectMapper.readTree(respuesta);
        assertEquals(id, json.get("clienteId").asLong(), "La relación sobrevive a la relectura desde la base");
        assertEquals(documento, json.get("propietarioDocumento").asText());
        assertFalse(respuesta.contains("hibernate"), "La respuesta no expone clases internas");

        mockMvc.perform(get(RUTA_CLIENTES + "/" + id).header("Authorization", "Bearer " + tokenOperador))
                .andExpect(content().string(notContains("\"clave\"")))
                .andExpect(content().string(containsString(documento)));
    }

    @Test
    void case17_laBaseRechazaBorrarUnClienteConVehiculosVinculados() {
        long id = crearClienteConIdSinHttp(documento("33"), "Ana", "3001112233");
        String placa = placaUnica();
        vehiculoRepository.save(Vehiculo.builder()
                .placa(placa).marca("Mazda").modelo("3").color("Azul").tipo(TipoVehiculo.CARRO)
                .cliente(porId(id)).build());

        assertThrows(DataIntegrityViolationException.class, () -> clienteRepository.deleteById(id),
                "La clave foránea de vehiculo.cliente_id se declaró sin borrado en cascada");
        assertTrue(clienteRepository.findById(id).isPresent(), "El cliente rechazado sigue existido");
        assertEquals(id, vehiculo(placa).getCliente().getId(), "La vinculación no se anuló en cascada");
    }

    @Test
    void case17_actualizarElClienteNoReescribeLosHistoricosDeRegistro() throws Exception {
        long id = crearClienteConId("CC", documento("34"), "Ana Pérez", "3001112233", tokenAdmin);
        String placa = placaUnica();
        crearVehiculo(placa, "Nombre histórico", tokenAdmin).andExpect(status().isCreated());
        vincular(placa, id, tokenAdmin).andExpect(status().isOk());
        entrada(placa, tokenOperador).andExpect(status().isCreated());
        Vehiculo vinculado = vehiculo(placa);
        String columnaHistorica = vinculado.getPropietarioNombre();

        actualizarCliente(id, cuerpoCliente("CC", porId(id).getDocumento(), "Ana Ruiz", "3001112233"), tokenAdmin)
                .andExpect(status().isOk());

        assertEquals("Ana Ruiz", porId(id).getNombreCompleto());
        assertEquals(columnaHistorica, vehiculo(placa).getPropietarioNombre(),
                "Editar al cliente no reescribe las columnas del vehículo");
        assertEquals(1, registroRepository.findByVehiculoPlacaAndEstado(placa, EstadoRegistro.ACTIVO)
                .stream().count(), "El ingreso sigue siendo un solo hecho histórico");
    }

    // ------------------------------------------------------------------ 18. auditoría

    @Test
    void case18_altaActualizacionEstadoYVinculacionQuedanAuditadasConActorYFecha() throws Exception {
        String documento = documento("35");
        String telefono = "3001112233";
        long id = crearClienteConId("CC", documento, "Ana Pérez", telefono, tokenOperador);
        String placa = placaUnica();
        crearVehiculo(placa, "Nombre histórico", tokenOperador).andExpect(status().isCreated());
        vincular(placa, id, tokenOperador).andExpect(status().isOk());
        actualizarCliente(id, cuerpoCliente("CC", documento, "Ana Ruiz", telefono), tokenOperador)
                .andExpect(status().isOk());
        cambiarEstadoCliente(id, false, tokenAdmin).andExpect(status().isOk());

        AuditoriaLog alta = auditoriaDe("CLIENTE_CREAR").get(0);
        assertEquals("Cliente", alta.getEntidadAfectada());
        assertEquals(id, alta.getRegistroId());
        assertEquals("EXITO", alta.getResultado());
        assertEquals(correoOperador, alta.getUsuario().getCorreo(), "La auditoría guarda quién la ejecutó");
        assertNotNull(alta.getFechaHora(), "La auditoría guarda la fecha");
        assertEquals("Alta de cliente id " + id, alta.getDetalle());

        AuditoriaLog edicion = auditoriaDe("CLIENTE_ACTUALIZAR").get(0);
        assertEquals(correoOperador, edicion.getUsuario().getCorreo());
        assertEquals("Actualización de datos del cliente id " + id, edicion.getDetalle());

        AuditoriaLog vinculacion = auditoriaDe("VEHICULO_VINCULAR_CLIENTE").get(0);
        assertEquals("Vehiculo", vinculacion.getEntidadAfectada());
        assertEquals(vehiculo(placa).getId(), vinculacion.getRegistroId());
        assertEquals(correoOperador, vinculacion.getUsuario().getCorreo());

        AuditoriaLog estado = auditoriaDe("CLIENTE_CAMBIAR_ESTADO").get(0);
        assertEquals(correoAdmin, estado.getUsuario().getCorreo(),
                "El cambio de estado lo audita el administrador, no el que creó el cliente");
        assertEquals("Desactivación del cliente id " + id, estado.getDetalle());

        List<AuditoriaLog> logs = auditoriaDe("CLIENTE_CREAR");
        logs.addAll(auditoriaDe("CLIENTE_ACTUALIZAR"));
        logs.addAll(auditoriaDe("CLIENTE_CAMBIAR_ESTADO"));
        logs.addAll(auditoriaDe("VEHICULO_VINCULAR_CLIENTE"));
        assertEquals(4, logs.size(), "Las cuatro operaciones del escenario dejan exactamente un recibo cada una");
        logs.forEach(log -> {
            String detalle = String.valueOf(log.getDetalle());
            assertFalse(detalle.contains(documento), "El detalle no copia el documento del cliente");
            assertFalse(detalle.contains(telefono), "El detalle no copia el teléfono del cliente");
            assertFalse(detalle.contains("Ana"), "El detalle no copia el nombre ni el cuerpo de la solicitud");
        });
    }

    @Test
    void case18_unaOperacionRechazadaNoDejaAuditoriaDeExito() throws Exception {
        long anterior = crearClienteConId("CC", documento("36"), "Ana", "3001112233", tokenAdmin);
        long nuevo = crearClienteConId("CC", documento("37"), "Beto", "3002223344", tokenAdmin);
        String placa = placaUnica();
        crearVehiculo(placa, "Ana", tokenAdmin).andExpect(status().isCreated());
        vincular(placa, anterior, tokenAdmin).andExpect(status().isOk());
        entrada(placa, tokenOperador).andExpect(status().isCreated());

        long cambiosAntes = auditoriaDe("VEHICULO_CAMBIAR_CLIENTE").size();
        vincular(placa, nuevo, tokenAdmin).andExpect(status().isConflict());

        assertEquals(cambiosAntes, auditoriaDe("VEHICULO_CAMBIAR_CLIENTE").size(),
                "La vinculación fallida no escribe auditoría de cambio de titular");
        assertEquals(anterior, vehiculo(placa).getCliente().getId(), "El titular sigue siendo el original");
    }

    @Test
    void case18_repetirLaMismaVinculacionONoCambiarElEstadoNoGeneraAuditoriaNueva() throws Exception {
        long id = crearClienteConId("CC", documento("38"), "Ana", "3001112233", tokenAdmin);
        String placa = placaUnica();
        crearVehiculo(placa, "Ana", tokenAdmin).andExpect(status().isCreated());
        vincular(placa, id, tokenAdmin).andExpect(status().isOk());

        long vinculacionesAntes = auditoriaDe("VEHICULO_VINCULAR_CLIENTE").size();
        vincular(placa, id, tokenAdmin).andExpect(status().isOk());
        assertEquals(vinculacionesAntes, auditoriaDe("VEHICULO_VINCULAR_CLIENTE").size(),
                "Reapuntar al mismo cliente no es un cambio que deba auditarse");

        long estadosAntes = auditoriaDe("CLIENTE_CAMBIAR_ESTADO").size();
        cambiarEstadoCliente(id, true, tokenAdmin).andExpect(status().isOk());
        assertEquals(estadosAntes, auditoriaDe("CLIENTE_CAMBIAR_ESTADO").size(),
                "Confirmar el estado vigente no audita una modificación inexistente");
    }

    // ------------------------------------------------------------------ auxiliares

    private long crearClienteConIdSinHttp(String documento, String nombre, String telefono) {
        return clienteRepository.save(Cliente.builder()
                .tipoDocumento(com.autoscolombia.domain.enums.TipoDocumento.CC)
                .documento(documento)
                .nombreCompleto(nombre)
                .telefono(telefono)
                .activo(true)
                .build()).getId();
    }

    private static org.hamcrest.Matcher<String> notContains(String fragmento) {
        return org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(fragmento));
    }
}
