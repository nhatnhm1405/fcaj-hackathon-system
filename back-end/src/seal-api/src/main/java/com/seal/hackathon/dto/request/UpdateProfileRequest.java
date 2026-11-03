package com.seal.hackathon.dto.request;

import lombok.Data;

/**
 * Body for PUT /api/auth/me — a user edits their OWN profile.
 * Patch semantics: a null field is left unchanged. Email, userType, judgeType,
 * studentId, approval and active flags are NOT self-editable (managed by admin).
 */
@Data
public class UpdateProfileRequest {
    private String fullName;
    // Kept so the service can explicitly reject attempts to change this field.
    private String studentId;
    private String university;
}
