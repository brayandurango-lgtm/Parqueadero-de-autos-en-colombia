package com.autoscolombia.presentation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo de {@code PUT /api/v1/vehiculos/{placa}/cliente}: vincula o cambia el titular de un vehículo.
 *
 * <p>Se identifica al cliente por su {@code id}, no por documento libre, para que la vinculación no pueda
 * reinventar una identidad distinta de la que ya está normalizada y guardada.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VincularClienteDTO {

    @NotNull(message = "El identificador del cliente es obligatorio")
    private Long clienteId;
}
