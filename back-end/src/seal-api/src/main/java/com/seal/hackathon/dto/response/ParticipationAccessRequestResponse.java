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
public class ParticipationAccessRequestResponse {
    private Integer requestId;
    private Integer userId;
    private String email;
    private String fullName;
    private String userType;
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime resolvedAt;
    private Integer resolvedBy;
}
