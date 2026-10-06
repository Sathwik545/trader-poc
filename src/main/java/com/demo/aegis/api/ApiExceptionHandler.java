package com.demo.aegis.api;

import com.demo.aegis.ingest.InvalidTradeFileException;
import com.demo.aegis.ingest.RunNotFoundException;
import com.demo.aegis.rules.InvalidRulesException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.time.Instant;

/** Turns every exception into the same {@link ApiError} body. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidTradeFileException.class)
    public ResponseEntity<ApiError> invalidFile(InvalidTradeFileException e, HttpServletRequest request) {
        log.warn("Rejected upload: {}", e.getMessage());
        return respond(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(RunNotFoundException.class)
    public ResponseEntity<ApiError> runNotFound(RunNotFoundException e, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(InvalidRulesException.class)
    public ResponseEntity<ApiError> invalidRules(InvalidRulesException e, HttpServletRequest request) {
        log.warn("Rejected rules: {}", e.getMessage());
        return respond(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> badParameter(ConstraintViolationException e, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file is larger than the allowed size", request);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiError> notMultipart(MultipartException e, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Send the CSV as multipart form data in a part named 'file'", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> badArgument(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "'" + e.getValue() + "' is not a valid value for " + e.getName(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> other(Exception e, HttpServletRequest request) {
        // Spring's own request errors (missing part, wrong method, unknown path) carry their status
        if (e instanceof ErrorResponse known) {
            HttpStatus status = HttpStatus.valueOf(known.getStatusCode().value());
            return respond(status, known.getBody().getDetail(), request);
        }
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error; see the server log", request);
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
