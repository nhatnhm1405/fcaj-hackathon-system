package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrizeResponse {
    private Integer prizeId;
    private Integer eventId;
    private String name;
    private String description;
    private Integer rankPosition;
    private Integer teamId;
    private String teamName;
    private String teamTrackName;   // the winning team's track (display only; prize itself is event-wide)
    private BigDecimal finalScore;  // the team's total score in the final round, if available
    private LocalDateTime awardedAt;
    private boolean announced;      // awardedAt != null
}
