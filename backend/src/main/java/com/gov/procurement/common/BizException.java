package com.gov.procurement.common;

/**
 * 业务异常：用于校验失败、状态非法、超预算/超库存等可预期错误，由全局异常处理统一转 {@link Result}。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(ErrorCode.PARAM_INVALID.code(), message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 以错误码枚举的默认文案抛出。 */
    public BizException(ErrorCode errorCode) {
        this(errorCode.code(), errorCode.message());
    }

    /** 以错误码枚举 + 自定义文案抛出。 */
    public BizException(ErrorCode errorCode, String message) {
        this(errorCode.code(), message);
    }

    public int getCode() {
        return code;
    }
}
