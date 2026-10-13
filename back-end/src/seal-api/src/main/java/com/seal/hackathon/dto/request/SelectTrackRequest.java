package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Team leader picks a track during the SETUP phase (SELF_SELECT events). */
@Data
public class SelectTrackRequest {

    @NotNull(message = "Track ID is required")
    private Integer trackId;
}
