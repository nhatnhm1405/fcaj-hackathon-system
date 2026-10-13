package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Saves the criteria of a round as a reusable scoring-criteria template. */
@Data
public class CreateTemplateRequest {
    @NotBlank(message = "Template name is required")
    private String name;
    private String description;
}
