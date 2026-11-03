package com.seal.hackathon.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One mentor-support request, shown to both the team and the mentor. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SupportRequestResponse {

    private Integer requestId;
    private Integer teamId;
    private String teamName;
    private Integer trackId;
    private String trackName;
    private String category;         // RULES | TECHNICAL | DIRECTION | OTHER
    private String description;
    private String status;           // OPEN | RESOLVED | CANCELLED
    private String requesterName;
    private LocalDateTime createdAt;
    private String resolvedByName;
    private LocalDateTime resolvedAt;
}
