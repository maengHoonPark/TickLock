package com.ticklock.common;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 모든 컨트롤러의 오류를 {"message": "..."} 한 가지 모양으로 통일한다.
 * 프론트는 이 모양만 알면 되므로 오류 처리 코드가 단순해진다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorBody(String message) {
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(new ErrorBody(e.getMessage()));
    }

    /** @Valid 검증 실패. 첫 번째 위반 메시지만 보여준다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException e) {
        String message = "요청 값이 올바르지 않아요.";
        List<FieldError> errors = e.getBindingResult().getFieldErrors();
        if (!errors.isEmpty() && errors.get(0).getDefaultMessage() != null) {
            message = errors.get(0).getDefaultMessage();
        }
        return ResponseEntity.badRequest().body(new ErrorBody(message));
    }

    /** JSON 문법 오류, 허용되지 않는 enum 값 등. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorBody("요청 형식이 올바르지 않아요."));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ErrorBody> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.badRequest().body(new ErrorBody(e.getHeaderName() + " 헤더가 필요해요."));
    }

    /** 예상하지 못한 오류. 내부 정보는 숨기고 일반 메시지만 내려준다 (상세 내용은 서버 로그에서 확인). */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> handleUnexpected(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorBody("일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요."));
    }
}
