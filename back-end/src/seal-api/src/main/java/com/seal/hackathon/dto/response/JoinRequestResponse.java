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
public class JoinRequestResponse {
    private Integer requestId;
    private Integer teamId;
    private String teamName;
    private Integer eventId;
    private String eventName;
    private Integer trackId;
    private String trackName;
    private String teamStatus;
    private Integer requesterUserId;
    private String requesterName;
    private String requesterEmail;
    private String message;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
}
