package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateRoundRequest {
    private String name;
    private Integer orderNumber;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime submissionDeadline;

    @Min(value = 1, message = "Top N advance must be at least 1")
    private Integer topNAdvance;

    // Partial update can't tell "field omitted" from "set to null", so a value of
    // topNAdvance only ever SETS the cut-off. To REMOVE the cut-off (no elimination),
    // send clearTopNAdvance = true.
    private Boolean clearTopNAdvance;

    private Boolean isFinal;
    private String status; // PENDING | ACTIVE | CLOSED | FINALIZED
}
