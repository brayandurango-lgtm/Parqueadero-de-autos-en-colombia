package com.autoscolombia.domain.service;

import com.autoscolombia.domain.enums.EstadoRegistro;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.Vehiculo;
import com.autoscolombia.infrastructure.repository.VehiculoRepository;
import com.autoscolombia.presentation.dto.VehiculoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Servicio para gestión de vehículos.
 */
@Service
@RequiredArgsConstructor
public class VehiculoService {

    private final VehiculoRepository vehiculoRepository;

    /**
     * Crea o actualiza un vehículo.
     */
    @Transactional
    public Vehiculo crearOActualizar(VehiculoDTO vehiculoDTO) {
        Optional<Vehiculo> existente = vehiculoRepository.findByPlaca(vehiculoDTO.getPlaca());

        if (existente.isPresent()) {
            Vehiculo vehiculo = existente.get();
            vehiculo.setMarca(vehiculoDTO.getMarca());
            vehiculo.setModelo(vehiculoDTO.getModelo());
            vehiculo.setColor(vehiculoDTO.getColor());
            vehiculo.setTipo(vehiculoDTO.getTipo());
            vehiculo.setPropietarioDocumento(vehiculoDTO.getPropietarioDocumento());
            vehiculo.setPropietarioNombre(vehiculoDTO.getPropietarioNombre());
            vehiculo.setPropietarioTelefono(vehiculoDTO.getPropietarioTelefono());
            return vehiculoRepository.save(vehiculo);
        } else {
            Vehiculo vehiculo = Vehiculo.builder()
                    .placa(vehiculoDTO.getPlaca())
                    .marca(vehiculoDTO.getMarca())
                    .modelo(vehiculoDTO.getModelo())
                    .color(vehiculoDTO.getColor())
                    .tipo(vehiculoDTO.getTipo())
                    .propietarioDocumento(vehiculoDTO.getPropietarioDocumento())
                    .propietarioNombre(vehiculoDTO.getPropietarioNombre())
                    .propietarioTelefono(vehiculoDTO.getPropietarioTelefono())
                    .build();
            return vehiculoRepository.save(vehiculo);
        }
    }

    /**
     * Busca un vehículo por placa.
     */
    public Optional<Vehiculo> buscarPorPlaca(String placa) {
        return vehiculoRepository.findByPlaca(placa);
    }

    /**
     * Búsqueda flexible de vehículos.
     */
    public List<VehiculoDTO> buscar(String criterio, TipoVehiculo tipo, EstadoRegistro estado) {
        List<Vehiculo> vehiculos;

        if (criterio == null || criterio.trim().isEmpty()) {
            vehiculos = vehiculoRepository.findAll();
        } else {
            vehiculos = vehiculoRepository.findByPlacaContainingIgnoreCaseOrPropietarioNombreContainingIgnoreCaseOrPropietarioDocumentoContaining(
                    criterio, criterio, criterio);
        }

        // Filtrar por tipo si se especifica
        if (tipo != null) {
            vehiculos = vehiculos.stream()
                    .filter(v -> v.getTipo() == tipo)
                    .collect(Collectors.toList());
        }

        // Nota: El filtro por estado no se aplica directamente aquí porque
        // el estado está en la entidad Registro, no en Vehiculo.
        // Este filtro se puede implementar en el servicio de Registro.

        return vehiculos.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private VehiculoDTO toDTO(Vehiculo vehiculo) {
        return VehiculoDTO.builder()
                .id(vehiculo.getId())
                .placa(vehiculo.getPlaca())
                .marca(vehiculo.getMarca())
                .modelo(vehiculo.getModelo())
                .color(vehiculo.getColor())
                .tipo(vehiculo.getTipo())
                .propietarioDocumento(vehiculo.getPropietarioDocumento())
                .propietarioNombre(vehiculo.getPropietarioNombre())
                .propietarioTelefono(vehiculo.getPropietarioTelefono())
                .build();
    }
}
