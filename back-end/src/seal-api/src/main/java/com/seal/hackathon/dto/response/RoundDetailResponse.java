package com.seal.hackathon.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RoundDetailResponse {
    private Integer roundId;
    private String eventName;
    private String roundName;
    private Integer orderNumber;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime submissionDeadline;
    private Integer topNAdvance;
    private Boolean isFinal;
    private String status;
}
