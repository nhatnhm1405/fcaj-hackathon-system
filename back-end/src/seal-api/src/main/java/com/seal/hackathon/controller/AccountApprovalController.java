package com.seal.hackathon.controller;

import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.UserResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Account approval endpoints — only accessible by EVENT_COORDINATOR.
 * Security is enforced both at the path level (SecurityConfig) and method level (@PreAuthorize).
 */
@RestController
@RequestMapping("/api/account-approvals")
@RequiredArgsConstructor
public class AccountApprovalController {

    private final AccountService approvalService;

    /**
     * GET /api/account-approvals/pending
     * Lists all users who have registered but not yet been approved or rejected.
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getPendingApprovals() {
        List<UserResponse> pending = approvalService.getPendingApprovals();
        return ResponseEntity.ok(ApiResponse.success("Pending approvals retrieved.", pending));
    }

    /**
     * GET /api/account-approvals/participants
     * Lists every active, approved participant (FPT_STUDENT / EXTERNAL_STUDENT).
     */
    @GetMapping("/participants")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getActiveParticipants() {
        return ResponseEntity.ok(ApiResponse.success("Active participants retrieved.", approvalService.getActiveParticipants()));
    }

    /**
     * GET /api/account-approvals/staff
     * Lists every active, approved staff account eligible to serve as judge/mentor
     * (excludes SYSTEM_ADMIN/EVENT_COORDINATOR).
     */
    @GetMapping("/staff")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getActiveJudgeMentorStaff() {
        return ResponseEntity.ok(ApiResponse.success("Active judge/mentor staff retrieved.", approvalService.getActiveJudgeMentorStaff()));
    }

    /**
     * PUT /api/account-approvals/{userId}/approve
     * Sets is_approved = true so the user can log in.
     */
    @PutMapping("/{userId}/approve")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<UserResponse>> approveUser(
            @PathVariable Integer userId, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UserResponse user = approvalService.approveUser(userId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("User approved successfully.", user));
    }

    /**
     * PUT /api/account-approvals/{userId}/reject
     * Permanently deletes a still-pending account.
     */
    @PutMapping("/{userId}/reject")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<UserResponse>> rejectUser(
            @PathVariable Integer userId,
            @RequestParam(required = false) String reason,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UserResponse user = approvalService.rejectUser(userId, principal.getUserId(), reason);
        return ResponseEntity.ok(ApiResponse.success("User rejected.", user));
    }
}
