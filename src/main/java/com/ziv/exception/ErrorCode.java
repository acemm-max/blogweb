package com.ziv.exception;

import java.util.Arrays;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Common transport/system errors. Business-specific codes are configured separately. */
@Getter
@AllArgsConstructor
public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.value(), "Bad Request"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.value(), "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN.value(), "Access denied"),
    NOT_FOUND(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.value(), "Not Found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED.value(), "Method Not Allowed"),
    CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.value(), "Conflict"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "Unsupported Media Type"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal server error"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE.value(), "Service Unavailable"),
    DATA_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE.value(), "Data storage is unavailable");

    private final HttpStatus httpStatus;
    private final int code;
    private final String message;

    public static Optional<ErrorCode> fromHttpStatus(int status) {
        return Arrays.stream(values()).filter(error -> error.httpStatus.value() == status).findFirst();
    }
}
