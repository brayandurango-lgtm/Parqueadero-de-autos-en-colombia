package com.autoscolombia.infrastructure.exception;

/**
 * Excepción lanzada cuando falla la autenticación.
 */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
