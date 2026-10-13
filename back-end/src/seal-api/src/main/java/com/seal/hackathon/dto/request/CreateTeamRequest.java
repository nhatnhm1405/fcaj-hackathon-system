package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTeamRequest {

    @NotNull(message = "Event ID is required")
    private Integer eventId;

    // No track at registration — it is assigned during the SETUP phase (leader
    // self-selects or coordinator draws, depending on the event's mode).

    @NotBlank(message = "Team name is required")
    @Size(max = 255, message = "Team name must not exceed 255 characters")
    private String name;

    private String description;
}
