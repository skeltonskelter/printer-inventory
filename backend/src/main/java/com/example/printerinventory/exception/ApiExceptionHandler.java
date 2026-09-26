package com.example.printerinventory.exception;

import com.example.printerinventory.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.ErrorResponse;
import org.springframework.validation.method.ParameterErrors;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> applicationError(ApiException exception, HttpServletRequest request) {
        return error(exception.getStatus(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(field ->
                fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields.", request, fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> methodValidation(HandlerMethodValidationException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors errors) {
                errors.getFieldErrors().forEach(field -> fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
            }
        });
        if (fields.isEmpty()) return parameterValidation(exception, request);
        return error(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields.", request, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> parameterValidation(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST,
                "Invalid request parameters. IDs must be positive; page must be 0–1000000 and size 1–100.",
                request, Map.of());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> unreadable(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST,
                "Invalid JSON or parameter value. Use numeric IDs and supported values.", request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        String detail = exception.getMostSpecificCause().getMessage();
        String message = "This change conflicts with existing data. Referenced locations cannot be deleted.";
        if (detail != null && detail.contains("uk_printers_sticker")) message = "Sticker number is already in use.";
        if (detail != null && detail.contains("uk_printers_serial")) message = "Serial number is already in use.";
        if (detail != null && detail.contains("uk_app_users_username_lower")) message = "Username already exists.";
        return error(HttpStatus.CONFLICT, message, request, Map.of());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ApiError> concurrentChange(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "This record changed during your request. Reload it and try again.", request, Map.of());
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> database(DataAccessException exception, HttpServletRequest request) {
        log.error("Database request failed", exception);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "The database is temporarily unavailable. Try again shortly.", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse response) {
            return error(response.getStatusCode(), "The requested resource or operation is not available.", request, Map.of());
        }
        log.error("Unexpected API error", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again.", request, Map.of());
    }

    private ResponseEntity<ApiError> error(HttpStatusCode status, String message,
                                           HttpServletRequest request, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(),
                message, request.getRequestURI(), fields));
    }
}
