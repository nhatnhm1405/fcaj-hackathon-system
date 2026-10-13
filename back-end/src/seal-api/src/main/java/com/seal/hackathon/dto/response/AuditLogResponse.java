package com.seal.hackathon.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Read-only view of an AuditLog entry for the audit review screen.
 * Actor name is resolved so the UI never shows a raw user_id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditLogResponse {

    private Integer logId;
    private Integer actorUserId;
    private String actorName;
    private String action;
    private String targetType;
    private Integer targetId;
    private String reason;
    private String metadataJson;
    private String ipAddress;
    private LocalDateTime createdAt;
}
