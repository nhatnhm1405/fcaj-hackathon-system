package com.fcaj.hackathon.mockdemo;

import org.springframework.http.HttpStatus;

public class MockJobException extends RuntimeException {
    private final HttpStatus status;
    public MockJobException(HttpStatus status, String safeCode) {
        super(safeCode);
        this.status = status;
    }
    public HttpStatus status() { return status; }
    static MockJobException hidden() { return new MockJobException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND"); }
}
