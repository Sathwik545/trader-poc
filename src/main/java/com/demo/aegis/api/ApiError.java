package com.demo.aegis.api;

import java.time.Instant;

/** The body of every error response from the API. */
public record ApiError(Instant timestamp, int status, String error, String message, String path) {
}
