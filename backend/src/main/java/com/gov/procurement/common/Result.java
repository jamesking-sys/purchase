package com.gov.procurement.common;

/**
 * 统一响应包装。约定：code=0 成功，非 0 为业务/系统错误码。
 *
 * @param <T> 数据类型
 */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
