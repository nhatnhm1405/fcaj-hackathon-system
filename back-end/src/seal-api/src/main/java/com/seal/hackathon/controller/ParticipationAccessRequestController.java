package com.seal.hackathon.controller;

import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.ParticipationAccessRequestResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.ParticipationAccessRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ParticipationAccessRequestController {

    private final ParticipationAccessRequestService requestService;

    @PostMapping("/api/participation-requests")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<ParticipationAccessRequestResponse>> requestAccess(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        ParticipationAccessRequestResponse response = requestService.requestAccess(principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Participation access request submitted.", response));
    }

    // Reactivating a participant for the current season is a competition action,
    // so the Event Coordinator (not the System Admin) reviews these requests.
    @GetMapping("/api/coordinator/participation-requests")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<ParticipationAccessRequestResponse>>> listPending() {
        return ResponseEntity.ok(ApiResponse.success("Pending participation access requests retrieved.",
                requestService.listPending()));
    }

    @PostMapping("/api/coordinator/participation-requests/{requestId}/approve")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<ParticipationAccessRequestResponse>> approve(
            @PathVariable Integer requestId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Participation access approved.",
                requestService.approve(requestId, principal.getUserId())));
    }

    @PostMapping("/api/coordinator/participation-requests/{requestId}/reject")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<ParticipationAccessRequestResponse>> reject(
            @PathVariable Integer requestId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Participation access rejected.",
                requestService.reject(requestId, principal.getUserId())));
    }
}
