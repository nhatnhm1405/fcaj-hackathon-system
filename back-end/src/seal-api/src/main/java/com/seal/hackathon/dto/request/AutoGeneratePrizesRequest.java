package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Builds {@code topN} draft prize slots from the final round's global ranking.
 * {@code topN} is independent of any round's advancement cut-off — it is how many
 * teams the organizers want to award.
 */
@Data
public class AutoGeneratePrizesRequest {
    @NotNull(message = "topN is required")
    @Min(value = 1, message = "topN must be at least 1")
    private Integer topN;
}
