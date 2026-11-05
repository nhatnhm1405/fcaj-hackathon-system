package com.seal.hackathon.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Read-only view of a SystemLog entry for the admin System Log screen.
 * Actor name is resolved so the UI never shows a raw user_id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemLogResponse {

    private Integer logId;
    private Integer actorUserId;
    private String actorName;
    private String action;
    private String detail;
    private LocalDateTime createdAt;
}
