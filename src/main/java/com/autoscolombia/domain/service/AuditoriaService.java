package com.autoscolombia.domain.service;

import com.autoscolombia.domain.model.AuditoriaLog;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.infrastructure.repository.AuditoriaLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio para gestión de auditoría del sistema.
 */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final AuditoriaLogRepository auditoriaLogRepository;

    /**
     * Registra una operación en el log de auditoría.
     *
     * @param operacion      Nombre de la operación realizada
     * @param entidad        Entidad afectada
     * @param registroId     ID del registro afectado
     * @param ipOrigen       IP de origen de la solicitud
     * @param resultado      Resultado de la operación (EXITO, ERROR, etc.)
     * @param detalle        Detalles adicionales de la operación
     * @param usuario        Usuario que realizó la operación
     */
    @Transactional
    public void registrar(String operacion, String entidad, Long registroId, String ipOrigen,
                         String resultado, String detalle, Usuario usuario) {
        AuditoriaLog log = AuditoriaLog.builder()
                .usuario(usuario)
                .operacion(operacion)
                .entidadAfectada(entidad)
                .registroId(registroId)
                .ipOrigen(ipOrigen)
                .resultado(resultado)
                .detalle(detalle)
                .build();

        auditoriaLogRepository.save(log);
    }

    /**
     * Registra una operación sin usuario específico (para operaciones públicas).
     */
    @Transactional
    public void registrarSinUsuario(String operacion, String entidad, Long registroId, String ipOrigen,
                                     String resultado, String detalle) {
        AuditoriaLog log = AuditoriaLog.builder()
                .operacion(operacion)
                .entidadAfectada(entidad)
                .registroId(registroId)
                .ipOrigen(ipOrigen)
                .resultado(resultado)
                .detalle(detalle)
                .build();

        auditoriaLogRepository.save(log);
    }
}
