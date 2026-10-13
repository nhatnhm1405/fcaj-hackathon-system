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
public class TeamResponse {
    private Integer teamId;
    private Integer eventId;
    private String eventName;
    private Integer trackId;
    private String trackName;
    private String name;
    private String description;
    private String status;
    private LocalDateTime createdAt;
}
