package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateCriteriaRequest {
    @NotBlank(message = "Criteria name is required")
    private String name;
    private String description;

    @NotNull(message = "Weight is required")
    private BigDecimal weight;

    @NotNull(message = "Max score is required")
    private BigDecimal maxScore;

    private Integer orderNumber;
}
