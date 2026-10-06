package com.demo.aegis.persist;

/** A row of the uploaded file that was skipped, and why. */
public record RowError(long rowNumber, String message) {
}
