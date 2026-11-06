package com.blogweb.common.constant;

import java.util.Arrays;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Common transport/system errors. Business-specific codes are configured separately. */
@Getter
@AllArgsConstructor
public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "HTTP_400", "Bad Request"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "HTTP_404", "Not Found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP_405", "Method Not Allowed"),
    CONFLICT(HttpStatus.CONFLICT, "HTTP_409", "Conflict"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "HTTP_415", "Unsupported Media Type"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "HTTP_429", "Too Many Requests"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "HTTP_503", "Service Unavailable"),
    DATA_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "DATA_STORE_UNAVAILABLE", "Data storage is unavailable");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    public static Optional<ErrorCode> fromHttpStatus(int status) {
        return Arrays.stream(values()).filter(error -> error.httpStatus.value() == status).findFirst();
    }
}
