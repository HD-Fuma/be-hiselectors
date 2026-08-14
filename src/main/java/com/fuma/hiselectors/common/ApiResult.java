package com.fuma.hiselectors.common;

public record ApiResult<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(true, "OK", null, data);
    }

    public static ApiResult<Void> error(String code, String message) {
        return new ApiResult<>(false, code, message, null);
    }
}
