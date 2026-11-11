package com.seal.hackathon.controller;

import com.seal.hackathon.dto.request.CoordinatorAnnouncementRequest;
import com.seal.hackathon.dto.request.MentorAnnouncementRequest;
import com.seal.hackathon.dto.response.AnnouncementResponse;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Announcement endpoints for Mentor (track-scoped) and Coordinator (event-scoped),
 * plus each author's own "sent history".
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    // ── Mentor ────────────────────────────────────────────────────────

    @PostMapping("/mentor/announcements")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> createMentorAnnouncement(
            @Valid @RequestBody MentorAnnouncementRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        AnnouncementResponse res = announcementService.createMentorAnnouncement(
                principal.getUserId(), request.getTrackId(), request.getTitle(),
                request.getContent(), request.getLinkUrl());
        return ResponseEntity.ok(ApiResponse.success(
                "Announcement sent to " + res.getRecipientCount() + " participant(s).", res));
    }

    @GetMapping("/mentor/announcements")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<ApiResponse<List<AnnouncementResponse>>> listMentorAnnouncements(
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Announcement history retrieved.",
                announcementService.listBySender(principal.getUserId())));
    }

    // ── Coordinator ───────────────────────────────────────────────────

    @PostMapping("/coordinator/announcements")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> createCoordinatorAnnouncement(
            @Valid @RequestBody CoordinatorAnnouncementRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        AnnouncementResponse res = announcementService.createCoordinatorAnnouncement(
                principal.getUserId(), request.getEventId(), request.getAudience(),
                request.getTitle(), request.getContent(), request.getLinkUrl());
        return ResponseEntity.ok(ApiResponse.success(
                "Announcement sent to " + res.getRecipientCount() + " participant(s).", res));
    }

    @GetMapping("/coordinator/announcements")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<AnnouncementResponse>>> listCoordinatorAnnouncements(
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Announcement history retrieved.",
                announcementService.listBySender(principal.getUserId())));
    }
}
