package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Current state of a round timer. {@code serverNow} lets the client correct for
 * clock skew: remaining = endsAt - (clientNow + (serverNow - clientNow)). The
 * client then ticks locally and only re-syncs occasionally.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundTimerResponse {

    private Integer roundId;
    private String phase;                 // CONTEST | JUDGING
    private String status;                // IDLE | RUNNING | PAUSED | STOPPED | EXPIRED
    private Integer durationSeconds;
    private LocalDateTime startedAt;
    private LocalDateTime endsAt;
    private Long remainingSeconds;        // effective seconds left (0 when not running)
    private LocalDateTime serverNow;      // authoritative reference time
    private List<Integer> milestoneMinutes;
    private Boolean notifyAtHalf;
}
