package com.seal.hackathon.dto.request;

import lombok.Data;

/**
 * Used by a SYSTEM_ADMIN to edit an existing account's profile fields.
 *
 * Patch semantics: a null field is left unchanged. Editable fields are limited to
 * profile data — email (login identity), userType (account category), approval and
 * active flags are intentionally NOT editable here. Approval/activation has its own
 * endpoints; userType is fixed at creation.
 */
@Data
public class UpdateUserRequest {

    // Non-null & non-blank → replaces the current full name
    private String fullName;

    // Non-null → replaces studentId (blank string clears it)
    private String studentId;

    // Non-null → replaces university (blank string clears it)
    private String university;

    // Non-null → INTERNAL | GUEST (blank string clears it; only meaningful for judges)
    private String judgeType;
}
