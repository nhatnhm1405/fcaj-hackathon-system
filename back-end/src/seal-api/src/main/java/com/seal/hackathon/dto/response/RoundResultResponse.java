package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundResultResponse {
    private Integer resultId;
    private Integer teamId;
    private String teamName;
    private String trackName;
    private Integer roundId;
    private String roundName;
    private BigDecimal totalScore;
    private Integer rankPosition;
    private Boolean advanced;
    private Boolean isPublished;
    private LocalDateTime finalizedAt;
    private Integer finalizedById;
    private String finalizedByName;
}
