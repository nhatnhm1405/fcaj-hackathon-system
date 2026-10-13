package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for POST /api/auth/login
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    // Controls the auth cookie's lifetime: true = persistent (Max-Age set),
    // false/omitted = session cookie (cleared when the browser closes).
    private boolean rememberMe;
}
