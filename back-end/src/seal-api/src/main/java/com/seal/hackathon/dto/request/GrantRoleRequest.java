package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for admin role management:
 *   POST   /api/admin/roles/grant
 *   DELETE /api/admin/roles/revoke
 *
 * eventId is null for system-wide grants (e.g. EVENT_COORDINATOR across all events).
 */
@Data
public class GrantRoleRequest {

    @NotNull(message = "userId is required")
    private Integer userId;

    // SYSTEM_ADMIN | EVENT_COORDINATOR | MENTOR | JUDGE
    @NotBlank(message = "Role name is required")
    private String roleName;

    // Null = system-wide; set = scoped to a specific event
    private Integer eventId;
}
