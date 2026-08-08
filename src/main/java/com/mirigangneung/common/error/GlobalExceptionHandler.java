package com.mirigangneung.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handle(ApiException e, HttpServletRequest r) { return response(e.getStatus(), e.getCode(), e.getMessage(), r); }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> validation(Exception e, HttpServletRequest r) { return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청값이 올바르지 않습니다.", r); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> tooLarge(Exception e, HttpServletRequest r) { return response(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", "이미지 파일이 너무 큽니다.", r); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unknown(Exception e, HttpServletRequest r) { log.error("Unhandled request error: method={}, path={}, type={}, message={}", r.getMethod(), r.getRequestURI(), e.getClass().getName(), e.getMessage(), e); return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다.", r); }
    private ResponseEntity<ErrorResponse> response(HttpStatus s, String c, String m, HttpServletRequest r) { return ResponseEntity.status(s).body(new ErrorResponse(OffsetDateTime.now(), s.value(), c, m, r.getRequestURI())); }
    public record ErrorResponse(OffsetDateTime timestamp, int status, String code, String message, String path) {}
}
