package com.autoscolombia.domain.service;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.EstadoRegistro;
import com.autoscolombia.domain.enums.TipoComprobante;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.*;
import com.autoscolombia.infrastructure.exception.BusinessRuleException;
import com.autoscolombia.infrastructure.exception.ResourceNotFoundException;
import com.autoscolombia.infrastructure.repository.*;
import com.autoscolombia.presentation.dto.RegistroDTO;
import com.autoscolombia.presentation.dto.RegistroEntradaDTO;
import com.autoscolombia.presentation.dto.RegistroSalidaDTO;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio para gestión de registros de entrada y salida de vehículos.
 */
@Service
@RequiredArgsConstructor
public class RegistroService {

    private final RegistroRepository registroRepository;
    private final VehiculoRepository vehiculoRepository;
    private final CeldaRepository celdaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ComprobanteRepository comprobanteRepository;
    private final VehiculoService vehiculoService;
    private final CeldaService celdaService;
    private final PicoYPlacaService picoYPlacaService;
    private final AuditoriaService auditoriaService;

    /**
     * Registra la entrada de un vehículo al parqueadero.
     */
    @Transactional
    public Registro registrarEntrada(RegistroEntradaDTO dto, String operadorCorreo) {
        // Validar pico y placa contra el reloj inyectado en el servicio de la regla
        if (!picoYPlacaService.puedeTransitar(dto.getPlaca())) {
            String mensaje = picoYPlacaService.obtenerMensajeRestriccion(dto.getPlaca());
            throw new BusinessRuleException(mensaje);
        }

        // Verificar si el vehículo ya tiene un registro activo
        Optional<Registro> registroActivo = registroRepository.findByVehiculoPlacaAndEstado(dto.getPlaca(), EstadoRegistro.ACTIVO);
        if (registroActivo.isPresent()) {
            throw new BusinessRuleException("El vehículo ya tiene un registro activo en el parqueadero");
        }

        // Buscar o crear vehículo
        Vehiculo vehiculo = vehiculoService.crearOActualizar(
                com.autoscolombia.presentation.dto.VehiculoDTO.builder()
                        .placa(dto.getPlaca())
                        .marca(dto.getMarca())
                        .modelo(dto.getModelo())
                        .color(dto.getColor())
                        .tipo(dto.getTipo())
                        .propietarioDocumento(dto.getPropietarioDocumento())
                        .propietarioNombre(dto.getPropietarioNombre())
                        .propietarioTelefono(dto.getPropietarioTelefono())
                        .build()
        );

        // Asignar celda disponible
        Celda celda = celdaService.asignarCeldaDisponible(dto.getTipo());

        // Obtener usuario operador
        Usuario operador = usuarioRepository.findByCorreo(operadorCorreo)
                .orElseThrow(() -> new ResourceNotFoundException("Operador no encontrado"));

        // Crear registro
        Registro registro = Registro.builder()
                .vehiculo(vehiculo)
                .celda(celda)
                .usuarioEntrada(operador)
                .fechaHoraEntrada(LocalDateTime.now())
                .estado(EstadoRegistro.ACTIVO)
                .build();

        registro = registroRepository.save(registro);

        // Generar comprobante de entrada
        generarComprobante(registro, TipoComprobante.ENTRADA);

        // Registrar en auditoría
        auditoriaService.registrar("REGISTRO_ENTRADA", "Registro", registro.getId(), null, "EXITO",
                "Entrada vehículo placa " + dto.getPlaca() + " en celda " + celda.getNumero(), operador);

        return registro;
    }

    /**
     * Registra la salida de un vehículo del parqueadero.
     */
    @Transactional
    public Registro registrarSalida(String placa, RegistroSalidaDTO dto, String operadorCorreo) {
        // Buscar registro activo
        Registro registro = registroRepository.findByVehiculoPlacaAndEstado(placa, EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new ResourceNotFoundException("No hay registro activo para la placa: " + placa));

        // Obtener usuario operador
        Usuario operador = usuarioRepository.findByCorreo(operadorCorreo)
                .orElseThrow(() -> new ResourceNotFoundException("Operador no encontrado"));

        // Actualizar registro
        registro.setFechaHoraSalida(LocalDateTime.now());
        registro.setEstado(EstadoRegistro.CERRADO);
        registro.setUsuarioSalida(operador);
        if (dto.getObservaciones() != null) {
            registro.setObservaciones(dto.getObservaciones());
        }

        registro = registroRepository.save(registro);

        // Liberar celda
        celdaService.liberarCelda(registro.getCelda().getId());

        // Generar comprobante de salida
        generarComprobante(registro, TipoComprobante.SALIDA);

        // Registrar en auditoría
        auditoriaService.registrar("REGISTRO_SALIDA", "Registro", registro.getId(), null, "EXITO",
                "Salida vehículo placa " + placa + " de celda " + registro.getCelda().getNumero(), operador);

        return registro;
    }

