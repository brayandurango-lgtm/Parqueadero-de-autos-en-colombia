package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.service.UsuarioService;
import com.autoscolombia.infrastructure.exception.GlobalExceptionHandler;
import com.autoscolombia.presentation.dto.UsuarioDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.SQLException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recorre el controlador real y el advice real mediante MockMvc, con el servicio sustituido,
 * para verificar cómo se resuelve cada excepción dentro de la cadena de manejo.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioIntegridadMockMvcTest {

    private static final String DOCUMENTO_EN_JUEGO = "87654321";
    private static final String SQL_DE_REFERENCIA =
            "insert into usuario (documento, nombre, ...) values ('" + DOCUMENTO_EN_JUEGO + "', 'Luis', ...)";

    @Mock
    private UsuarioService usuarioService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UsuarioController(usuarioService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String cuerpoActualizacion() throws Exception {
        UsuarioDTO dto = UsuarioDTO.builder()
                .nombre("Ana").apellido("Pérez").documento(DOCUMENTO_EN_JUEGO)
                .correo("ana@example.com").telefono("3001112233").tipo(TipoUsuario.OPERADOR)
                .build();
        return objectMapper.writeValueAsString(dto);
    }

    @Test
    void restriccionDeDocumentoConocidaDevuelve409SinFiltrarMetadatos() throws Exception {
        when(usuarioService.actualizar(eq(1L), any(UsuarioDTO.class)))
                .thenThrow(integridad("uk_usuario_documento"));

        mockMvc.perform(put("/api/v1/usuarios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoActualizacion()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Ya existe otra cuenta del personal con ese documento"))
                .andExpect(content().string(not(containsString("uk_usuario_documento"))))
                .andExpect(content().string(not(containsString("usuario ("))))
                .andExpect(content().string(not(containsString("insert"))))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString(DOCUMENTO_EN_JUEGO))))
                .andExpect(content().string(not(containsString("ana@example.com"))));
    }

    @Test
    void restriccionDeCorreoConocidaDevuelve409ConMensajePropio() throws Exception {
        when(usuarioService.actualizar(eq(1L), any(UsuarioDTO.class)))
                .thenThrow(integridad("uk_usuario_correo"));

        mockMvc.perform(put("/api/v1/usuarios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoActualizacion()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Ya existe otra cuenta del personal con ese correo"))
                .andExpect(content().string(not(containsString("uk_usuario_correo"))));
    }

    @Test
    void restriccionNoCatalogadaDevuelve409ConMensajeSeguro() throws Exception {
        when(usuarioService.actualizar(eq(1L), any(UsuarioDTO.class)))
                .thenThrow(integridad("fk_registro_vehiculo"));

        mockMvc.perform(put("/api/v1/usuarios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoActualizacion()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("La operación entra en conflicto con los datos existentes o sus relaciones"))
                .andExpect(content().string(not(containsString("fk_registro_vehiculo"))))
                .andExpect(content().string(not(containsString(SQL_DE_REFERENCIA))));
    }

    @Test
    void integridadSinCausaDetalladaDevuelve409YNoFallaelManejador() throws Exception {
        when(usuarioService.actualizar(eq(1L), any(UsuarioDTO.class)))
                .thenThrow(new DataIntegrityViolationException("violación de integridad"));

        mockMvc.perform(put("/api/v1/usuarios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoActualizacion()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("La operación entra en conflicto con los datos existentes o sus relaciones"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void errorDeProgramacionNoSeConvierteEnConflicto() throws Exception {
        when(usuarioService.actualizar(eq(1L), any(UsuarioDTO.class)))
                .thenThrow(new IllegalStateException("detalle interno del estado"));

        mockMvc.perform(put("/api/v1/usuarios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoActualizacion()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Ocurrió un error interno en el servidor"));
    }

    @Test
    void falloDeConexionNoSeConvierteEnConflicto() throws Exception {
        when(usuarioService.actualizar(eq(1L), any(UsuarioDTO.class)))
                .thenThrow(new DataRetrievalFailureException("base indisponible", new SQLException("no route")));

        mockMvc.perform(put("/api/v1/usuarios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoActualizacion()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Ocurrió un error interno en el servidor"));
    }

    @Test
    void recursoInexistenteSigueSiendo404() throws Exception {
        when(usuarioService.actualizar(eq(9L), any(UsuarioDTO.class)))
                .thenThrow(new com.autoscolombia.infrastructure.exception.ResourceNotFoundException("Usuario no encontrado con ID: 9"));

        mockMvc.perform(put("/api/v1/usuarios/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoActualizacion()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void payloadInvalidoSigueSiendo400() throws Exception {
        mockMvc.perform(put("/api/v1/usuarios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"documento\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    /**
     * Conflicto de integridad tal y como lo produce Hibernate: el nombre de la restricción viaja
     * como metadato del {@code ConstraintViolationException}, no dentro del texto que se lee.
     */
    private static DataIntegrityViolationException integridad(String nombreDeRestriccion) {
        SQLException sql = new SQLException(SQL_DE_REFERENCIA, "23505", 0);
        ConstraintViolationException causa =
                new ConstraintViolationException("restricción violada", sql, nombreDeRestriccion);
        return new DataIntegrityViolationException("could not execute statement", causa);
    }
}
