package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.enums.TipoComprobante;
import com.autoscolombia.domain.model.Celda;
import com.autoscolombia.domain.model.Comprobante;
import com.autoscolombia.domain.model.Registro;
import com.autoscolombia.domain.model.Vehiculo;
import com.autoscolombia.infrastructure.repository.ComprobanteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComprobanteControllerTest {
    @Mock private ComprobanteRepository comprobanteRepository;
    @InjectMocks private ComprobanteController controller;

    private Comprobante comprobante(Long id, Long registroId, String codigo, TipoComprobante tipo, String placa) {
        Registro registro = Registro.builder().id(registroId)
                .vehiculo(Vehiculo.builder().placa(placa).build())
                .celda(Celda.builder().numero("A-01").build()).build();
        return Comprobante.builder().id(id).registro(registro).codigo(codigo).tipo(tipo).qrBase64("base64-qr").build();
    }

    @Test
    void listarComprobantesDelRegistroMapeaCamposYFiltra() {
        Comprobante entrada = comprobante(1L, 8L, "ENTRY-1", TipoComprobante.ENTRADA, "ABC123");
        Comprobante otroRegistro = comprobante(2L, 9L, "ENTRY-2", TipoComprobante.ENTRADA, "XYZ987");
        when(comprobanteRepository.findAll()).thenReturn(List.of(entrada, otroRegistro));

        var response = controller.listarPorRegistro(8L);

        assertEquals(1, response.getBody().size());
        assertEquals("ENTRY-1", response.getBody().get(0).getCodigo());
        assertEquals("ABC123", response.getBody().get(0).getPlaca());
        assertEquals("A-01", response.getBody().get(0).getNumeroCelda());
        assertEquals("base64-qr", response.getBody().get(0).getQrBase64());
    }

    @Test
    void listarSinCoincidenciasDevuelveVacio() {
        when(comprobanteRepository.findAll()).thenReturn(List.of(comprobante(1L, 8L, "ENTRY-1", TipoComprobante.ENTRADA, "ABC123")));
        assertTrue(controller.listarPorRegistro(2L).getBody().isEmpty());
    }

    @Test
    void endpointPdfSimuladoRetornaContenidoQr() {
        Comprobante ticket = comprobante(1L, 8L, "ENTRY-1", TipoComprobante.ENTRADA, "ABC123");
        when(comprobanteRepository.findAll()).thenReturn(List.of(ticket));
        var response = controller.obtenerComprobantePDF("ENTRY-1");
        assertEquals("base64-qr", response.getBody());
    }

    @Test
    void endpointPdfSimuladoCuandoFaltaCodigoLanzaError() {
        when(comprobanteRepository.findAll()).thenReturn(List.of());
        assertThrows(RuntimeException.class, () -> controller.obtenerComprobantePDF("missing"));
    }
}
