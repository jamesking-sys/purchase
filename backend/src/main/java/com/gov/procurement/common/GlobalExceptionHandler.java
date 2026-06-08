package com.gov.procurement.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：把 Sa-Token / 校验 / 业务异常映射为统一 {@link Result} 与合适的 HTTP 状态。
 * 未登录 → 401(40110)，缺角色/权限 → 403(40301)，参数校验失败 → 400(40001)，
 * 业务异常按错误码前三位派生 HTTP 状态（如 40101 → 401），兜底 → 500(50000)。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 错误码前三位即对应 HTTP 状态（40101→401、40301→403…），用此除数取前三位。 */
    private static final int HTTP_STATUS_DIVISOR = 100;

    /** 未登录 / token 无效 → 401。 */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(ErrorCode.NOT_LOGIN.code(), ErrorCode.NOT_LOGIN.message()));
    }

    /** 缺少角色 / 权限 → 403。 */
    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    public ResponseEntity<Result<Void>> handleNoPermission(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.error(ErrorCode.NO_PERMISSION.code(), ErrorCode.NO_PERMISSION.message()));
    }

    /** 业务异常 → 按错误码派生 HTTP 状态。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(resolveStatus(e.getCode()))
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    /** 唯一约束冲突（并发双写绕过应用层 count 校验时的最终防线）→ 409（40902）。 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey(DuplicateKeyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(ErrorCode.CODE_DUPLICATE.code(), ErrorCode.CODE_DUPLICATE.message()));
    }

    /** 缺少必填请求参数 / multipart 部件 → 400（40001）。 */
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<Result<Void>> handleMissingParam(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ErrorCode.PARAM_INVALID.code(), "缺少必填参数：" + e.getMessage()));
    }

    /** 入参校验失败 → 400，聚合字段错误信息。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ErrorCode.PARAM_INVALID.code(),
                        msg.isBlank() ? ErrorCode.PARAM_INVALID.message() : msg));
    }

    /** 兜底：其余未捕获异常 → 500（对外不泄露堆栈，服务端记录完整堆栈便于排查）。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("未捕获异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ErrorCode.SYSTEM_ERROR.code(), ErrorCode.SYSTEM_ERROR.message()));
    }

    private HttpStatus resolveStatus(int code) {
        HttpStatus status = HttpStatus.resolve(code / HTTP_STATUS_DIVISOR);
        return status != null ? status : HttpStatus.BAD_REQUEST;
    }

    private String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }
}
