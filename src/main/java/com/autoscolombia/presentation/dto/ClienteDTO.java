package com.autoscolombia.presentation.dto;

import com.autoscolombia.domain.enums.TipoDocumento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Respuesta pública de un cliente.
 *
 * <p>Solo se usa para salir: el alta y la edición se reciben en {@link ClienteCrearDTO}, que es el único
 * DTO con los campos sin normalizar tal como llegaron del cliente de la API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteDTO {

    private Long id;

    private TipoDocumento tipoDocumento;

    private String documento;

    private String nombreCompleto;

    private String telefono;

    private Boolean activo;

    private LocalDateTime fechaRegistro;
}
