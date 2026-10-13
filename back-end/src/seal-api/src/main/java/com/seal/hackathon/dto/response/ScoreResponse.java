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
public class ScoreResponse {
    private Integer scoreId;
    private Integer submissionId;
    private Integer judgeUserId;
    private String judgeName;
    private Integer criteriaId;
    private String criteriaName;
    private BigDecimal value;
    private String comment;
    private Boolean isDraft;
    private LocalDateTime scoredAt;
    private LocalDateTime updatedAt;
}
