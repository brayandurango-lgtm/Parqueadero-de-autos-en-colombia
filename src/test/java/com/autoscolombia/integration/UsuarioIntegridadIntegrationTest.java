package com.autoscolombia.integration;

import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.infrastructure.exception.GlobalExceptionHandler;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Violación real de la restricción única de documento dentro del contexto de la aplicación,
 * sobre el H2 del perfil de prueba, para ver cómo llega el conflicto al manejador.
 */
@SpringBootTest
@ActiveProfiles("test")
class UsuarioIntegridadIntegrationTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    private final List<Long> idsCreados = new ArrayList<>();

    @AfterEach
    void limpiar() {
        if (!idsCreados.isEmpty()) {
            usuarioRepository.deleteAllById(idsCreados);
            idsCreados.clear();
        }
    }

    private Usuario nuevo(String documento, String correo) {
        Usuario guardado = usuarioRepository.save(Usuario.builder()
                .nombre("Ana").apellido("Pérez").documento(documento).correo(correo)
                .claveHash(passwordEncoder.encode("clave-de-prueba"))
                .tipo(TipoUsuario.OPERADOR).activo(true)
                .build());
        idsCreados.add(guardado.getId());
        return guardado;
    }

    @Test
    void duplicadoRealDeDocumentoLlegaComoConflictoSeguro() {
        String documento = "9001" + (System.nanoTime() % 100_000_000L);
        nuevo(documento, "una-" + documento + "@example.com");

        DataIntegrityViolationException conflicto = assertThrows(DataIntegrityViolationException.class,
                () -> nuevo(documento, "otra-" + documento + "@example.com"));

        var response = globalExceptionHandler.handleDataIntegrityViolationException(conflicto);
        String mensaje = response.getBody().getMessage();

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Conflict", response.getBody().getError());
        assertNotNull(mensaje);
        assertFalse(mensaje.contains(documento), "el mensaje no debe repetir el documento");
        assertFalse(mensaje.toLowerCase().contains("insert"), "el mensaje no debe contener SQL");
        assertFalse(mensaje.contains("PUBLIC"), "el mensaje no debe contener esquemas internos");
        assertFalse(mensaje.contains("uk_"), "el mensaje no debe contener nombres de restricción");
    }

    /**
     * En H2 el identificador que Hibernate expone es generado por la base de datos
     * ({@code public.CONSTRAINT_INDEX_F ON public.usuario(documento ...)}), no el nombre declarado en
     * {@code V1__schema_inicial.sql}; por eso el conflicto real cae en el mensaje genérico y nunca en el
     * específico. La rama específica solo se comprobó con excepciones construidas en prueba.
     */
    @Test
    void conflictoRealEnH2UsaElMensajeGenericoPorqueElNombreNoEsElDeclaradoEnV1() {
        String documento = "9002" + (System.nanoTime() % 100_000_000L);
        nuevo(documento, "uno-" + documento + "@example.com");

        DataIntegrityViolationException conflicto = assertThrows(DataIntegrityViolationException.class,
                () -> nuevo(documento, "dos-" + documento + "@example.com"));

        boolean hayExcepcionDeHibernate = false;
        for (Throwable causa = conflicto; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException) {
                hayExcepcionDeHibernate = true;
            }
        }
        assertTrue(hayExcepcionDeHibernate, "Hibernate debe estar en la cadena de causas");

        var response = globalExceptionHandler.handleDataIntegrityViolationException(conflicto);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("La operación entra en conflicto con los datos existentes o sus relaciones",
                response.getBody().getMessage());
    }
}
