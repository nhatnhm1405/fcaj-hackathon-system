package com.seal.hackathon.controller;

import com.seal.hackathon.dto.request.CreateRoundRequest;
import com.seal.hackathon.dto.request.UpdateRoundRequest;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.RoundDetailResponse;
import com.seal.hackathon.dto.response.RoundResponse;
import com.seal.hackathon.service.RoundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events/{eventId}/rounds")
@RequiredArgsConstructor
public class RoundController {

    private final RoundService roundService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoundResponse>>> getRounds(@PathVariable Integer eventId) {
        return ResponseEntity.ok(ApiResponse.success("Rounds retrieved successfully.",
                roundService.getRoundsByEvent(eventId)));
    }

    @GetMapping("/{roundId}")
    public ResponseEntity<ApiResponse<RoundDetailResponse>> getRoundDetail(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId) {
        return ResponseEntity.ok(ApiResponse.success("Round detail retrieved successfully.",
                roundService.getRoundDetail(eventId, roundId)));
    }

    @PostMapping
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<RoundResponse>> createRound(
            @PathVariable Integer eventId,
            @Valid @RequestBody CreateRoundRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Round created successfully.",
                roundService.createRound(eventId, request)));
    }

    @PutMapping("/{roundId}")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<RoundResponse>> updateRound(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId,
            @Valid @RequestBody UpdateRoundRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Round updated successfully.",
                roundService.updateRound(eventId, roundId, request)));
    }

    @DeleteMapping("/{roundId}")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<Void>> deleteRound(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId) {
        roundService.deleteRound(eventId, roundId);
        return ResponseEntity.ok(ApiResponse.success("Round deleted successfully."));
    }
}
