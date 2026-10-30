package com.autoscolombia.service;

import com.autoscolombia.domain.enums.EstadoRegistro;
import com.autoscolombia.domain.enums.TipoNovedad;
import com.autoscolombia.domain.model.Novedad;
import com.autoscolombia.domain.model.Registro;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.domain.service.AuditoriaService;
import com.autoscolombia.domain.service.NovedadService;
import com.autoscolombia.infrastructure.exception.ResourceNotFoundException;
import com.autoscolombia.infrastructure.repository.NovedadRepository;
import com.autoscolombia.infrastructure.repository.RegistroRepository;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import com.autoscolombia.presentation.dto.NovedadDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NovedadServiceTest {
    @Mock private NovedadRepository novedadRepository;
    @Mock private RegistroRepository registroRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AuditoriaService auditoriaService;
    @InjectMocks private NovedadService novedadService;

    private Usuario usuario;
    private Registro activo;
    private NovedadDTO dto;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder().id(2L).nombre("María").apellido("López").correo("op@example.com").build();
        activo = Registro.builder().id(7L).estado(EstadoRegistro.ACTIVO).build();
        dto = NovedadDTO.builder().registroId(7L).tipo(TipoNovedad.RAYON)
                .descripcion("Rayón en puerta").evidenciaUrl("https://example.com/photo.jpg").build();
    }

    @Test
    void registrarGuardaNovedadConOperadorYEvidenciaYAudita() {
        when(registroRepository.findById(7L)).thenReturn(Optional.of(activo));
        when(usuarioRepository.findByCorreo("op@example.com")).thenReturn(Optional.of(usuario));
        when(novedadRepository.save(any(Novedad.class))).thenAnswer(call -> {
            Novedad saved = call.getArgument(0);
            saved.setId(9L);
            return saved;
        });

        Novedad result = novedadService.registrar(dto, "op@example.com");

        assertEquals(9L, result.getId());
        assertEquals(activo, result.getRegistro());
        assertEquals(usuario, result.getReportadoPor());
        assertEquals(TipoNovedad.RAYON, result.getTipo());
        assertEquals(dto.getEvidenciaUrl(), result.getEvidenciaUrl());
        verify(auditoriaService).registrar("NOVEDAD_REGISTRAR", "Novedad", 9L, null, "EXITO",
                "Novedad tipo RAYON en registro 7", usuario);
    }

    @Test
    void registrarRechazaRegistroInexistente() {
        when(registroRepository.findById(7L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> novedadService.registrar(dto, "op@example.com"));
        verifyNoInteractions(usuarioRepository, novedadRepository, auditoriaService);
    }

    @Test
    void registrarRechazaRegistroCerrado() {
        activo.setEstado(EstadoRegistro.CERRADO);
        when(registroRepository.findById(7L)).thenReturn(Optional.of(activo));
        assertThrows(ResourceNotFoundException.class, () -> novedadService.registrar(dto, "op@example.com"));
        verifyNoInteractions(usuarioRepository, novedadRepository, auditoriaService);
    }

    @Test
    void registrarRechazaOperadorInexistente() {
        when(registroRepository.findById(7L)).thenReturn(Optional.of(activo));
        when(usuarioRepository.findByCorreo("op@example.com")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> novedadService.registrar(dto, "op@example.com"));
        verifyNoInteractions(novedadRepository, auditoriaService);
    }

    @Test
    void listarPorRegistroConvierteNovedadADto() {
        Novedad novedad = Novedad.builder().id(9L).registro(activo).tipo(TipoNovedad.RAYON)
                .descripcion("Rayón").reportadoPor(usuario).evidenciaUrl("https://example.com/photo.jpg").build();
        when(novedadRepository.findByRegistroIdOrderByFechaHoraDesc(7L)).thenReturn(List.of(novedad));

        List<NovedadDTO> result = novedadService.listarPorRegistro(7L);

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getRegistroId());
        assertEquals("María López", result.get(0).getReportadoPor());
        assertEquals("https://example.com/photo.jpg", result.get(0).getEvidenciaUrl());
    }

    @Test
    void listarPorRegistroSinResultadosDevuelveListaVacia() {
        when(novedadRepository.findByRegistroIdOrderByFechaHoraDesc(8L)).thenReturn(List.of());
        assertTrue(novedadService.listarPorRegistro(8L).isEmpty());
    }

    @Test
    void listarPendientesCombinaNovedadesDeRegistrosActivos() {
        Registro segundo = Registro.builder().id(8L).estado(EstadoRegistro.ACTIVO).build();
        Novedad n1 = Novedad.builder().id(1L).registro(activo).tipo(TipoNovedad.RAYON).descripcion("Uno").build();
        Novedad n2 = Novedad.builder().id(2L).registro(segundo).tipo(TipoNovedad.OTRO).descripcion("Dos").build();
        when(registroRepository.findByEstadoOrderByFechaHoraEntradaDesc(EstadoRegistro.ACTIVO)).thenReturn(List.of(activo, segundo));
        when(novedadRepository.findByRegistroIdOrderByFechaHoraDesc(7L)).thenReturn(List.of(n1));
        when(novedadRepository.findByRegistroIdOrderByFechaHoraDesc(8L)).thenReturn(List.of(n2));

        List<NovedadDTO> result = novedadService.listarPendientes();

        assertEquals(2, result.size());
        assertEquals(List.of(7L, 8L), result.stream().map(NovedadDTO::getRegistroId).toList());
        verify(novedadRepository).findByRegistroIdOrderByFechaHoraDesc(7L);
        verify(novedadRepository).findByRegistroIdOrderByFechaHoraDesc(8L);
    }

    @Test
    void listarPendientesSinRegistrosActivosNoConsultaNovedades() {
        when(registroRepository.findByEstadoOrderByFechaHoraEntradaDesc(EstadoRegistro.ACTIVO)).thenReturn(List.of());
        assertTrue(novedadService.listarPendientes().isEmpty());
        verifyNoInteractions(novedadRepository);
    }
}
