package com.seal.hackathon.dto.request;

import lombok.Data;

/**
 * Coordinator (re)assigns a team to a track during SETUP, or pulls it back to the
 * unassigned pool. Unlike {@link SelectTrackRequest}, trackId is NULLABLE: a null
 * value moves the team to "unassigned".
 */
@Data
public class AssignTeamTrackRequest {

    /** Target track, or null to unassign the team (move it to the unassigned pool). */
    private Integer trackId;
}
