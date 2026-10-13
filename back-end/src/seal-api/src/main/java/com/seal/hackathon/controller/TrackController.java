package com.seal.hackathon.controller;

import com.seal.hackathon.dto.request.CreateTrackRequest;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.TrackResponse;
import com.seal.hackathon.service.TrackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events/{eventId}/tracks")
@RequiredArgsConstructor
public class TrackController {

    private final TrackService trackService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TrackResponse>>> getTracks(@PathVariable Integer eventId) {
        return ResponseEntity.ok(ApiResponse.success("Tracks retrieved successfully.",
                trackService.getTracksByEvent(eventId)));
    }

    @GetMapping("/{trackId}")
    public ResponseEntity<ApiResponse<TrackResponse>> getTrackById(
            @PathVariable Integer eventId,
            @PathVariable Integer trackId) {
        return ResponseEntity.ok(ApiResponse.success("Track retrieved successfully.",
                trackService.getTrackById(eventId, trackId)));
    }

    @PostMapping
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<TrackResponse>> createTrack(
            @PathVariable Integer eventId,
            @Valid @RequestBody CreateTrackRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Track created successfully.",
                trackService.createTrack(eventId, request)));
    }

    @PutMapping("/{trackId}")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<TrackResponse>> updateTrack(
            @PathVariable Integer eventId,
            @PathVariable Integer trackId,
            @RequestBody CreateTrackRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Track updated successfully.",
                trackService.updateTrack(eventId, trackId, request)));
    }

    @DeleteMapping("/{trackId}")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<Void>> deleteTrack(
            @PathVariable Integer eventId,
            @PathVariable Integer trackId) {
        trackService.deleteTrack(eventId, trackId);
        return ResponseEntity.ok(ApiResponse.success("Track deleted successfully."));
    }
}
