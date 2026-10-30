package com.autoscolombia.presentation.dto;

import com.autoscolombia.domain.enums.TipoVehiculo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para registro de entrada de vehículo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistroEntradaDTO {

    @NotBlank(message = "La placa es obligatoria")
    @Pattern(regexp = "^[A-Z]{3}[0-9]{3}|[A-Z]{2}[0-9]{3}[A-Z]$", message = "La placa debe tener formato válido (ABC123 o AB123C)")
    @Size(max = 10, message = "La placa no puede exceder 10 caracteres")
    private String placa;

    @NotBlank(message = "La marca es obligatoria")
    @Size(max = 50, message = "La marca no puede exceder 50 caracteres")
    private String marca;

    @NotBlank(message = "El modelo es obligatorio")
    @Size(max = 50, message = "El modelo no puede exceder 50 caracteres")
    private String modelo;

    @NotBlank(message = "El color es obligatorio")
    @Size(max = 30, message = "El color no puede exceder 30 caracteres")
    private String color;

    @NotNull(message = "El tipo de vehículo es obligatorio")
    private TipoVehiculo tipo;

    @Size(max = 20, message = "El documento del propietario no puede exceder 20 caracteres")
    private String propietarioDocumento;

    @Size(max = 120, message = "El nombre del propietario no puede exceder 120 caracteres")
    private String propietarioNombre;

    @Size(max = 20, message = "El teléfono del propietario no puede exceder 20 caracteres")
    private String propietarioTelefono;
}
