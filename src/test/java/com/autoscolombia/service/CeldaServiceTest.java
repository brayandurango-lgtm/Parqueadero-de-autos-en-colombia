package com.autoscolombia.service;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.EstadoRegistro;
import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.Celda;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.domain.service.AuditoriaService;
import com.autoscolombia.domain.service.CeldaService;
import com.autoscolombia.infrastructure.exception.BusinessRuleException;
import com.autoscolombia.infrastructure.exception.ResourceNotFoundException;
import com.autoscolombia.infrastructure.repository.CeldaRepository;
import com.autoscolombia.infrastructure.repository.RegistroRepository;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import com.autoscolombia.presentation.dto.CeldaActualizarDTO;
import com.autoscolombia.presentation.dto.CeldaCrearDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Reglas de celdas al nivel del servicio (bloque B3): asignación con bloqueo y revalidación, transiciones
 * manuales, baja lógica, edición según ocupación y su huella en auditoría.
 *
 * <p>Estas pruebas usan dobles del repositorio para observar las decisiones del servicio. No son evidencia de
 * concurrencia real: la serialización entre dos transacciones se comprueba en
 * {@code CeldasIntegridadIntegrationTest} sobre H2, y el índice único parcial queda pendiente de PostgreSQL.
 */
@ExtendWith(MockitoExtension.class)
class CeldaServiceTest {

    private static final String OPERADOR = "admin@autoscolombia.com";

    @Mock private CeldaRepository celdaRepository;
    @Mock private RegistroRepository registroRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AuditoriaService auditoriaService;

    @InjectMocks private CeldaService celdaService;

    private Celda libre;
    private Usuario actor;

    @BeforeEach
    void setUp() {
        libre = celda(1L, "A-01", EstadoCelda.DISPONIBLE, true);
        actor = Usuario.builder().id(7L).correo(OPERADOR).tipo(TipoUsuario.ADMINISTRADOR).build();
    }

    private Celda celda(Long id, String numero, EstadoCelda estado, boolean activo) {
        return Celda.builder().id(id).numero(numero).piso(1).tipoVehiculo(TipoVehiculo.CARRO)
                .estado(estado).activo(activo).build();
    }

    private void actorResuelto() {
        when(usuarioRepository.findByCorreo(OPERADOR)).thenReturn(Optional.of(actor));
    }

