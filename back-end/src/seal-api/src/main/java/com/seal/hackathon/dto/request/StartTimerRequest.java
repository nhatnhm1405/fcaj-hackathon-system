package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

/**
 * Starts a round timer. Milestone reminders are configurable: omit
 * {@code milestoneMinutes}/{@code notifyAtHalf} to use the defaults
 * ([30, 15, 5, 1] minutes + half-time). Only marks smaller than the total
 * duration ever fire, so short contests degrade gracefully.
 */
@Data
public class StartTimerRequest {

    @NotNull(message = "durationSeconds is required")
    @Positive(message = "durationSeconds must be positive")
    private Integer durationSeconds;

    // Optional: "minutes remaining" reminder marks. Null = use default.
    private List<Integer> milestoneMinutes;

    // Optional: also notify at 50% elapsed. Null = use default (true).
    private Boolean notifyAtHalf;
}
