package com.seal.hackathon.controller;

import com.seal.hackathon.dto.request.CreateSupportRequestRequest;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.MentorContactResponse;
import com.seal.hackathon.dto.response.SupportRequestResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.MentorSupportRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Participant-facing mentor-support endpoints: see the track mentor, raise a
 * request (leader), view the team's requests, and cancel an open one (leader).
 */
@RestController
@RequestMapping("/api/support-requests")
@RequiredArgsConstructor
public class SupportRequestController {

    private final MentorSupportRequestService service;

    @GetMapping("/my-mentors")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<List<MentorContactResponse>>> myMentors(Authentication auth) {
        Integer userId = ((UserPrincipal) auth.getPrincipal()).getUserId();
        return ResponseEntity.ok(ApiResponse.success("Track mentor retrieved.", service.getMyTeamMentors(userId)));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<List<SupportRequestResponse>>> mine(Authentication auth) {
        Integer userId = ((UserPrincipal) auth.getPrincipal()).getUserId();
        return ResponseEntity.ok(ApiResponse.success("Support requests retrieved.", service.getMyTeamRequests(userId)));
    }

    @PostMapping
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<SupportRequestResponse>> create(
            @Valid @RequestBody CreateSupportRequestRequest request, Authentication auth) {
        Integer userId = ((UserPrincipal) auth.getPrincipal()).getUserId();
        return ResponseEntity.ok(ApiResponse.success("Support request sent to your mentor.",
                service.createRequest(userId, request)));
    }

    @PutMapping("/{requestId}/cancel")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<SupportRequestResponse>> cancel(
            @PathVariable Integer requestId, Authentication auth) {
        Integer userId = ((UserPrincipal) auth.getPrincipal()).getUserId();
        return ResponseEntity.ok(ApiResponse.success("Support request cancelled.",
                service.cancelRequest(userId, requestId)));
    }
}
