package com.demo.aegis.ingest;

/** The uploaded file as a whole is unusable (empty, no header, missing columns, unreadable CSV). */
public class InvalidTradeFileException extends RuntimeException {

    public InvalidTradeFileException(String message) {
        super(message);
    }

    public InvalidTradeFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
