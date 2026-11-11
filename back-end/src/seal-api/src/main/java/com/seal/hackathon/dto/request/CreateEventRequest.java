package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateEventRequest {
    @NotBlank(message = "Event name is required")
    @Size(max = 255, message = "Event name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "Season is required")
    private String season; // SPRING | SUMMER | FALL

    @NotNull(message = "Year is required")
    @Min(value = 2026, message = "Year must be 2026 or later")
    @Max(value = 3000, message = "Year must not exceed 3000")
    private Integer year;

    private String description;

    @Size(max = 500, message = "Topic must not exceed 500 characters")
    private String topic;

    @NotNull(message = "Registration start date is required")
    private LocalDateTime registrationStart;

    @NotNull(message = "Registration end date is required")
    private LocalDateTime registrationEnd;

    @NotNull(message = "Start date is required")
    private LocalDateTime startDate;

    @NotNull(message = "End date is required")
    private LocalDateTime endDate;

    private String status; // defaults to DRAFT if null

    private String trackSelectionMode; // SELF_SELECT (default) | RANDOM
}
