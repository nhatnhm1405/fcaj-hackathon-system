package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamRejoinRequestResponse {
    private Integer requestId;
    private Integer teamId;
    private String teamName;
    private Integer eventId;
    private String eventName;
    private Integer requestedByUserId;
    private String requestedByName;
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime resolvedAt;
    private Integer resolvedBy;
}
