package com.ziv.common;

import com.ziv.exception.ErrorCode;

/** Static factories for the shared API response body. */
public final class ApiResponseUtil {
    private ApiResponseUtil() {
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode);
    }
}
