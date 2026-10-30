package com.autoscolombia.presentation.dto;

import com.autoscolombia.domain.enums.TipoComprobante;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para comprobante.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComprobanteDTO {
    private Long id;
    private String codigo;
    private TipoComprobante tipo;
    private LocalDateTime fechaGeneracion;
    private String qrBase64;
    private String placa;
    private String numeroCelda;
}
