package com.autoscolombia.infrastructure.exception;

/**
 * Excepción lanzada cuando un recurso no se encuentra en el sistema.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
