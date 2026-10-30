package com.autoscolombia.integration;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.EstadoRegistro;
import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.Celda;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.infrastructure.repository.AuditoriaLogRepository;
import com.autoscolombia.infrastructure.repository.CeldaRepository;
import com.autoscolombia.infrastructure.repository.ComprobanteRepository;
import com.autoscolombia.infrastructure.repository.NovedadRepository;
import com.autoscolombia.infrastructure.repository.RegistroRepository;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import com.autoscolombia.infrastructure.repository.VehiculoRepository;
import com.autoscolombia.presentation.dto.LoginRequest;
import com.autoscolombia.presentation.dto.RegistroEntradaDTO;
import com.autoscolombia.presentation.dto.RegistroSalidaDTO;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistroControllerIntegrationTest {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    /** Miércoles: la regla vigente restringe los dígitos 2 y 3, no el 7. */
    private static final LocalDateTime MIERCOLES_PERMITE_DIGITO_7 =
            LocalDateTime.of(2026, Month.NOVEMBER, 4, 10, 0);

    /** Viernes: la regla vigente restringe los dígitos 7 y 8. */
    private static final LocalDateTime VIERNES_RESTRINGE_DIGITO_7 =
            LocalDateTime.of(2026, Month.OCTOBER, 30, 10, 0);

    /**
     * Reloj mutable del contexto de prueba. Se expone como bean {@code @Primary} para
     * sustituir al {@code Clock} de producción sin generar dos candidatos ambiguos.
     */
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
        public RelojDePrueba relojDePrueba() {
            return new RelojDePrueba(MIERCOLES_PERMITE_DIGITO_7, ZONA_BOGOTA);
        }

        @Bean
        @Primary
        public Clock relojPrimarioDePrueba(RelojDePrueba relojDePrueba) {
            return relojDePrueba;
        }
    }

    @Autowired
    private RelojDePrueba reloj;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CeldaRepository celdaRepository;

    @Autowired
    private RegistroRepository registroRepository;

    @Autowired
    private ComprobanteRepository comprobanteRepository;

    @Autowired
    private AuditoriaLogRepository auditoriaLogRepository;

    @Autowired
    private NovedadRepository novedadRepository;

    @Autowired
    private VehiculoRepository vehiculoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private Usuario usuario;

    @BeforeEach
    void setUp() throws Exception {
        reloj.fijar(MIERCOLES_PERMITE_DIGITO_7);

        comprobanteRepository.deleteAll();
        novedadRepository.deleteAll();
        registroRepository.deleteAll();
        auditoriaLogRepository.deleteAll();
        celdaRepository.deleteAll();
        vehiculoRepository.deleteAll();
        usuarioRepository.deleteAll();

        Celda celda = Celda.builder()
                .numero("A-01")
                .piso(1)
                .tipoVehiculo(TipoVehiculo.CARRO)
                .estado(EstadoCelda.DISPONIBLE)
                .build();
        celdaRepository.save(celda);

        usuario = Usuario.builder()
                .nombre("Test")
                .apellido("User")
                .documento("12345678")
                .correo("test@test.com")
                .claveHash(passwordEncoder.encode("clave123"))
                .tipo(TipoUsuario.OPERADOR)
                .activo(true)
                .build();

        usuarioRepository.save(usuario);

        // Login para obtener token
        LoginRequest loginRequest = LoginRequest.builder()
                .correo("test@test.com")
                .clave("clave123")
                .build();

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        token = objectMapper.readTree(response).get("token").asText();
    }

    private RegistroEntradaDTO dtoEntradaPlacaTerminadaEn7() {
        return RegistroEntradaDTO.builder()
                .placa("XYZ987")
                .marca("Mazda")
                .modelo("3")
                .color("Azul")
                .tipo(TipoVehiculo.CARRO)
                .propietarioDocumento("87654321")
                .propietarioNombre("Pedro Gómez")
                .propietarioTelefono("3009876543")
                .build();
    }

    @Test
    void testRegistrarEntrada_Exito() throws Exception {
        reloj.fijar(MIERCOLES_PERMITE_DIGITO_7);

        mockMvc.perform(post("/api/v1/registros/entrada")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoEntradaPlacaTerminadaEn7())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placa").value("XYZ987"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.numeroCelda").value("A-01"));

        assertTrue(registroRepository.findByVehiculoPlacaAndEstado("XYZ987", EstadoRegistro.ACTIVO).isPresent(),
                "La entrada exitosa debe dejar un registro ACTIVO persistido");
        assertEquals(EstadoCelda.OCUPADA, celdaRepository.findAll().get(0).getEstado(),
                "La entrada exitosa debe marcar la celda asignada como OCUPADA");
    }

    @Test
    void testRegistrarEntrada_PicoYPlacaRestringe_NoOcupaCeldaNiCreaRegistro() throws Exception {
        reloj.fijar(VIERNES_RESTRINGE_DIGITO_7);

        mockMvc.perform(post("/api/v1/registros/entrada")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoEntradaPlacaTerminadaEn7())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Pico y Placa")));

        assertTrue(registroRepository.findByVehiculoPlacaAndEstado("XYZ987", EstadoRegistro.ACTIVO).isEmpty(),
                "El rechazo por Pico y Placa no debe crear un registro activo");
        assertEquals(0L, registroRepository.count(),
                "El rechazo por Pico y Placa no debe dejar ningún registro persistido");
        assertEquals(0L, comprobanteRepository.count(),
                "El rechazo por Pico y Placa no debe generar comprobantes");
        assertEquals(EstadoCelda.DISPONIBLE, celdaRepository.findAll().get(0).getEstado(),
                "El rechazo por Pico y Placa no debe ocupar la celda disponible");
    }

    @Test
    void testRegistrarEntrada_SinToken_401() throws Exception {
        RegistroEntradaDTO entradaDTO = RegistroEntradaDTO.builder()
                .placa("XYZ987")
                .marca("Mazda")
                .modelo("3")
                .color("Azul")
                .tipo(TipoVehiculo.CARRO)
                .build();

        mockMvc.perform(post("/api/v1/registros/entrada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entradaDTO)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testListarActivos_Exito() throws Exception {
        mockMvc.perform(get("/api/v1/registros/activos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void testListarUltimosIngresos_Exito() throws Exception {
        mockMvc.perform(get("/api/v1/registros/ultimos?limite=5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
