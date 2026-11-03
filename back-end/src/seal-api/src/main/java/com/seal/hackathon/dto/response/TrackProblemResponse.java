package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * The "đề thi" (problem statement) status of a single track. Drives both the
 * Coordinator management table (one row per track) and the participant's
 * "download problem" panel. The file itself is never inlined — participants
 * fetch it via GET /api/events/{e}/tracks/{t}/problem/download.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackProblemResponse {
    private Integer trackId;
    private String trackName;

    /** Whether a problem file has been uploaded for this track. */
    private boolean hasProblem;

    /** Original file name (null when hasProblem = false). */
    private String fileName;

    /** File size in bytes (null when hasProblem = false). */
    private Long fileSize;

    /** MIME type of the stored file (null when hasProblem = false). */
    private String contentType;

    /** Whether the problem is published to the track's participants. */
    private boolean released;

    private LocalDateTime uploadedAt;
    private LocalDateTime releasedAt;
}
