package com.autoscolombia.infrastructure.exception;

/**
 * Excepción lanzada cuando se viola una regla de negocio del sistema.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }

    public BusinessRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
