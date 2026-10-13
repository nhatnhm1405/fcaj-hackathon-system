package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * A prize's winning team and rank always come from {@code autoGenerate} (the
 * final ranking) — a coordinator may only rename a slot, and only before it's
 * announced.
 */
@Data
public class UpdatePrizeRequest {
    @NotBlank(message = "Prize name is required")
    private String name;
}
