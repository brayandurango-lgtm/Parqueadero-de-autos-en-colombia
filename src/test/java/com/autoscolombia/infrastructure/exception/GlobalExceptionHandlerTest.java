package com.autoscolombia.infrastructure.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void resourceNotFoundMapsTo404() {
        var response = handler.handleResourceNotFoundException(new ResourceNotFoundException("missing"));
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Not Found", response.getBody().getError());
        assertEquals("missing", response.getBody().getMessage());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    void businessRuleMapsToConflict() {
        var response = handler.handleBusinessRuleException(new BusinessRuleException("duplicate"));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Conflict", response.getBody().getError());
        assertEquals("duplicate", response.getBody().getMessage());
    }

    @Test
    void authenticationFailureMapsTo401() {
        var response = handler.handleAuthenticationException(new AuthenticationCredentialsNotFoundException("no credentials"));
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthorized", response.getBody().getError());
    }

    @Test
    void customAuthenticationFailureMapsTo401() {
        var response = handler.handleCustomAuthenticationException(new AuthenticationException("invalid token"));
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("invalid token", response.getBody().getMessage());
    }

    @Test
    void forbiddenFailureDoesNotExposeInternalMessage() {
        var response = handler.handleAccessDeniedException(new AccessDeniedException("private detail"));
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("No tiene permisos para realizar esta operación", response.getBody().getMessage());
    }

    @Test
    void validationFailureIncludesFieldMessages() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "correo", "El correo es obligatorio"));
        binding.addError(new FieldError("request", "placa", "La placa no es válida"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, binding);

        var response = handler.handleValidationException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Validation Error", response.getBody().getError());
        assertEquals(Map.of("correo", "El correo es obligatorio", "placa", "La placa no es válida"),
                response.getBody().getErrors());
    }

    @Test
    void genericFailureHidesExceptionDetails() {
        var response = handler.handleGenericException(new IllegalStateException("secret"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Ocurrió un error interno en el servidor", response.getBody().getMessage());
        assertNotEquals("secret", response.getBody().getMessage());
    }
}
