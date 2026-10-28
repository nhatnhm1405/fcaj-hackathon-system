package com.fcaj.hackathon.aijob;

import com.fcaj.hackathon.dto.response.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=AiJobController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name="app.ai.jobs.enabled", havingValue="true")
public class JobErrors {
    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class})
    ResponseEntity<ApiResponse<?>> concurrent() { return error(HttpStatus.CONFLICT,"CONCURRENT_UPDATE"); }
    @ExceptionHandler(JobException.class)
    ResponseEntity<ApiResponse<?>> job(JobException ex) { return error(ex.status(),ex.getMessage()); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<?>> denied() { return error(HttpStatus.FORBIDDEN,"JOB_ACCESS_FORBIDDEN"); }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<?>> invalid() { return error(HttpStatus.BAD_REQUEST,"INVALID_JOB_IDENTIFIER"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<?>> unexpected(Exception ex) {
        if(com.fcaj.hackathon.exception.GlobalExceptionHandler.isConcurrentUpdate(ex)) return concurrent();
        return error(HttpStatus.SERVICE_UNAVAILABLE,"JOB_SERVICE_UNAVAILABLE");
    }
    private ResponseEntity<ApiResponse<?>> error(HttpStatus status,String code) { return ResponseEntity.status(status).body(ApiResponse.error(code)); }
}
