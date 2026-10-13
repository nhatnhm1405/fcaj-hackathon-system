package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTrackRequest {
    @NotBlank(message = "Track name is required")
    @Size(max = 255, message = "Track name must not exceed 255 characters")
    private String name;

    @Size(max = 2000, message = "Track description must not exceed 2000 characters")
    private String description;
}
