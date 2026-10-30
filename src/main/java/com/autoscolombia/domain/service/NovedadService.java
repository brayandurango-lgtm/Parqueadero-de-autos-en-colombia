package com.autoscolombia.domain.service;

import com.autoscolombia.domain.enums.EstadoRegistro;
import com.autoscolombia.domain.model.Novedad;
import com.autoscolombia.domain.model.Registro;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.infrastructure.exception.ResourceNotFoundException;
import com.autoscolombia.infrastructure.repository.NovedadRepository;
import com.autoscolombia.infrastructure.repository.RegistroRepository;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import com.autoscolombia.presentation.dto.NovedadDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio para gestión de novedades.
 */
@Service
@RequiredArgsConstructor
public class NovedadService {

    private final NovedadRepository novedadRepository;
    private final RegistroRepository registroRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    /**
     * Registra una novedad en un registro activo.
     */
    @Transactional
    public Novedad registrar(NovedadDTO dto, String operadorCorreo) {
        Registro registro = registroRepository.findById(dto.getRegistroId())
                .orElseThrow(() -> new ResourceNotFoundException("Registro no encontrado con ID: " + dto.getRegistroId()));

        if (registro.getEstado() != EstadoRegistro.ACTIVO) {
            throw new ResourceNotFoundException("Solo se pueden registrar novedades en registros ACTIVOS");
        }

        Usuario operador = usuarioRepository.findByCorreo(operadorCorreo)
                .orElseThrow(() -> new ResourceNotFoundException("Operador no encontrado"));

        Novedad novedad = Novedad.builder()
                .registro(registro)
                .tipo(dto.getTipo())
                .descripcion(dto.getDescripcion())
                .reportadoPor(operador)
                .evidenciaUrl(dto.getEvidenciaUrl())
                .build();

        novedad = novedadRepository.save(novedad);

        // Registrar en auditoría
        auditoriaService.registrar("NOVEDAD_REGISTRAR", "Novedad", novedad.getId(), null, "EXITO",
                "Novedad tipo " + dto.getTipo() + " en registro " + dto.getRegistroId(), operador);

        return novedad;
    }

    /**
     * Lista novedades por registro.
     */
    public List<NovedadDTO> listarPorRegistro(Long registroId) {
        return novedadRepository.findByRegistroIdOrderByFechaHoraDesc(registroId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lista novedades pendientes (de registros aún activos).
     */
    public List<NovedadDTO> listarPendientes() {
        List<Registro> registrosActivos = registroRepository.findByEstadoOrderByFechaHoraEntradaDesc(EstadoRegistro.ACTIVO);
        return registrosActivos.stream()
                .flatMap(registro -> novedadRepository.findByRegistroIdOrderByFechaHoraDesc(registro.getId()).stream())
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private NovedadDTO toDTO(Novedad novedad) {
        return NovedadDTO.builder()
                .id(novedad.getId())
                .registroId(novedad.getRegistro().getId())
                .tipo(novedad.getTipo())
                .descripcion(novedad.getDescripcion())
                .reportadoPor(novedad.getReportadoPor() != null ?
                        novedad.getReportadoPor().getNombre() + " " + novedad.getReportadoPor().getApellido() : null)
                .evidenciaUrl(novedad.getEvidenciaUrl())
                .build();
    }
}
