package com.ziv.exception;

import com.ziv.common.ApiResponse;
import com.ziv.common.ApiResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.stream.Collectors;
import com.ziv.util.ErrorLogUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct().sorted().collect(Collectors.joining("; "));
        if (message.isEmpty()) message = "请求参数校验失败";
        log.warn("Request validation failed: {} status={} details={}", request.getDescription(false), status.value(), message);
        return new ResponseEntity<>(ApiResponseUtil.error(ErrorCode.BAD_REQUEST.getCode(), message), headers, status);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception, HttpServletRequest request) {
        log.warn("Business error: method={} path={} status={} code={} message={}",
                request.getMethod(), request.getRequestURI(), exception.getStatus().value(), exception.getCode(), exception.getMessage());
        if (exception.getCause() != null) ErrorLogUtil.log(log, "Business error cause", exception);
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResponseUtil.error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDatabase(DataAccessException exception, HttpServletRequest request) {
        ErrorLogUtil.log(log, "Database operation failed: method=" + request.getMethod()
                + " path=" + request.getRequestURI() + " status=503", exception);
        return ResponseEntity.status(ErrorCode.DATA_STORE_UNAVAILABLE.getHttpStatus())
                .body(ApiResponseUtil.error(ErrorCode.DATA_STORE_UNAVAILABLE));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Preserve framework status codes such as 400, 404 and 405.
        HttpStatus knownStatus = HttpStatus.resolve(status.value());
        String message = knownStatus == null ? "Request failed" : knownStatus.getReasonPhrase();
        log.warn("HTTP request error: {} status={} type={}", request.getDescription(false), status.value(), exception.getClass().getName());
        ApiResponse<Void> errorBody = ErrorCode.fromHttpStatus(status.value())
                .map(ApiResponseUtil::error)
                .orElseGet(() -> ApiResponseUtil.error(status.value(), message));
        return super.handleExceptionInternal(exception,
                errorBody, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnexpected(Exception exception, HttpServletRequest request) {
        ErrorLogUtil.log(log, "Unhandled request error: method=" + request.getMethod()
                + " path=" + request.getRequestURI() + " status=500", exception);
        return ApiResponseUtil.error(ErrorCode.INTERNAL_ERROR);
    }
}
