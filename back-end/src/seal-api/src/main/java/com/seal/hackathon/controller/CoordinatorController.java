package com.seal.hackathon.controller;

import com.seal.hackathon.dto.request.CreateGuestJudgeRequest;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.CoordinatorEventHistoryResponse;
import com.seal.hackathon.dto.response.JudgeAssignmentResponse;
import com.seal.hackathon.dto.response.UserResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.AssignableStaffService;
import com.seal.hackathon.service.CoordinatorEventHistoryService;
import com.seal.hackathon.service.JudgeAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Coordinator read-only lookups — EVENT_COORDINATOR only.
 *
 * After the platform split, the coordinator can no longer list global accounts
 * (that is /api/admin, admin-only). This endpoint gives the coordinator just the
 * approved STAFF pool they need to pick judges/mentors for their event.
 */
@RestController
@RequestMapping("/api/coordinator")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EVENT_COORDINATOR')")
public class CoordinatorController {

    private final AssignableStaffService assignableStaffService;
    private final CoordinatorEventHistoryService coordinatorEventHistoryService;
    private final JudgeAssignmentService judgeAssignmentService;

    @GetMapping("/staff")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getStaff() {
        return ResponseEntity.ok(ApiResponse.success("Staff retrieved.", assignableStaffService.listApprovedStaff()));
    }

    /** Full retrospective of one past event: teams/results/prizes + mentor/judge assignments. */
    @GetMapping("/history/{eventId}")
    public ResponseEntity<ApiResponse<CoordinatorEventHistoryResponse>> getEventHistory(@PathVariable Integer eventId) {
        return ResponseEntity.ok(ApiResponse.success("Event history retrieved.", coordinatorEventHistoryService.getEventHistory(eventId)));
    }

    /** Create a GUEST judge account and assign it to a round in one step. */
    @PostMapping("/guest-judges")
    public ResponseEntity<ApiResponse<JudgeAssignmentResponse>> createGuestJudge(
            @Valid @RequestBody CreateGuestJudgeRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Guest judge created and assigned.",
                        judgeAssignmentService.createGuestJudge(request, principal.getUserId())));
    }
}
