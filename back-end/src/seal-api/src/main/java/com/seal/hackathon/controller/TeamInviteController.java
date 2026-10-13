package com.seal.hackathon.controller;

import com.seal.hackathon.dto.request.CreateInviteRequest;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.TeamInviteResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.TeamInviteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invites")
@RequiredArgsConstructor
public class TeamInviteController {

    private final TeamInviteService teamInviteService;

    @PostMapping("/teams/{teamId}")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<TeamInviteResponse>> createInvite(
            @PathVariable Integer teamId,
            @Valid @RequestBody CreateInviteRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Invitation sent successfully.",
                teamInviteService.createInvite(principal.getUserId(), teamId, request)));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<List<TeamInviteResponse>>> getPendingInvites(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Pending invitations retrieved.",
                teamInviteService.getPendingInvites(principal.getUserId())));
    }

    @PutMapping("/{inviteId}/accept")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<TeamInviteResponse>> acceptInvite(
            @PathVariable Integer inviteId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Invitation accepted. You have joined the team.",
                teamInviteService.acceptInvite(principal.getUserId(), inviteId)));
    }

    @PutMapping("/{inviteId}/decline")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<TeamInviteResponse>> declineInvite(
            @PathVariable Integer inviteId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Invitation declined.",
                teamInviteService.declineInvite(principal.getUserId(), inviteId)));
    }
}
