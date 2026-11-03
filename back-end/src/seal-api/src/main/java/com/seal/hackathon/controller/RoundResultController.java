package com.seal.hackathon.controller;

import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.RoundResultResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.RoundResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events/{eventId}/rounds/{roundId}/results")
@RequiredArgsConstructor
public class RoundResultController {

    private final RoundResultService roundResultService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoundResultResponse>>> getPublishedResults(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId) {
        return ResponseEntity.ok(ApiResponse.success("Results retrieved successfully.",
                roundResultService.getPublishedResults(eventId, roundId)));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<RoundResultResponse>>> getAllResults(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId) {
        return ResponseEntity.ok(ApiResponse.success("All results retrieved successfully.",
                roundResultService.getAllResults(eventId, roundId)));
    }

    @PostMapping("/finalize")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<RoundResultResponse>>> finalizeRound(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Rankings calculated successfully.",
                roundResultService.finalizeRound(eventId, roundId, principal.getUserId())));
    }

    @PostMapping("/publish")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<RoundResultResponse>>> publishResults(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId) {
        return ResponseEntity.ok(ApiResponse.success("Results published successfully.",
                roundResultService.publishResults(eventId, roundId)));
    }
}
