package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateTeamRejoinRequestRequest {
    @NotNull(message = "Event ID is required")
    private Integer eventId;
}
