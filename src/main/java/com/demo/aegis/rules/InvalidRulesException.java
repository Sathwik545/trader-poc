package com.demo.aegis.rules;

/** The rules file cannot be used: it is missing, malformed, or holds an expression that does not parse. */
public class InvalidRulesException extends RuntimeException {

    public InvalidRulesException(String message) {
        super(message);
    }

    public InvalidRulesException(String message, Throwable cause) {
        super(message, cause);
    }
}