    private void guardaComoArgumento() {
        when(celdaRepository.save(any(Celda.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    // ------------------------------------------------------------------ asignación

    @Test
    void asignarCeldaDisponible_bloqueaRevalidayOcupoLaPrimeraAsignable() {
        when(celdaRepository.listarAsignablesPorTipo(TipoVehiculo.CARRO)).thenReturn(List.of(libre));
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(libre));
        when(registroRepository.existsByCeldaIdAndEstado(1L, EstadoRegistro.ACTIVO)).thenReturn(false);
        guardaComoArgumento();

        Celda asignada = celdaService.asignarCeldaDisponible(TipoVehiculo.CARRO);

        assertEquals(EstadoCelda.OCUPADA, asignada.getEstado());
        verify(celdaRepository).buscarConBloqueo(1L);
        verify(celdaRepository, times(1)).save(any(Celda.class));
    }

    @Test
    void asignarCeldaDisponible_sinCandidatosRechazaConMensajeSeguro() {
        when(celdaRepository.listarAsignablesPorTipo(TipoVehiculo.MOTO)).thenReturn(List.of());

        BusinessRuleException excepcion = assertThrows(BusinessRuleException.class,
                () -> celdaService.asignarCeldaDisponible(TipoVehiculo.MOTO));

        assertEquals("No hay celdas disponibles para vehículos tipo MOTO", excepcion.getMessage());
        verify(celdaRepository, never()).save(any(Celda.class));
    }

    @Test
    void asignarCeldaDisponible_siElPrimeroYaFueOcupadoAlBloquearPasaAlSiguiente() {
        Celda segundo = celda(2L, "A-02", EstadoCelda.DISPONIBLE, true);
        when(celdaRepository.listarAsignablesPorTipo(TipoVehiculo.CARRO)).thenReturn(List.of(libre, segundo));

        // El primer candidato llegó OCUPADA al bloquear la fila: otro ingreso ganó la carrera.
        when(celdaRepository.buscarConBloqueo(1L))
                .thenReturn(Optional.of(celda(1L, "A-01", EstadoCelda.OCUPADA, true)));
        when(celdaRepository.buscarConBloqueo(2L)).thenReturn(Optional.of(segundo));
        when(registroRepository.existsByCeldaIdAndEstado(2L, EstadoRegistro.ACTIVO)).thenReturn(false);
        guardaComoArgumento();

        Celda asignada = celdaService.asignarCeldaDisponible(TipoVehiculo.CARRO);

        assertEquals("A-02", asignada.getNumero());
        assertEquals(EstadoCelda.OCUPADA, asignada.getEstado());
    }

    @Test
    void asignarCeldaDisponible_elReintentoEstaAcotadoALosPrimerosCandidatos() {
        List<Celda> candidatos = new ArrayList<>();
        for (int indice = 1; indice <= 8; indice++) {
            candidatos.add(celda((long) indice, "A-0" + indice, EstadoCelda.DISPONIBLE, true));
        }
        when(celdaRepository.listarAsignablesPorTipo(TipoVehiculo.CARRO)).thenReturn(candidatos);

        // Todos los bloqueos devuelven la celda OCUPADA: la carrera la ganó siempre otra operación.
        when(celdaRepository.buscarConBloqueo(any())).thenAnswer(invocacion -> {
            Long id = invocacion.getArgument(0);
            return Optional.of(celda(id, "A-0" + id, EstadoCelda.OCUPADA, true));
        });

        assertThrows(BusinessRuleException.class, () -> celdaService.asignarCeldaDisponible(TipoVehiculo.CARRO));

        // Ocho candidatos en lista, pero como máximo se bloquean los primeros N del límite acotado.
        verify(celdaRepository, times(CeldaService.MAXIMO_CANDIDATOS_POR_ASIGNACION))
                .buscarConBloqueo(any());
        verify(celdaRepository, never()).save(any(Celda.class));
    }

    @Test
    void asignarCeldaDisponible_rechazaCandidatoInactivoAunqueLaConsultaLoDevuelva() {
        Celda inactiva = celda(1L, "A-01", EstadoCelda.DISPONIBLE, false);
        when(celdaRepository.listarAsignablesPorTipo(TipoVehiculo.CARRO)).thenReturn(List.of(inactiva));
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(inactiva));

        assertThrows(BusinessRuleException.class, () -> celdaService.asignarCeldaDisponible(TipoVehiculo.CARRO));

        verify(celdaRepository, never()).save(any(Celda.class));
    }

    // ------------------------------------------------------------------ liberación

    @Test
    void liberarCelda_dejaDisponibleSoloLaCeldaDelRegistroPropio() {
        Celda ocupada = celda(1L, "A-01", EstadoCelda.OCUPADA, true);
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(ocupada));
        when(registroRepository.existsByCeldaIdAndEstado(1L, EstadoRegistro.ACTIVO)).thenReturn(false);

        celdaService.liberarCelda(1L, 55L);

        assertEquals(EstadoCelda.DISPONIBLE, ocupada.getEstado());
        verify(celdaRepository).save(ocupada);
    }

    @Test
    void liberarCelda_siOtroRegistroActivoUsaLaMismaCeldaSeRechazaSinLiberar() {
        Celda ocupada = celda(1L, "A-01", EstadoCelda.OCUPADA, true);
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(ocupada));
        when(registroRepository.existsByCeldaIdAndEstado(1L, EstadoRegistro.ACTIVO)).thenReturn(true);

        BusinessRuleException excepcion = assertThrows(BusinessRuleException.class,
                () -> celdaService.liberarCelda(1L, 55L));

        assertEquals(CeldaService.MENSAJE_CELDA_EN_OTRO_REGISTRO, excepcion.getMessage());
        assertEquals(EstadoCelda.OCUPADA, ocupada.getEstado());
        verify(celdaRepository, never()).save(any(Celda.class));
    }

