package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Body for PUT /api/auth/complete-profile.
 * A first-time OAuth user (userType = PENDING_PROFILE) picks their real account
 * type and supplies student details. Approval still happens afterwards.
 */
@Data
public class CompleteProfileRequest {

    /** FPT_STUDENT | EXTERNAL_STUDENT | STAFF */
    @NotBlank(message = "User type is required")
    private String userType;

    private String studentId;
    private String university;
}
