package com.autoscolombia.service;

import com.autoscolombia.domain.enums.TipoDocumento;
import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.model.Cliente;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.domain.service.AuditoriaService;
import com.autoscolombia.domain.service.ClienteService;
import com.autoscolombia.infrastructure.exception.BusinessRuleException;
import com.autoscolombia.infrastructure.repository.ClienteRepository;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import com.autoscolombia.presentation.dto.ClienteCrearDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de identidad y estado del cliente, comprobadas en el servicio (bloque B2).
 */
@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    private static final String OPERADOR = "operador@test.com";

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private ClienteService clienteService;

    private Usuario operador;

    @BeforeEach
    void setUp() {
        operador = Usuario.builder().id(7L).correo(OPERADOR).tipo(TipoUsuario.OPERADOR).activo(true).build();
    }

    private ClienteCrearDTO dto(TipoDocumento tipo, String documento, String nombre, String telefono) {
        return ClienteCrearDTO.builder()
                .tipoDocumento(tipo)
                .documento(documento)
                .nombreCompleto(nombre)
                .telefono(telefono)
                .build();
    }

    private Cliente guardarComo(Cliente cliente) {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findByCorreo(OPERADOR)).thenReturn(Optional.of(operador));
        return cliente;
    }

    // ---------------------------------------------------------------- normalización

    @Test
    void normalizarDocumento_convierteAMayusculasSinPerderCerosIniciales() {
        assertEquals("0012345678", ClienteService.normalizarDocumento("  0012345678  "));
        assertEquals("10000001A", ClienteService.normalizarDocumento("10000001a"));
        assertNull(ClienteService.normalizarDocumento(null));
    }

    /**
     * {@code Locale#ROOT} evita que un idioma local cambie la «i»: en turco {@code "id".toUpperCase()} produce
     * una I con punto y partiría la unicidad del documento según el idioma del servidor.
     */
    @Test
    void normalizarDocumento_noDependeDelIdiomaDelServidor() {
        java.util.Locale anterior = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertEquals("ID", ClienteService.normalizarDocumento("id"));
        } finally {
            java.util.Locale.setDefault(anterior);
        }
    }

    @Test
    void normalizarTexto_recortaSinCambiarElRegistro() {
        assertEquals("+57 300 123 4567", ClienteService.normalizarTexto("  +57 300 123 4567  "));
        assertEquals("María José", ClienteService.normalizarTexto("María José"));
    }

    @Test
    void crear_guardaLosCamposNormalizados() {
        when(clienteRepository.findByTipoDocumentoAndDocumento(TipoDocumento.CC, "0012345678"))
                .thenReturn(Optional.empty());
        guardarComo(Cliente.builder().id(1L).build());

        clienteService.crear(dto(TipoDocumento.CC, "  0012345678  ", "  Ana Pérez  ", " 3001112233 "), OPERADOR);

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepository).save(captor.capture());
        assertEquals("0012345678", captor.getValue().getDocumento());
        assertEquals("Ana Pérez", captor.getValue().getNombreCompleto());
        assertEquals("3001112233", captor.getValue().getTelefono());
        assertTrue(captor.getValue().getActivo());
    }

    // ---------------------------------------------------------------- unicidad

    @Test
    void crear_mismoTipoYMismoDocumento_esRechazadoSinEscribirNiAuditar() {
        when(clienteRepository.findByTipoDocumentoAndDocumento(TipoDocumento.CC, "12345678"))
                .thenReturn(Optional.of(Cliente.builder().id(2L).build()));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> clienteService.crear(dto(TipoDocumento.CC, "12345678", "Ana", "3001112233"), OPERADOR));

        assertEquals(ClienteService.MENSAJE_DOCUMENTO_DUPLICADO, ex.getMessage());
        verify(clienteRepository, never()).save(any(Cliente.class));
        verify(auditoriaService, never()).registrar(anyString(), anyString(), anyLong(), any(), anyString(),
                anyString(), any(Usuario.class));
    }

    @Test
    void crear_elMismoNumeroConOtroTipoDeDocumento_esPermitido() {
        when(clienteRepository.findByTipoDocumentoAndDocumento(TipoDocumento.CE, "12345678"))
                .thenReturn(Optional.empty());
        guardarComo(Cliente.builder().id(3L).build());

        Cliente creado = clienteService.crear(dto(TipoDocumento.CE, "12345678", "Beto", "3001112233"), OPERADOR);

        assertEquals(TipoDocumento.CE, creado.getTipoDocumento());
        verify(clienteRepository).save(any(Cliente.class));
    }

    @Test
    void actualizar_conservarLaPropiaIdentidadEstaPermitido() {
        Cliente propio = Cliente.builder().id(5L).tipoDocumento(TipoDocumento.CC).documento("12345678")
                .nombreCompleto("Ana").telefono("3001112233").activo(true).build();
        when(clienteRepository.findById(5L)).thenReturn(Optional.of(propio));
        when(clienteRepository.findByTipoDocumentoAndDocumento(TipoDocumento.CC, "12345678"))
                .thenReturn(Optional.of(propio));
        guardarComo(propio);

        Cliente resultado = clienteService.actualizar(5L,
                dto(TipoDocumento.CC, "12345678", "Ana Ruiz", "3009998877"), OPERADOR);

        assertEquals("Ana Ruiz", resultado.getNombreCompleto());
        assertEquals("12345678", resultado.getDocumento());
    }

    @Test
    void actualizar_adoptarLaIdentidadDeOtroCliente_esRechazada() {
        Cliente propio = Cliente.builder().id(5L).tipoDocumento(TipoDocumento.CC).documento("111")
                .nombreCompleto("Ana").telefono("3001112233").activo(true).build();
        Cliente otro = Cliente.builder().id(6L).tipoDocumento(TipoDocumento.CC).documento("222").build();
        when(clienteRepository.findById(5L)).thenReturn(Optional.of(propio));
        when(clienteRepository.findByTipoDocumentoAndDocumento(TipoDocumento.CC, "222"))
                .thenReturn(Optional.of(otro));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> clienteService.actualizar(5L, dto(TipoDocumento.CC, "222", "Ana", "3001112233"), OPERADOR));

        assertEquals(ClienteService.MENSAJE_DOCUMENTO_DUPLICADO, ex.getMessage());
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    // ---------------------------------------------------------------- estado

    @Test
    void cambiarEstado_alValorYaVigente_noEscribeNiAudita() {
        Cliente activo = Cliente.builder().id(9L).activo(true).build();
        when(clienteRepository.findById(9L)).thenReturn(Optional.of(activo));

        Cliente resultado = clienteService.cambiarEstado(9L, true, OPERADOR);

        assertTrue(resultado.getActivo());
        verify(clienteRepository, never()).save(any(Cliente.class));
        verify(auditoriaService, never()).registrar(anyString(), anyString(), anyLong(), any(), anyString(),
                anyString(), any(Usuario.class));
    }

    @Test
    void desactivar_esBajaLogicaConservaLosDatosYAuditaSinDocumentosNiTelefonos() {
        Cliente cliente = Cliente.builder().id(9L).documento("12345678").nombreCompleto("Ana Pérez")
                .telefono("3001112233").activo(true).build();
        when(clienteRepository.findById(9L)).thenReturn(Optional.of(cliente));
        guardarComo(cliente);

        clienteService.cambiarEstado(9L, false, OPERADOR);

        assertFalse(cliente.getActivo());
        assertEquals("12345678", cliente.getDocumento());
        assertEquals("3001112233", cliente.getTelefono());
        verify(auditoriaService).registrar(eq("CLIENTE_CAMBIAR_ESTADO"), eq("Cliente"), eq(9L), isNull(),
                eq("EXITO"), eq("Desactivación del cliente id 9"), any(Usuario.class));
    }

    @Test
    void crear_auditaConElActorResueltoYElDetalleNoLlevaDocumentoNiTelefono() {
        when(clienteRepository.findByTipoDocumentoAndDocumento(TipoDocumento.CC, "12345678"))
                .thenReturn(Optional.empty());
        when(usuarioRepository.findByCorreo(OPERADOR)).thenReturn(Optional.of(operador));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente nuevo = inv.getArgument(0);
            nuevo.setId(11L);
            return nuevo;
        });

        clienteService.crear(dto(TipoDocumento.CC, "12345678", "Ana Pérez", "3001112233"), OPERADOR);

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(auditoriaService).registrar(eq("CLIENTE_CREAR"), eq("Cliente"), eq(11L), isNull(),
                eq("EXITO"), detalle.capture(), eq(operador));
        assertEquals("Alta de cliente id 11", detalle.getValue());
        assertFalse(detalle.getValue().contains("12345678"));
        assertFalse(detalle.getValue().contains("3001112233"));
    }

    // ---------------------------------------------------------------- regla de ingreso

    @Test
    void permiteNuevoIngreso_aceptaVehiculosSinClienteYClientesActivos() {
        assertTrue(ClienteService.permiteNuevoIngreso(null));
        assertTrue(ClienteService.permiteNuevoIngreso(Cliente.builder().activo(true).build()));
        assertFalse(ClienteService.permiteNuevoIngreso(Cliente.builder().activo(false).build()));
    }

    @Test
    void cambiarEstado_conClienteInexistenteNoAuditaNada() {
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(com.autoscolombia.infrastructure.exception.ResourceNotFoundException.class,
                () -> clienteService.cambiarEstado(99L, false, OPERADOR));
        verify(auditoriaService, never()).registrar(anyString(), anyString(), anyLong(), any(), anyString(),
                anyString(), any(Usuario.class));
    }
}
