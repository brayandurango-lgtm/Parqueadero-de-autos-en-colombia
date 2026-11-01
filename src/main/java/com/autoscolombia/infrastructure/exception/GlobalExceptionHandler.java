package com.autoscolombia.infrastructure.exception;

import com.autoscolombia.domain.service.CeldaService;
import com.autoscolombia.domain.service.ClienteService;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Manejador global de excepciones para la API REST.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Mensaje para los conflictos de integridad cuya restricción no se reconoce.
     */
    static final String MENSAJE_CONFLICTO_NO_CATALOGADO =
            "La operación entra en conflicto con los datos existentes o sus relaciones";

    /**
     * Respuesta fija cuando otra operación tiene bloqueada la fila de la celda. Se redacta sin mencionar la
     * sentencia ni el índice involucrado.
     */
    static final String MENSAJE_BLOQUEO_CELDA =
            "Otra operación está usando esa celda en este momento. Vuelve a intentarlo.";

    /**
     * Restricciones de unicidad declaradas en {@code V1__schema_inicial.sql} (cuentas del personal y celdas),
     * en {@code V2__clientes_y_vinculo_vehiculos.sql} (clientes) y en
     * {@code V3__gestion_celdas_e_integridad_ocupacion.sql} (ocupación activa). El nombre que entrega
     * Hibernate se usa solo para una comparación exacta contra estas claves y nunca se copia en la respuesta:
     * en H2 ese campo trae identificadores generados por la base y valores de las columnas.
     */
    private static final Map<String, String> MENSAJES_POR_RESTRICCION = Map.of(
            "uk_usuario_documento", "Ya existe otra cuenta del personal con ese documento",
            "uk_usuario_correo", "Ya existe otra cuenta del personal con ese correo",
            "uk_celda_numero", CeldaService.MENSAJE_CODIGO_DUPLICADO,
            "uk_cliente_tipo_documento", ClienteService.MENSAJE_DOCUMENTO_DUPLICADO);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRuleException(BusinessRuleException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ValidationErrorResponse error = new ValidationErrorResponse();
        error.setTimestamp(LocalDateTime.now());
        error.setStatus(HttpStatus.BAD_REQUEST.value());
        error.setError("Validation Error");
        error.setMessage("Error de validación en los campos enviados");
        error.setErrors(errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationCredentialsNotFoundException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleCustomAuthenticationException(AuthenticationException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message("No tiene permisos para realizar esta operación")
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(mensajeDeConflicto(ex))
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Dos operaciones concurrentes pidieron a la vez la misma fila de celda y esta perdió el bloqueo, o el
     * servidor devolvió un tiempo de espera de candado. La transacción ya revirtió por completo —registro,
     * comprobante y auditoría—, así que la respuesta es un conflicto controlado y no un 500: el mensaje no
     * incluye SQL, nombre de índice ni datos del otro vehículo.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConflictoDeBloqueo(PessimisticLockingFailureException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(MENSAJE_BLOQUEO_CELDA)
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Cuerpo ilegible o ausente: 400 con un mensaje fijo. El mensaje de la excepción original se descarta
     * porque Spring incluye en él un fragmento del payload, que en una cuenta del personal puede contener
     * la contraseña inicial.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("El cuerpo de la solicitud es obligatorio y debe ser JSON válido")
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("Ocurrió un error interno en el servidor")
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Mensaje para el cliente: específico solo si la restricción violada se reconoce por su nombre
     * catalogado; genérico y seguro en cualquier otro caso.
     */
    private static String mensajeDeConflicto(DataIntegrityViolationException ex) {
        String restriccion = restriccionCatalogada(ex);
        return restriccion == null
                ? MENSAJE_CONFLICTO_NO_CATALOGADO
                : MENSAJES_POR_RESTRICCION.get(restriccion);
    }

    private static String restriccionCatalogada(Throwable origen) {
        for (Throwable causa = origen; causa != null; causa = causa.getCause()) {
            String nombre = nombreDeRestriccion(causa);
            if (nombre != null) {
                return nombre;
            }
        }
        return null;
    }

    private static String nombreDeRestriccion(Throwable ex) {
        if (!(ex instanceof ConstraintViolationException excepcionHibernate)) {
            return null;
        }
        String cruda = excepcionHibernate.getConstraintName();
        if (cruda == null) {
            return null;
        }
        String normalizada = cruda.trim().toLowerCase(Locale.ROOT);
        return MENSAJES_POR_RESTRICCION.containsKey(normalizada) ? normalizada : null;
    }
}
