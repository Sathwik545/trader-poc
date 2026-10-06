package com.demo.aegis.ingest;

/** A single row could not be parsed. The row is skipped and recorded; the file carries on. */
public class RowParseException extends RuntimeException {

    public RowParseException(String message) {
        super(message);
    }
}
