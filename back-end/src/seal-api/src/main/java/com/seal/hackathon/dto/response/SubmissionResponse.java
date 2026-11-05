package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponse {
    private Integer submissionId;
    private Integer teamId;
    private String teamName;
    private Integer roundId;
    private String roundName;
    private String repoUrl;
    private String demoUrl;
    private String slideUrl;
    private String description;
    private LocalDateTime submittedAt;
    private Integer submittedById;
    private String submittedByName;
    private String status;
}
