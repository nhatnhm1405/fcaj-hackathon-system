package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Used by a SYSTEM_ADMIN to create a platform account directly (no self-registration,
 * pre-approved). Typical use: guest judges, mentors, or a coordinator account.
 *
 * Role grants are a SEPARATE step (POST /api/admin/roles/grant) — creating the
 * account does not assign any role.
 */
@Data
public class CreateUserRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    // FPT_STUDENT | EXTERNAL_STUDENT | STAFF (admin typically creates STAFF)
    @NotBlank(message = "User type is required")
    private String userType;

    // INTERNAL | GUEST — set only for users who will act as judges; null otherwise
    private String judgeType;
}
