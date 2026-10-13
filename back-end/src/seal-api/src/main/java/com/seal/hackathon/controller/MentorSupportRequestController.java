package com.seal.hackathon.controller;

import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.SupportRequestResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.MentorSupportRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Mentor-facing mentor-support endpoints: list requests across the mentor's
 * assigned tracks and mark one resolved after helping the team in person.
 */
@RestController
@RequestMapping("/api/mentor/support-requests")
@RequiredArgsConstructor
public class MentorSupportRequestController {

    private final MentorSupportRequestService service;

    @GetMapping
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<ApiResponse<List<SupportRequestResponse>>> list(Authentication auth) {
        Integer userId = ((UserPrincipal) auth.getPrincipal()).getUserId();
        return ResponseEntity.ok(ApiResponse.success("Support requests retrieved.", service.listForMentor(userId)));
    }

    @PutMapping("/{requestId}/resolve")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<ApiResponse<SupportRequestResponse>> resolve(
            @PathVariable Integer requestId, Authentication auth) {
        Integer userId = ((UserPrincipal) auth.getPrincipal()).getUserId();
        return ResponseEntity.ok(ApiResponse.success("Support request marked resolved.",
                service.resolveRequest(userId, requestId)));
    }
}
