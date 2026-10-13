package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitRequest {
    @NotNull(message = "Round ID is required")
    private Integer roundId;

    @NotBlank(message = "Repository URL is required")
    @Size(max = 500, message = "Repository URL must be at most 500 characters")
    @Pattern(regexp = "^https?://\\S+$", message = "Repository URL must start with http:// or https://")
    private String repoUrl;

    @Size(max = 500, message = "Demo URL must be at most 500 characters")
    @Pattern(regexp = "^\\s*$|^https?://\\S+$", message = "Demo URL must start with http:// or https://")
    private String demoUrl;

    @Size(max = 500, message = "Slide URL must be at most 500 characters")
    @Pattern(regexp = "^\\s*$|^https?://\\S+$", message = "Slide URL must start with http:// or https://")
    private String slideUrl;

    @Size(max = 5000, message = "Description must be at most 5000 characters")
    private String description;
}
