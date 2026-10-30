package com.autoscolombia.presentation.dto;

import com.autoscolombia.domain.enums.EstadoRegistro;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para registro de parqueadero.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistroDTO {
    private Long id;
    private String placa;
    private String marca;
    private String modelo;
    private String color;
    private String numeroCelda;
    private Integer pisoCelda;
    private String operadorEntrada;
    private String operadorSalida;
    private LocalDateTime fechaHoraEntrada;
    private LocalDateTime fechaHoraSalida;
    private EstadoRegistro estado;
    private String observaciones;
    private Long duracionMinutos;
}