    @Test
    void liberarCelda_estadoInconsistenteNoSeReparaSilenciosamente() {
        Celda seDeciaLibre = celda(1L, "A-01", EstadoCelda.DISPONIBLE, true);
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(seDeciaLibre));

        BusinessRuleException excepcion = assertThrows(BusinessRuleException.class,
                () -> celdaService.liberarCelda(1L, 55L));

        assertEquals(CeldaService.MENSAJE_INCONSISTENCIA, excepcion.getMessage());
        verify(celdaRepository, never()).save(any(Celda.class));
    }

    @Test
    void liberarCelda_celdaInexistenteDevuelveNotFound() {
        when(celdaRepository.buscarConBloqueo(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> celdaService.liberarCelda(99L, 1L));
    }

    // ------------------------------------------------------------------ alta

    @Test
    void crear_normalizaCodigoYNaceActivaYDisponible() {
        actorResuelto();
        when(celdaRepository.findByNumero("A-09")).thenReturn(Optional.empty());
        when(celdaRepository.save(any(Celda.class))).thenAnswer(invocacion -> {
            Celda nueva = invocacion.getArgument(0);
            nueva.setId(9L);
            return nueva;
        });

        Celda creada = celdaService.crear(CeldaCrearDTO.builder()
                .numero("  a-09  ").piso(1).tipoVehiculo(TipoVehiculo.CARRO)
                .observaciones("  junto a la rampa  ").build(), OPERADOR);

        assertEquals("A-09", creada.getNumero());
        assertEquals(EstadoCelda.DISPONIBLE, creada.getEstado());
        assertTrue(creada.getActivo());
        assertEquals("junto a la rampa", creada.getObservaciones());

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(auditoriaService).registrar(eq("CELDA_CREAR"), eq("Celda"), eq(9L), isNull(), eq("EXITO"),
                detalle.capture(), eq(actor));
        assertEquals("Alta de celda A-09", detalle.getValue());
    }

    @Test
    void crear_rechazaCodigoDuplicadoSinEscribirNiAuditar() {
        when(celdaRepository.findByNumero("A-01")).thenReturn(Optional.of(libre));

        BusinessRuleException excepcion = assertThrows(BusinessRuleException.class,
                () -> celdaService.crear(CeldaCrearDTO.builder()
                        .numero("a-01").piso(1).tipoVehiculo(TipoVehiculo.CARRO).build(), OPERADOR));

        assertEquals(CeldaService.MENSAJE_CODIGO_DUPLICADO, excepcion.getMessage());
        verify(celdaRepository, never()).save(any(Celda.class));
        verifyNoInteractions(auditoriaService);
    }

    // ------------------------------------------------------------------ edición

    @Test
    void actualizar_conCeldaLibreCambiaCodigoPisoTipoYObservaciones() {
        actorResuelto();
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(libre));
        when(celdaRepository.findByNumero("D-77")).thenReturn(Optional.empty());
        guardaComoArgumento();

        Celda guardada = celdaService.actualizar(1L, CeldaActualizarDTO.builder()
                .numero("d-77").piso(4).tipoVehiculo(TipoVehiculo.CAMIONETA)
                .observaciones("recién pintada").build(), OPERADOR);

        assertEquals("D-77", guardada.getNumero());
        assertEquals(4, guardada.getPiso());
        assertEquals(TipoVehiculo.CAMIONETA, guardada.getTipoVehiculo());
        verify(auditoriaService).registrar(eq("CELDA_EDITAR"), eq("Celda"), eq(1L), isNull(), eq("EXITO"),
                anyString(), eq(actor));
    }

    @Test
    void actualizar_conCeldaOcupadaRechazaCambiarCodigoPisoOTipo() {
        Celda ocupada = celda(1L, "A-01", EstadoCelda.OCUPADA, true);
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(ocupada));
        when(registroRepository.existsByCeldaIdAndEstado(1L, EstadoRegistro.ACTIVO)).thenReturn(true);

        BusinessRuleException excepcion = assertThrows(BusinessRuleException.class,
                () -> celdaService.actualizar(1L, CeldaActualizarDTO.builder()
                        .numero("Z-99").piso(1).tipoVehiculo(TipoVehiculo.CARRO)
                        .observaciones("sin cambios").build(), OPERADOR));

        assertEquals(CeldaService.MENSAJE_CELDA_OCUPADA_NO_EDITABLE, excepcion.getMessage());
        verify(celdaRepository, never()).save(any(Celda.class));
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void actualizar_conCeldaOcupadaPermiteEditarLasObservaciones() {
        Celda ocupada = celda(1L, "A-01", EstadoCelda.OCUPADA, true);
        actorResuelto();
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(ocupada));
        when(registroRepository.existsByCeldaIdAndEstado(1L, EstadoRegistro.ACTIVO)).thenReturn(true);
        guardaComoArgumento();

        Celda guardada = celdaService.actualizar(1L, CeldaActualizarDTO.builder()
                .numero("A-01").piso(1).tipoVehiculo(TipoVehiculo.CARRO)
                .observaciones("rayón en el parachoques").build(), OPERADOR);

        assertEquals("rayón en el parachoques", guardada.getObservaciones());
        assertEquals("A-01", guardada.getNumero());
        assertEquals(EstadoCelda.OCUPADA, guardada.getEstado());
    }

    @Test
    void actualizar_rechazaQuedarseConElCodigoDeOtraCelda() {
        Celda otra = celda(2L, "A-02", EstadoCelda.DISPONIBLE, true);
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(libre));
        when(celdaRepository.findByNumero("A-02")).thenReturn(Optional.of(otra));

        BusinessRuleException excepcion = assertThrows(BusinessRuleException.class,
                () -> celdaService.actualizar(1L, CeldaActualizarDTO.builder()
                        .numero("A-02").piso(1).tipoVehiculo(TipoVehiculo.CARRO).build(), OPERADOR));

        assertEquals(CeldaService.MENSAJE_CODIGO_DUPLICADO, excepcion.getMessage());
        verify(celdaRepository, never()).save(any(Celda.class));
    }

    // ------------------------------------------------------------------ transiciones manuales

    @Test
    void cambiarEstado_rechazaOcupadaAntesDeTocarLaBase() {
        BusinessRuleException excepcion = assertThrows(BusinessRuleException.class,
                () -> celdaService.cambiarEstado(1L, EstadoCelda.OCUPADA, OPERADOR));

        assertEquals(CeldaService.MENSAJE_OCUPADA_NO_ASIGNABLE_MANUAL, excepcion.getMessage());
        verifyNoInteractions(celdaRepository);
    }

    @Test
    void cambiarEstado_permitePasarDeDisponibleAMantenimientoYLoAudita() {
        actorResuelto();
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(libre));
        when(registroRepository.existsByCeldaIdAndEstado(1L, EstadoRegistro.ACTIVO)).thenReturn(false);
        guardaComoArgumento();

        Celda guardada = celdaService.cambiarEstado(1L, EstadoCelda.MANTENIMIENTO, OPERADOR);

        assertEquals(EstadoCelda.MANTENIMIENTO, guardada.getEstado());
        verify(auditoriaService).registrar(eq("CELDA_ESTADO"), eq("Celda"), eq(1L), isNull(), eq("EXITO"),
                eq("Inicio de mantenimiento de la celda A-01"), eq(actor));
    }

    @Test
    void cambiarEstado_finalizarMantenimientoDevuelveLaCeldaADisponible() {
        Celda enMantenimiento = celda(1L, "A-01", EstadoCelda.MANTENIMIENTO, true);
        actorResuelto();
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(enMantenimiento));
        when(registroRepository.existsByCeldaIdAndEstado(1L, EstadoRegistro.ACTIVO)).thenReturn(false);
        guardaComoArgumento();

        Celda guardada = celdaService.cambiarEstado(1L, EstadoCelda.DISPONIBLE, OPERADOR);

        assertEquals(EstadoCelda.DISPONIBLE, guardada.getEstado());
        verify(auditoriaService).registrar(eq("CELDA_ESTADO"), eq("Celda"), eq(1L), isNull(), eq("EXITO"),
                eq("Fin de mantenimiento de la celda A-01"), eq(actor));
    }

    @Test
    void cambiarEstado_elMismoEstadoVigenteNoEscribeNiAudita() {
        Celda enMantenimiento = celda(1L, "A-01", EstadoCelda.MANTENIMIENTO, true);
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(enMantenimiento));

        Celda resultado = celdaService.cambiarEstado(1L, EstadoCelda.MANTENIMIENTO, OPERADOR);

        assertSame(enMantenimiento, resultado);
        verify(celdaRepository, never()).save(any(Celda.class));
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void cambiarEstado_rechazaSobreCeldaInactiva() {
        Celda inactiva = celda(1L, "A-01", EstadoCelda.DISPONIBLE, false);
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(inactiva));

        BusinessRuleException excepcion = assertThrows(BusinessRuleException.class,
                () -> celdaService.cambiarEstado(1L, EstadoCelda.MANTENIMIENTO, OPERADOR));

        assertEquals(CeldaService.MENSAJE_CELDA_INACTIVA, excepcion.getMessage());
        verify(celdaRepository, never()).save(any(Celda.class));
    }

    @Test
    void cambiarEstado_rechazaInconsistenciaConRegistroActivoSinRepararla() {
        Celda seDeciaLibre = celda(1L, "A-01", EstadoCelda.DISPONIBLE, true);
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(seDeciaLibre));
        when(registroRepository.existsByCeldaIdAndEstado(1L, EstadoRegistro.ACTIVO)).thenReturn(true);

        BusinessRuleException excepcion = assertThrows(BusinessRuleException.class,
                () -> celdaService.cambiarEstado(1L, EstadoCelda.MANTENIMIENTO, OPERADOR));

        assertEquals(CeldaService.MENSAJE_INCONSISTENCIA, excepcion.getMessage());
        verify(celdaRepository, never()).save(any(Celda.class));
    }

    // ------------------------------------------------------------------ baja lógica

    @Test
    void cambiarActivo_conHistorialCerradoDesactivaYConservaElEstadoOperativo() {
        actorResuelto();
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(libre));
        guardaComoArgumento();

        Celda guardada = celdaService.cambiarActivo(1L, false, OPERADOR);

        assertFalse(guardada.getActivo());
        assertEquals(EstadoCelda.DISPONIBLE, guardada.getEstado());
        verify(auditoriaService).registrar(eq("CELDA_DESACTIVAR"), eq("Celda"), eq(1L), isNull(), eq("EXITO"),
                eq("Desactivación de la celda A-01 (estado operativo DISPONIBLE)"), eq(actor));
    }

    @Test
    void cambiarActivo_rechazaDesactivarConOcupacionActiva() {
        Celda ocupada = celda(1L, "A-01", EstadoCelda.OCUPADA, true);
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(ocupada));
        when(registroRepository.existsByCeldaIdAndEstado(1L, EstadoRegistro.ACTIVO)).thenReturn(true);

        BusinessRuleException excepcion = assertThrows(BusinessRuleException.class,
                () -> celdaService.cambiarActivo(1L, false, OPERADOR));

        assertEquals(CeldaService.MENSAJE_CELDA_OCUPADA_NO_DESACTIVABLE, excepcion.getMessage());
        assertTrue(ocupada.getActivo());
        verify(celdaRepository, never()).save(any(Celda.class));
    }

    @Test
    void cambiarActivo_reactivarNoQuitaElMantenimientoPendiente() {
        Celda inactivaEnMantenimiento = celda(1L, "A-01", EstadoCelda.MANTENIMIENTO, false);
        actorResuelto();
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(inactivaEnMantenimiento));
        when(registroRepository.existsByCeldaIdAndEstado(1L, EstadoRegistro.ACTIVO)).thenReturn(false);
        guardaComoArgumento();

        Celda guardada = celdaService.cambiarActivo(1L, true, OPERADOR);

        assertTrue(guardada.getActivo());
        assertEquals(EstadoCelda.MANTENIMIENTO, guardada.getEstado());
        verify(auditoriaService).registrar(eq("CELDA_ACTIVAR"), eq("Celda"), eq(1L), isNull(), eq("EXITO"),
                eq("Reactivación de la celda A-01 (estado operativo MANTENIMIENTO)"), eq(actor));
    }

    @Test
    void cambiarActivo_alValorYaVigenteNoEscribeNiAudita() {
        when(celdaRepository.buscarConBloqueo(1L)).thenReturn(Optional.of(libre));
        when(registroRepository.existsByCeldaIdAndEstado(1L, EstadoRegistro.ACTIVO)).thenReturn(false);

        Celda resultado = celdaService.cambiarActivo(1L, true, OPERADOR);

        assertSame(libre, resultado);
        verify(celdaRepository, never()).save(any(Celda.class));
        verifyNoInteractions(auditoriaService);
    }

    // ------------------------------------------------------------------ proyección y consulta

    @Test
    void aDTO_derivaDisponibleSoloCuandoLaCeldaEsAsignableDeVerdad() {
        Celda ocupadaPeseAEstadoLibre = celda(3L, "A-03", EstadoCelda.DISPONIBLE, true);
        when(registroRepository.existsByCeldaIdAndEstado(3L, EstadoRegistro.ACTIVO)).thenReturn(true);

        assertFalse(celdaService.aDTO(ocupadaPeseAEstadoLibre).getDisponible());
        assertTrue(celdaService.aDTO(libre).getDisponible());
        assertEquals(Boolean.TRUE, celdaService.aDTO(libre).getActivo());
    }

    @Test
    void obtenerPorId_conCeldaInexistenteDevuelveNotFound() {
        when(celdaRepository.findById(404L)).thenReturn(Optional.empty());

        ResourceNotFoundException excepcion = assertThrows(ResourceNotFoundException.class,
                () -> celdaService.obtenerPorId(404L));

        assertEquals("Celda no encontrada con ID: 404", excepcion.getMessage());
    }

    @Test
    void listar_sinCriterioUsaCadenaVaciaParaEvitarParametroNuloEnPostgres() {
        var pageable = PageRequest.of(0, 20);
        when(celdaRepository.buscar("", null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var resultado = celdaService.listar(null, null, null, null, null, pageable);

        assertTrue(resultado.isEmpty());
        verify(celdaRepository).buscar("", null, null, null, null, pageable);
    }

    @Test
    void listarAsignables_sinTipoUsaLaConsultaGlobal() {
        when(celdaRepository.listarAsignables()).thenReturn(List.of(libre));

        List<Celda> resultado = celdaService.listarAsignables(null);

        assertEquals(1, resultado.size());
        verify(celdaRepository).listarAsignables();
        verify(celdaRepository, never()).listarAsignablesPorTipo(any());
    }
}
