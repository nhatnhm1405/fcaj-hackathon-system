package com.seal.hackathon.controller;

import com.seal.hackathon.dto.request.CreateReopenRequestRequest;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.ReopenRequestResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.ReopenRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Event reopen-request flow. Two audiences, guarded per method:
 *   - Coordinator files / checks a request under /api/events/{id}/reopen-requests
 *   - System Admin reviews the queue under /api/admin/reopen-requests
 *
 * No class-level @RequestMapping so each method owns its full path; URL-level
 * security is permitAll for /api/events/** and hasRole(SYSTEM_ADMIN) for
 * /api/admin/**, with these @PreAuthorize annotations as the real gate.
 */
@RestController
@RequiredArgsConstructor
public class ReopenRequestController {

    private final ReopenRequestService reopenRequestService;

    // ── Coordinator ───────────────────────────────────────────────────

    @PostMapping("/api/events/{eventId}/reopen-requests")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<ReopenRequestResponse>> create(
            @PathVariable Integer eventId,
            @Valid @RequestBody(required = false) CreateReopenRequestRequest request,
            Authentication authentication) {
        String reason = request != null ? request.getReason() : null;
        ReopenRequestResponse created =
                reopenRequestService.create(eventId, reason, currentUserId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reopen request sent to System Admin.", created));
    }

    // Latest request for an event (null data if none) — lets the UI show
    // "awaiting admin review" instead of a fresh request button.
    @GetMapping("/api/events/{eventId}/reopen-requests")
    @PreAuthorize("hasAnyRole('EVENT_COORDINATOR','SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<ReopenRequestResponse>> getForEvent(
            @PathVariable Integer eventId) {
        return ResponseEntity.ok(ApiResponse.success("Latest reopen request retrieved.",
                reopenRequestService.getForEvent(eventId)));
    }

    // ── System Admin ──────────────────────────────────────────────────

    @GetMapping("/api/admin/reopen-requests")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<List<ReopenRequestResponse>>> listPending() {
        return ResponseEntity.ok(ApiResponse.success("Pending reopen requests retrieved.",
                reopenRequestService.listPending()));
    }

    @PostMapping("/api/admin/reopen-requests/{requestId}/approve")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<ReopenRequestResponse>> approve(
            @PathVariable Integer requestId, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Reopen request approved; event is active again.",
                reopenRequestService.approve(requestId, currentUserId(authentication))));
    }

    @PostMapping("/api/admin/reopen-requests/{requestId}/reject")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<ReopenRequestResponse>> reject(
            @PathVariable Integer requestId, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Reopen request rejected.",
                reopenRequestService.reject(requestId, currentUserId(authentication))));
    }

    // ── Helper ────────────────────────────────────────────────────────

    private Integer currentUserId(Authentication authentication) {
        return ((UserPrincipal) authentication.getPrincipal()).getUserId();
    }
}
