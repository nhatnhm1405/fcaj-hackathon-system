package com.seal.hackathon.controller;

import com.seal.hackathon.dto.request.ExtendTimerRequest;
import com.seal.hackathon.dto.request.StartTimerRequest;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.RoundTimerResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.RoundTimerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Live countdown for a round phase ({@code CONTEST} gates submission,
 * {@code JUDGING} gates scoring). The Coordinator starts/pauses/resumes/extends/
 * stops it; any authenticated user reads the state (which also materialises
 * milestone reminders for the phase's audience). See {@link RoundTimerService}.
 */
@RestController
@RequestMapping("/api/events/{eventId}/rounds/{roundId}/timer/{phase}")
@RequiredArgsConstructor
public class RoundTimerController {

    private final RoundTimerService timerService;

    @PostMapping("/start")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<RoundTimerResponse>> start(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId,
            @PathVariable String phase,
            @Valid @RequestBody StartTimerRequest request,
            Authentication authentication) {
        Integer actorId = ((UserPrincipal) authentication.getPrincipal()).getUserId();
        return ResponseEntity.ok(ApiResponse.success("Timer started.",
                timerService.start(actorId, eventId, roundId, phase, request)));
    }

    @PostMapping("/pause")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<RoundTimerResponse>> pause(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId,
            @PathVariable String phase,
            Authentication authentication) {
        Integer actorId = ((UserPrincipal) authentication.getPrincipal()).getUserId();
        return ResponseEntity.ok(ApiResponse.success("Timer paused.",
                timerService.pause(actorId, eventId, roundId, phase)));
    }

    @PostMapping("/resume")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<RoundTimerResponse>> resume(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId,
            @PathVariable String phase,
            Authentication authentication) {
        Integer actorId = ((UserPrincipal) authentication.getPrincipal()).getUserId();
        return ResponseEntity.ok(ApiResponse.success("Timer resumed.",
                timerService.resume(actorId, eventId, roundId, phase)));
    }

    @PostMapping("/extend")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<RoundTimerResponse>> extend(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId,
            @PathVariable String phase,
            @Valid @RequestBody ExtendTimerRequest request,
            Authentication authentication) {
        Integer actorId = ((UserPrincipal) authentication.getPrincipal()).getUserId();
        return ResponseEntity.ok(ApiResponse.success("Timer extended.",
                timerService.extend(actorId, eventId, roundId, phase, request)));
    }

    @PostMapping("/stop")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<RoundTimerResponse>> stop(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId,
            @PathVariable String phase,
            Authentication authentication) {
        Integer actorId = ((UserPrincipal) authentication.getPrincipal()).getUserId();
        return ResponseEntity.ok(ApiResponse.success("Timer stopped.",
                timerService.stop(actorId, eventId, roundId, phase)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RoundTimerResponse>> getState(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId,
            @PathVariable String phase) {
        return ResponseEntity.ok(ApiResponse.success("Timer state retrieved.",
                timerService.getState(eventId, roundId, phase)));
    }
}
