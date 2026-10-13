package com.seal.hackathon.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bootstrap endpoint the SPA hits before its first state-changing request when
 * it has no XSRF-TOKEN cookie yet. The body is empty — CsrfCookieFilter sets the
 * cookie on the response regardless, which is all the client needs to read the
 * token and echo it back as X-XSRF-TOKEN. Public so it works before login.
 */
@RestController
public class CsrfController {

    @GetMapping("/api/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }
}
