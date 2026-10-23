package com.aiops.agent.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

/**
 * 全局异常处理：保证所有接口返回统一的 JSON 错误结构。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("请求参数不合法");
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler({HandlerMethodValidationException.class,
            ConstraintViolationException.class})
    public ResponseEntity<ApiError> handleParameterValidation(
            Exception exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "请求参数不合法", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleBadRequest(
            Exception exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "请求参数格式不正确", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(
            IllegalArgumentException exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                safeMessage(exception, "请求参数不合法"), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ResourceNotFoundException exception, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ApiError> handleAiService(
            AiServiceException exception, HttpServletRequest request) {
        return build(exception.getStatus(), exception.getMessage(), request);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiError> handleRestClient(
            RestClientException exception, HttpServletRequest request) {
        log.warn("调用外部服务失败: {}", exception.getMessage());
        return build(HttpStatus.BAD_GATEWAY,
                "外部服务暂时不可用，请稍后重试", request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDataAccess(
            DataAccessException exception, HttpServletRequest request) {
        log.error("数据库访问失败", exception);
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "数据库访问失败，请稍后重试", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "不支持的请求方法", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(
            NoResourceFoundException exception, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "请求的资源不存在", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        log.error("未处理的服务器异常", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "服务器内部错误，请稍后重试", request);
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status, String message, HttpServletRequest request) {
        ApiError body = new ApiError(
                status.value(),
                message,
                Instant.now(),
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    private String safeMessage(Exception exception, String fallback) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }
}
