package com.seal.hackathon.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-only view of a UserEventRole grant, with names resolved
 * (role, event) so the UI never has to show raw IDs.
 * Concrete work assignments (round/track/judgeType) live in
 * JudgeAssignment / MentorAssignment, not here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserEventRoleResponse {

    private Integer id;

    private Integer userId;
    private String userFullName;
    private String userEmail;

    // Resolved from Role.roleName — never expose role_id to the UI
    private String roleName;

    private Integer eventId;
    private String eventName;
}
