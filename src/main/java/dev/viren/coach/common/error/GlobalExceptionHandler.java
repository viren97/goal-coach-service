package dev.viren.coach.common.error;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CoachException.class)
    public ResponseEntity<ApiError> handleDomain(CoachException ex) {
        return respond(ex.code(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return respond(ErrorCode.INVALID_REQUEST, "Request validation failed", details);
    }

    /** Constraint violations on path variables / headers (e.g. Idempotency-Key length). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex) {
        List<String> details = ex.getAllErrors().stream().map(e -> String.valueOf(e.getDefaultMessage())).toList();
        return respond(ErrorCode.INVALID_REQUEST, "Request validation failed", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return respond(ErrorCode.INVALID_REQUEST, "Malformed request body", List.of());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        return respond(ErrorCode.INVALID_REQUEST, "Required header '" + ex.getHeaderName() + "' is missing", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(ErrorCode.INVALID_REQUEST, "Invalid value for '" + ex.getName() + "'", List.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return respond(ErrorCode.CONCURRENCY_CONFLICT, "Resource was modified concurrently, please retry", List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
        return respond(ErrorCode.DUPLICATE_RESOURCE, "Request conflicts with an existing resource", List.of());
    }

    @ExceptionHandler({
            NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ApiError> handleFramework(Exception ex) {
        HttpStatus status = HttpStatus.valueOf(((ErrorResponse) ex).getStatusCode().value());
        return ResponseEntity.status(status).body(new ApiError(status.name(), ex.getMessage(), List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(ErrorCode.INTERNAL_ERROR, "Unexpected error, please retry later", List.of());
    }

    private ResponseEntity<ApiError> respond(ErrorCode code, String message, List<String> details) {
        return ResponseEntity.status(code.httpStatus()).body(new ApiError(code.name(), message, details));
    }
}
