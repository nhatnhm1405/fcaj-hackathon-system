package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for POST /api/auth/register
 *
 * Business rules enforced here:
 * - FPT_STUDENT      → studentId required
 * - EXTERNAL_STUDENT → studentId + university required
 *
 * No role is assigned at registration. Participants are identified by user_type.
 * Staff roles (EVENT_COORDINATOR, MENTOR, JUDGE) are assigned by a coordinator after approval.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;

    // FPT_STUDENT | EXTERNAL_STUDENT | STAFF
    @NotBlank(message = "User type is required")
    private String userType;

    // Required if userType = FPT_STUDENT or EXTERNAL_STUDENT
    private String studentId;

    // Required if userType = EXTERNAL_STUDENT
    private String university;
}
