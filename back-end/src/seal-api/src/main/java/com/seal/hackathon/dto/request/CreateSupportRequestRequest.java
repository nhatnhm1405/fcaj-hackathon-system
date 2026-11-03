package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Team leader asks its track mentor for help. Category is one of
 * RULES | TECHNICAL | DIRECTION | OTHER; description briefly says what is needed.
 */
@Data
public class CreateSupportRequestRequest {

    @NotBlank(message = "Category is required")
    private String category;

    @NotBlank(message = "Please describe what you need help with")
    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;
}
