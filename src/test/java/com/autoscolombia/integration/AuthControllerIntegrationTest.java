package com.autoscolombia.integration;

import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import com.autoscolombia.presentation.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private com.autoscolombia.infrastructure.repository.AuditoriaLogRepository auditoriaLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        auditoriaLogRepository.deleteAll();
        usuarioRepository.deleteAll();

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
    }

    @Test
    void testLogin_Exito() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .correo("test@test.com")
                .clave("clave123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.correo").value("test@test.com"))
                .andExpect(jsonPath("$.tipo").value("OPERADOR"));
    }

    @Test
    void testLogin_CredencialesInvalidas() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .correo("test@test.com")
                .clave("claveIncorrecta")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testLogin_UsuarioNoEncontrado() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .correo("noexiste@test.com")
                .clave("clave123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testLoginPublicoSinTokenFunciona() throws Exception {
        LoginRequest request = LoginRequest.builder().correo("test@test.com").clave("clave123").build();
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void testAccesoSinToken_401() throws Exception {
        mockMvc.perform(get("/api/v1/registros/activos"))
                .andExpect(status().isUnauthorized());
    }

        @Test
        void testInterfazWebDisponibleSinToken() throws Exception {
                mockMvc.perform(get("/index.html"))
                                .andExpect(status().isOk())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                                .andExpect(content().string(org.hamcrest.Matchers.containsString("Autos Colombia")));
        }
}
