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
public class RoundResponse {
    private Integer roundId;
    private Integer eventId;
    private String eventName;
    private String name;
    private Integer orderNumber;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime submissionDeadline;
    private Integer topNAdvance;
    private Boolean isFinal;
    private String status;
}
