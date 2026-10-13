package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Coordinator's manual escape hatch for a leftover person/under-sized team that the
 * automatic {@code LeftoverGroupingPlanner} could not place. See
 * {@code LeftoverGroupingService#manualAssign} for the placement rules.
 */
@Data
public class ManualAssignLeftoverRequest {

    @NotEmpty(message = "userIds is required")
    @Size(max = 5, message = "Cannot place more than 5 users at once")
    private List<Integer> userIds;

    /** Existing team to place userIds onto; null to force-create a brand-new team. */
    private Integer targetTeamId;

    private String reason;
}
