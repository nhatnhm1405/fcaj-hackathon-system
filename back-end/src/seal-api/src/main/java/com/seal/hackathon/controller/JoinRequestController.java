package com.seal.hackathon.controller;

import com.seal.hackathon.dto.request.CreateJoinRequestRequest;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.JoinRequestResponse;
import com.seal.hackathon.dto.response.JoinableTeamListResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.JoinRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/join-requests")
@RequiredArgsConstructor
public class JoinRequestController {

    private final JoinRequestService joinRequestService;

    @PostMapping("/teams/{teamId}")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<JoinRequestResponse>> createJoinRequest(
            @PathVariable Integer teamId,
            @Valid @RequestBody(required = false) CreateJoinRequestRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Join request sent.",
                joinRequestService.createJoinRequest(principal.getUserId(), teamId, request)));
    }

    @GetMapping("/joinable-teams")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<JoinableTeamListResponse>> getJoinableTeams(
            @RequestParam(required = false) Integer eventId,
            @RequestParam(required = false) String query,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Joinable teams retrieved.",
                joinRequestService.getJoinableTeams(principal.getUserId(), eventId, query)));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<List<JoinRequestResponse>>> getMyRequests(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("My join requests retrieved.",
                joinRequestService.getMyRequests(principal.getUserId())));
    }

    @DeleteMapping("/{requestId}")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<Void>> withdrawRequest(
            @PathVariable Integer requestId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        joinRequestService.withdrawRequest(principal.getUserId(), requestId);
        return ResponseEntity.ok(ApiResponse.success("Join request withdrawn.", null));
    }

    @GetMapping("/teams/{teamId}")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<List<JoinRequestResponse>>> getPendingRequestsForTeam(
            @PathVariable Integer teamId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Pending join requests retrieved.",
                joinRequestService.getPendingRequestsForTeam(principal.getUserId(), teamId)));
    }

    @PutMapping("/{requestId}/accept")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<JoinRequestResponse>> acceptRequest(
            @PathVariable Integer requestId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Join request accepted.",
                joinRequestService.acceptRequest(principal.getUserId(), requestId)));
    }

    @PutMapping("/{requestId}/decline")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<JoinRequestResponse>> declineRequest(
            @PathVariable Integer requestId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Join request declined.",
                joinRequestService.declineRequest(principal.getUserId(), requestId)));
    }
}