    /**
     * Cancela un registro activo.
     */
    @Transactional
    public Registro cancelarRegistro(Long registroId, String motivo, String operadorCorreo) {
        Registro registro = registroRepository.findById(registroId)
                .orElseThrow(() -> new ResourceNotFoundException("Registro no encontrado con ID: " + registroId));

        if (registro.getEstado() != EstadoRegistro.ACTIVO) {
            throw new BusinessRuleException("Solo se pueden cancelar registros en estado ACTIVO");
        }

        Usuario operador = usuarioRepository.findByCorreo(operadorCorreo)
                .orElseThrow(() -> new ResourceNotFoundException("Operador no encontrado"));

        registro.setEstado(EstadoRegistro.CANCELADO);
        registro.setObservaciones(motivo);
        registro.setFechaHoraSalida(LocalDateTime.now());
        registro.setUsuarioSalida(operador);

        registro = registroRepository.save(registro);

        // Liberar celda
        celdaService.liberarCelda(registro.getCelda().getId());

        // Registrar en auditoría
        auditoriaService.registrar("REGISTRO_CANCELAR", "Registro", registro.getId(), null, "EXITO",
                "Cancelación registro ID " + registroId + ": " + motivo, operador);

        return registro;
    }

    /**
     * Calcula la duración de un registro.
     */
    public Duration calcularDuracion(Registro registro) {
        if (registro.getFechaHoraSalida() == null) {
            return Duration.between(registro.getFechaHoraEntrada(), LocalDateTime.now());
        }
        return Duration.between(registro.getFechaHoraEntrada(), registro.getFechaHoraSalida());
    }

    /**
     * Consulta el registro activo por placa.
     */
    public Optional<Registro> consultarActivoPorPlaca(String placa) {
        return registroRepository.findByVehiculoPlacaAndEstado(placa, EstadoRegistro.ACTIVO);
    }

    /**
     * Lista los últimos ingresos.
     */
    public List<Registro> listarUltimosIngresos(int limite) {
        List<Registro> registros = registroRepository.findByEstadoOrderByFechaHoraEntradaDesc(EstadoRegistro.ACTIVO);
        return registros.stream().limit(limite).toList();
    }

    /**
     * Genera un comprobante con código QR.
     */
    private void generarComprobante(Registro registro, TipoComprobante tipo) {
        try {
            String codigo = generarCodigoUnico();
            String qrData = String.format("Placa: %s, Celda: %s, Fecha: %s, Tipo: %s",
                    registro.getVehiculo().getPlaca(),
                    registro.getCelda().getNumero(),
                    registro.getFechaHoraEntrada(),
                    tipo);

            String qrBase64 = generarQRBase64(qrData);

            Comprobante comprobante = Comprobante.builder()
                    .registro(registro)
                    .codigo(codigo)
                    .tipo(tipo)
                    .qrBase64(qrBase64)
                    .build();

            comprobanteRepository.save(comprobante);
        } catch (Exception e) {
            throw new RuntimeException("Error al generar comprobante", e);
        }
    }

    /**
     * Genera un código único para el comprobante.
     */
    private String generarCodigoUnico() {
        return "CP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Genera un código QR en formato Base64.
     */
    private String generarQRBase64(String data) throws Exception {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, 200, 200);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    /**
     * Convierte un Registro a RegistroDTO.
     */
    public RegistroDTO toDTO(Registro registro) {
        Duration duracion = calcularDuracion(registro);

        return RegistroDTO.builder()
                .id(registro.getId())
                .placa(registro.getVehiculo().getPlaca())
                .marca(registro.getVehiculo().getMarca())
                .modelo(registro.getVehiculo().getModelo())
                .color(registro.getVehiculo().getColor())
                .numeroCelda(registro.getCelda().getNumero())
                .pisoCelda(registro.getCelda().getPiso())
                .operadorEntrada(registro.getUsuarioEntrada() != null ?
                        registro.getUsuarioEntrada().getNombre() + " " + registro.getUsuarioEntrada().getApellido() : null)
                .operadorSalida(registro.getUsuarioSalida() != null ?
                        registro.getUsuarioSalida().getNombre() + " " + registro.getUsuarioSalida().getApellido() : null)
                .fechaHoraEntrada(registro.getFechaHoraEntrada())
                .fechaHoraSalida(registro.getFechaHoraSalida())
                .estado(registro.getEstado())
                .observaciones(registro.getObservaciones())
                .duracionMinutos(duracion.toMinutes())
                .build();
    }
}
