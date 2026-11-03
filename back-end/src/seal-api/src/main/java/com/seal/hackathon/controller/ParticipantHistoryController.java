package com.seal.hackathon.controller;

import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.service.ParticipantHistorySnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ParticipantHistoryController {

    private final ParticipantHistorySnapshotService participantHistorySnapshotService;

    // One-time maintenance for events that completed before this feature
    // existed (so they have no snapshot yet) — re-runnable safely (upsert),
    // no FE button, triggered by hand when needed.
    @PostMapping("/api/admin/participant-history/backfill")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> backfill() {
        int eventsProcessed = participantHistorySnapshotService.backfillCompletedEvents();
        return ResponseEntity.ok(ApiResponse.success(
                "Backfilled participant history for " + eventsProcessed + " completed event(s).",
                eventsProcessed));
    }
}
