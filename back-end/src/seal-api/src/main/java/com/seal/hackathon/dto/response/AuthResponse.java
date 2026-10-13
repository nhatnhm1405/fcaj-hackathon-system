package com.seal.hackathon.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for register and login endpoints.
 * token is null after registration (user must wait for approval).
 * token is populated after successful login.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String token;
    private String tokenType;

    private Integer userId;
    private String email;
    private String fullName;
    private List<String> roles;

    // Human-readable message (e.g., "Registration successful. Awaiting approval.")
    private String message;
}
