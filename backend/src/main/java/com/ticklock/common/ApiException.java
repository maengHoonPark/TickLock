package com.ticklock.common;

import org.springframework.http.HttpStatus;

/** 사용자에게 보여줘도 되는 오류. HTTP 상태와 한국어 메시지를 함께 들고 다닌다. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
