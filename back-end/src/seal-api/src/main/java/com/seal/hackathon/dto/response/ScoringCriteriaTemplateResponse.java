package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoringCriteriaTemplateResponse {
    private Integer templateId;
    private String name;
    private String description;
    private Boolean isDefault;
    /** The template's own criteria items (event/round-independent). */
    private List<ScoringCriteriaResponse> items;
}
