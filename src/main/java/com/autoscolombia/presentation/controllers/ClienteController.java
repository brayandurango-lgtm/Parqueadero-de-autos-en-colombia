package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.model.Cliente;
import com.autoscolombia.domain.service.ClienteService;
import com.autoscolombia.domain.service.VehiculoService;
import com.autoscolombia.presentation.dto.CambiarEstadoClienteDTO;
import com.autoscolombia.presentation.dto.ClienteCrearDTO;
import com.autoscolombia.presentation.dto.ClienteDTO;
import com.autoscolombia.presentation.dto.VehiculoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para la gestión de clientes del parqueadero.
 *
 * <p>Registrar, consultar, actualizar y vincular un cliente activo está abierto a ADMINISTRADOR y OPERADOR.
 * Solo ADMINISTRADOR cambia el estado del cliente. La regla de «cambiar el titular de un vehículo ya
 * vinculado» vive en {@link VehiculoService} porque depende del estado guardado del vehículo, no del endpoint.
 */
@RestController
@RequestMapping("/api/v1/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Titulares del servicio de parqueadero (sin acceso al sistema)")
public class ClienteController {

    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final ClienteService clienteService;
    private final VehiculoService vehiculoService;

    @GetMapping
    @Operation(summary = "Listar o buscar clientes",
            description = "Lista con paginación; admite filtro por estado y búsqueda por documento o nombre")
    public ResponseEntity<Page<ClienteDTO>> listar(
            @RequestParam(required = false) String criterio,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), TAMANO_MAXIMO_PAGINA), Sort.by(Sort.Direction.ASC, "id"));
        return ResponseEntity.ok(clienteService.listar(activo, criterio, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar cliente por ID", description = "Retorna el perfil de un cliente")
    public ResponseEntity<ClienteDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(clienteService.aDTO(clienteService.obtenerPorId(id)));
    }

    @GetMapping("/{id}/vehiculos")
    @Operation(summary = "Vehículos del cliente",
            description = "Lista los vehículos cuyo titular actual es este cliente")
    public ResponseEntity<List<VehiculoDTO>> listarVehiculos(@PathVariable Long id) {
        clienteService.obtenerPorId(id);
        return ResponseEntity.ok(vehiculoService.listarVehiculosDeCliente(id));
    }

    @PostMapping
    @Operation(summary = "Crear cliente",
            description = "Registra un cliente; el documento se normaliza y la identidad debe ser única")
    public ResponseEntity<ClienteDTO> crear(@Valid @RequestBody ClienteCrearDTO dto,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        Cliente creado = clienteService.crear(dto, userDetails.getUsername());
        return ResponseEntity.status(201).body(clienteService.aDTO(creado));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar cliente",
            description = "Actualiza los datos de un cliente sin modificar su estado")
    public ResponseEntity<ClienteDTO> actualizar(@PathVariable Long id,
                                                 @Valid @RequestBody ClienteCrearDTO dto,
                                                 @AuthenticationPrincipal UserDetails userDetails) {
        Cliente actualizado = clienteService.actualizar(id, dto, userDetails.getUsername());
        return ResponseEntity.ok(clienteService.aDTO(actualizado));
    }

    @PatchMapping("/{id}/activo")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Activar o desactivar cliente",
            description = "Baja lógica: conserva vehículos e historial y bloquea nuevas vinculaciones e ingresos")
    public ResponseEntity<ClienteDTO> cambiarEstado(@PathVariable Long id,
                                                    @Valid @RequestBody CambiarEstadoClienteDTO dto,
                                                    @AuthenticationPrincipal UserDetails userDetails) {
        Cliente cliente = clienteService.cambiarEstado(id, dto.getActivo(), userDetails.getUsername());
        return ResponseEntity.ok(clienteService.aDTO(cliente));
    }
}
