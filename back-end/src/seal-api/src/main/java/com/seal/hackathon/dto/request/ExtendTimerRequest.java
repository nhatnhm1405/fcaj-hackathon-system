package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** Adds time to a RUNNING or PAUSED timer (e.g. +5 minutes = 300 seconds). */
@Data
public class ExtendTimerRequest {

    @NotNull(message = "seconds is required")
    @Positive(message = "seconds must be positive")
    private Integer seconds;
}
