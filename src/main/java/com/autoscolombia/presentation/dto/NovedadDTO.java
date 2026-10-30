package com.autoscolombia.presentation.dto;

import com.autoscolombia.domain.enums.TipoNovedad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para novedad.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovedadDTO {
    private Long id;

    @NotNull(message = "El registro ID es obligatorio")
    private Long registroId;

    @NotNull(message = "El tipo de novedad es obligatorio")
    private TipoNovedad tipo;

    @NotBlank(message = "La descripción es obligatoria")
    private String descripcion;

    private String reportadoPor;
    private String evidenciaUrl;
}
