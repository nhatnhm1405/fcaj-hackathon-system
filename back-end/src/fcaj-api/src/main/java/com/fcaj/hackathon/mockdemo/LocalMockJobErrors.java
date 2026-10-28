package com.fcaj.hackathon.mockdemo;

import com.fcaj.hackathon.dto.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Profile("mock-demo")
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = LocalMockJobController.class)
public class LocalMockJobErrors {
    @ExceptionHandler(MockJobException.class)
    ResponseEntity<ApiResponse<?>> job(MockJobException exception) {
        return ResponseEntity.status(exception.status()).body(ApiResponse.error(exception.getMessage()));
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<?>> denied() { return error(HttpStatus.FORBIDDEN, "JOB_ACCESS_FORBIDDEN"); }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<?>> invalid() { return error(HttpStatus.BAD_REQUEST, "INVALID_JOB_IDENTIFIER"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<?>> unexpected() { return error(HttpStatus.INTERNAL_SERVER_ERROR, "MOCK_JOB_UNAVAILABLE"); }
    private ResponseEntity<ApiResponse<?>> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).body(ApiResponse.error(code));
    }
}
