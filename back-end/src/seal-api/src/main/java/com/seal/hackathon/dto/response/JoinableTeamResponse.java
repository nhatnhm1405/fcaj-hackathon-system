package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinableTeamResponse {
    private Integer teamId;
    private String teamName;
    private String description;
    private Integer eventId;
    private String eventName;
    private Integer trackId;
    private String trackName;
    private String teamStatus;
    private Integer memberCount;
    private Integer maxMembers;
    private Integer leaderUserId;
    private String leaderName;
    private Integer myRequestId;
    private String myRequestStatus;
}
