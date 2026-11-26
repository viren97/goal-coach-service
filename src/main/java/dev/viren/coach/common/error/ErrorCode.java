package dev.viren.coach.common.error;

import org.springframework.http.HttpStatus;

/** Single catalogue of business error codes. A new failure reason is a new entry, not a new exception class. */
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT),
    GOAL_LIMIT_REACHED(HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY),
    GOAL_NOT_ACTIVE(HttpStatus.CONFLICT),
    ILLEGAL_STATE_TRANSITION(HttpStatus.CONFLICT),
    CONCURRENCY_CONFLICT(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) { this.httpStatus = httpStatus; }

    public HttpStatus httpStatus() { return httpStatus; }
}
