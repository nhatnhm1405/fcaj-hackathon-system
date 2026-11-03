package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoringCriteriaResponse {
    private Integer criteriaId;
    private String name;
    private String description;
    private BigDecimal weight;
    private BigDecimal maxScore;
    private Integer orderNumber;
}
