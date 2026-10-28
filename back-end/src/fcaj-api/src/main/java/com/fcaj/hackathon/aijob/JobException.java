package com.fcaj.hackathon.aijob;

import org.springframework.http.HttpStatus;

public class JobException extends RuntimeException {
    private final HttpStatus status;
    public JobException(HttpStatus status, String code) { super(code); this.status = status; }
    public HttpStatus status() { return status; }
    static JobException hidden() { return new JobException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND"); }
    static JobException invalid(String code) { return new JobException(HttpStatus.BAD_REQUEST, code); }
}
