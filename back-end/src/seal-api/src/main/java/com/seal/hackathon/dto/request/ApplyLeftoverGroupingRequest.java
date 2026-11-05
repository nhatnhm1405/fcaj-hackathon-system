package com.seal.hackathon.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * A coordinator-edited version of the leftover-grouping preview: the exact final
 * roster for every proposed team (existing or brand-new), after freely dragging
 * people between the preview's proposed-team cards. See
 * {@code LeftoverGroupingService#applyPlan} for how this is validated and applied.
 */
@Data
public class ApplyLeftoverGroupingRequest {

    @NotEmpty(message = "teams is required")
    @Valid
    private List<TeamComposition> teams;

    private String reason;

    /** One proposed team's final desired membership. */
    @Data
    public static class TeamComposition {

        /** An existing team to grow; null to force-create a brand-new team. */
        private Integer existingTeamId;

        /** This team's complete final roster (not just newcomers). */
        @NotEmpty(message = "memberUserIds is required")
        @Size(max = 5, message = "A team cannot have more than 5 members")
        private List<Integer> memberUserIds;
    }
}
