package com.aiops.agent.common;

import org.springframework.http.HttpStatus;

/**
 * AI 服务异常，携带希望返回给客户端的 HTTP 状态。
 */
public class AiServiceException extends RuntimeException {

    private final HttpStatus status;

    public AiServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public AiServiceException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
