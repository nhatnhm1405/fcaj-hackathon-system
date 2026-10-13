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
public class ReopenRequestResponse {
    private Integer requestId;
    private Integer eventId;
    private String eventName;
    private Integer requestedById;
    private String requesterName;
    private String requesterEmail;
    private String reason;
    private String status;          // PENDING | APPROVED | REJECTED
    private Integer resolvedById;
    private String resolverName;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
