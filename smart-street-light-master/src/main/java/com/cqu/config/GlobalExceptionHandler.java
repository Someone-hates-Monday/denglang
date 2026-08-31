package com.cqu.config;

import com.cqu.security.ForbiddenException;
import com.cqu.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ForbiddenException.class)
    public Result<String> handleForbidden(ForbiddenException e) {
        return Result.fail(403, e.getMessage());
    }

    @ExceptionHandler
    public Result<String> handleException(Exception e) {
        log.error("服务器异常:", e);
        return Result.fail("服务器异常:" + e.getMessage());
    }
}
