package com.zyyqq.desk.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 全局异常处理器：统一把各类异常转为标准 ApiResponse */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 处理业务异常 */
    @ExceptionHandler(BizException.class)
    public ApiResponse<?> handleBiz(BizException e) {
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /** 处理参数校验异常 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<?> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("参数校验失败");
        return ApiResponse.error(400, msg);
    }

    /** 处理唯一键冲突异常 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResponse<?> handleDuplicate(DuplicateKeyException e) {
        return ApiResponse.error(409, "数据已存在，请勿重复提交");
    }

    /** 处理接口不存在异常 */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ApiResponse<?> handleNotFound(Exception e) {
        return ApiResponse.error(404, "接口不存在");
    }

    /** 处理其他未捕获异常 */
    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleOther(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.error(500, "系统内部错误：" + e.getMessage());
    }
}
