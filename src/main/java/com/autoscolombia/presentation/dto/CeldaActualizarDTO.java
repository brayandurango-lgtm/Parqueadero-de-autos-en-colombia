package com.autoscolombia.presentation.dto;

import com.autoscolombia.domain.enums.TipoVehiculo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Edición administrativa de una celda. Los mismos límites y validaciones del alta, porque el formulario de
 * la pantalla reenvía la ficha completa; lo que cambia es lo que el servicio admite modificar: si la celda
 * está ocupada, código, piso y tipo se rechazan y solo se aceptan observaciones.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CeldaActualizarDTO {

    @NotBlank(message = "El código de la celda es obligatorio")
    @Size(max = 10, message = "El código de la celda no puede exceder 10 caracteres")
    private String numero;

    @NotNull(message = "El piso es obligatorio")
    @Min(value = 1, message = "El piso debe ser un número positivo")
    @Max(value = 20, message = "El piso no puede exceder 20")
    private Integer piso;

    @NotNull(message = "El tipo de vehículo es obligatorio")
    private TipoVehiculo tipoVehiculo;

    @Size(max = 255, message = "Las observaciones no pueden exceder 255 caracteres")
    private String observaciones;
}
