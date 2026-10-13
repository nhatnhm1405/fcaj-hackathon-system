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
public class TeamInviteResponse {
    private Integer inviteId;
    private Integer teamId;
    private String teamName;
    private String eventName;
    private String trackName;
    private String teamStatus;
    private Integer invitedUserId;
    private String invitedUserName;
    private Integer invitedById;
    private String invitedByName;
    private String message;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
}
