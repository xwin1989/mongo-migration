package com.neal.exception;

/**
 * @author Neal
 */
public class FlywayExecuteException extends RuntimeException {

    public FlywayExecuteException(String message) {
        super(message);
    }

    public FlywayExecuteException(String message, Throwable cause) {
        super(message, cause);
    }

}
