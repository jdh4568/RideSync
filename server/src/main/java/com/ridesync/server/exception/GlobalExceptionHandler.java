package com.ridesync.server.exception;

import com.ridesync.server.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("요청 처리 중 예기치 않은 오류가 발생했습니다.", exception);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("요청 데이터를 확인해주세요.");
        return handleExceptionInternal(exception,
                new ErrorResponse("INVALID_REQUEST", message), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        // Spring MVC 오류도 명세의 code/message 형식으로 응답한다.
        if (!(body instanceof ErrorResponse)) {
            String code = status.is5xxServerError() ? "INTERNAL_SERVER_ERROR" : "INVALID_REQUEST";
            String message = status.is5xxServerError()
                    ? "서버 내부 오류가 발생했습니다." : "요청을 처리할 수 없습니다. 요청 내용을 확인해주세요.";
            body = new ErrorResponse(code, message);
        }
        return super.handleExceptionInternal(exception, body, headers, status, request);
    }
}
