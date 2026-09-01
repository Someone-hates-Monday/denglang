package com.cqu.config;

import com.cqu.security.ForbiddenException;
import com.cqu.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<String> handleForbidden(ForbiddenException e) {
        return Result.fail(403, e.getMessage());
    }

    /** 参数/资源问题：含「不存在」→ 404，其余 → 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<String>> handleIllegalArgument(IllegalArgumentException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "参数错误";
        int code = msg.contains("不存在") ? 404 : 400;
        return ResponseEntity.status(code).body(Result.fail(code, msg));
    }

    /** 状态机冲突（如 PENDING 上 claim）→ 409 */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<String> handleIllegalState(IllegalStateException e) {
        return Result.fail(409, e.getMessage());
    }

    @ExceptionHandler
    public Result<String> handleException(Exception e) {
        log.error("服务器异常:", e);
        return Result.fail("服务器异常:" + e.getMessage());
    }
}
