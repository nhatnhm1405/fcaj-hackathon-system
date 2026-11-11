package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateEventRequest {
    @Size(max = 255, message = "Event name must not exceed 255 characters")
    private String name;
    private String season;
    @Min(value = 2026, message = "Year must be 2026 or later")
    @Max(value = 3000, message = "Year must not exceed 3000")
    private Integer year;
    private String description;
    @Size(max = 500, message = "Topic must not exceed 500 characters")
    private String topic;
    private LocalDateTime registrationStart;
    private LocalDateTime registrationEnd;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String status; // DRAFT | OPEN | SETUP | IN_PROGRESS | COMPLETED | CANCELLED
    private String trackSelectionMode; // SELF_SELECT | RANDOM (changeable only before SETUP)
}
