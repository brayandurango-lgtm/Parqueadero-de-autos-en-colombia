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
 * Alta de celda. El código se normaliza en el servicio, así que aquí solo se declaran los límites, que son
 * los mismos de las columnas de {@code V1} y {@code V3}: {@code numero VARCHAR(10)},
 * {@code tipo_vehiculo VARCHAR(15)}, {@code observaciones VARCHAR(255)}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CeldaCrearDTO {

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
