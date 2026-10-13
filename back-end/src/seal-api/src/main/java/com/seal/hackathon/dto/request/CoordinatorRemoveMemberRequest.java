package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Coordinator's mandatory-reason removal of a specific team member during a live
 * (IN_PROGRESS) competition — e.g. absence at a roll call. See
 * {@code TeamModerationService#coordinatorRemoveMember} for the removal rules.
 */
@Data
public class CoordinatorRemoveMemberRequest {

    @NotBlank(message = "A reason is required.")
    @Size(max = 1000, message = "Reason must be at most 1000 characters")
    private String reason;
}
