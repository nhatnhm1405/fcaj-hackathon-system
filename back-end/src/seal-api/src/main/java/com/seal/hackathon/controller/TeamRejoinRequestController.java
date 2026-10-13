package com.seal.hackathon.controller;

import com.seal.hackathon.dto.request.CreateTeamRejoinRequestRequest;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.TeamRejoinRequestResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.TeamRejoinRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TeamRejoinRequestController {

    private final TeamRejoinRequestService rejoinRequestService;

    @PostMapping("/api/teams/{teamId}/rejoin-requests")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<TeamRejoinRequestResponse>> requestRejoin(
            @PathVariable Integer teamId,
            @Valid @RequestBody CreateTeamRejoinRequestRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        TeamRejoinRequestResponse response = rejoinRequestService
                .requestRejoin(principal.getUserId(), teamId, request.getEventId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Rejoin request submitted.", response));
    }

    // Re-entering a season is a competition action, so the Event Coordinator
    // (not the System Admin) reviews these — same reasoning as
    // ParticipationAccessRequestController.
    @GetMapping("/api/coordinator/team-rejoin-requests")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<TeamRejoinRequestResponse>>> listPending() {
        return ResponseEntity.ok(ApiResponse.success("Pending team rejoin requests retrieved.",
                rejoinRequestService.listPending()));
    }

    @PostMapping("/api/coordinator/team-rejoin-requests/{requestId}/approve")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<TeamRejoinRequestResponse>> approve(
            @PathVariable Integer requestId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Team rejoin approved.",
                rejoinRequestService.approve(requestId, principal.getUserId())));
    }

    @PostMapping("/api/coordinator/team-rejoin-requests/{requestId}/reject")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<TeamRejoinRequestResponse>> reject(
            @PathVariable Integer requestId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Team rejoin rejected.",
                rejoinRequestService.reject(requestId, principal.getUserId())));
    }
}
