package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.service.VehiculoService;
import com.autoscolombia.presentation.dto.VehiculoDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehiculoControllerTest {
    @Mock private VehiculoService vehiculoService;
    @InjectMocks private VehiculoController controller;

    @Test
    void buscarDelegaFiltrosYDevuelveLista() {
        List<VehiculoDTO> list = List.of(VehiculoDTO.builder().placa("ABC123").tipo(TipoVehiculo.CARRO).build());
        when(vehiculoService.buscar("ABC", TipoVehiculo.CARRO, null)).thenReturn(list);
        var response = controller.buscar("ABC", TipoVehiculo.CARRO, "ACTIVO");
        assertSame(list, response.getBody());
        verify(vehiculoService).buscar("ABC", TipoVehiculo.CARRO, null);
    }

    @Test
    void obtenerPorPlacaEncuentraYMapeaVehiculo() {
        var vehiculo = com.autoscolombia.domain.model.Vehiculo.builder().placa("ABC123").build();
        var dto = VehiculoDTO.builder().placa("ABC123").build();
        when(vehiculoService.buscarPorPlaca("ABC123")).thenReturn(Optional.of(vehiculo));
        when(vehiculoService.buscar("ABC123", null, null)).thenReturn(List.of(dto));
        var response = controller.obtenerPorPlaca("ABC123");
        assertEquals(200, response.getStatusCode().value());
        assertSame(dto, response.getBody());
    }

    @Test
    void obtenerPorPlacaInexistenteDevuelve404() {
        when(vehiculoService.buscarPorPlaca("none")).thenReturn(Optional.empty());
        assertEquals(404, controller.obtenerPorPlaca("none").getStatusCode().value());
    }

    @Test
    void crearVehiculoDevuelve201ConLaProyeccionDelServicio() {
        var dto = VehiculoDTO.builder().placa("ABC123").build();
        var guardado = com.autoscolombia.domain.model.Vehiculo.builder().placa("ABC123").build();
        var proyeccion = VehiculoDTO.builder().placa("ABC123").clienteId(4L).build();
        when(vehiculoService.crearOActualizar(dto)).thenReturn(guardado);
        when(vehiculoService.toDTO(guardado)).thenReturn(proyeccion);

        var response = controller.crear(dto);

        assertEquals(201, response.getStatusCode().value());
        assertSame(proyeccion, response.getBody());
    }

    @Test
    void actualizarVehiculoRespondeLaProyeccionYNoElCuerpoRecibido() {
        var recibido = VehiculoDTO.builder().placa("XYZ999").propietarioNombre("Texto libre").build();
        var guardado = com.autoscolombia.domain.model.Vehiculo.builder().placa("XYZ999").build();
        var proyeccion = VehiculoDTO.builder().placa("XYZ999").propietarioNombre("Ana Pérez").clienteId(8L).build();
        when(vehiculoService.crearOActualizar(recibido)).thenReturn(guardado);
        when(vehiculoService.toDTO(guardado)).thenReturn(proyeccion);

        var response = controller.actualizar(9L, recibido);

        assertEquals(200, response.getStatusCode().value());
        assertSame(proyeccion, response.getBody());
        assertNotSame(recibido, response.getBody(),
                "La respuesta sale de la proyección: un texto libre contradictorio no se devuelve como titular");
    }
}
