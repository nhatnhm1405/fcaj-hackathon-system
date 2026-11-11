package com.seal.hackathon.controller;

import com.seal.hackathon.dto.request.CreateUserRequest;
import com.seal.hackathon.dto.request.GrantRoleRequest;
import com.seal.hackathon.dto.request.UpdateUserRequest;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.AdminScoreDistributionResponse;
import com.seal.hackathon.dto.response.SystemLogResponse;
import com.seal.hackathon.dto.response.UserEventRoleResponse;
import com.seal.hackathon.dto.response.UserResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.AdminEventCsvExportService;
import com.seal.hackathon.service.AdminScoreDistributionService;
import com.seal.hackathon.service.AdminService;
import com.seal.hackathon.service.SystemLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Platform administration endpoints — SYSTEM_ADMIN only.
 *
 * The System Admin runs the PLATFORM (global accounts, role grants, system log).
 * Competition operations (events, rounds, scoring, approvals, judge/mentor
 * assignment) live under /api/coordinator and /api/events for EVENT_COORDINATOR.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final SystemLogService systemLogService;
    private final AdminEventCsvExportService eventCsvExportService;
    private final AdminScoreDistributionService scoreDistributionService;

    // ── Users ─────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success("Users retrieved.", adminService.getAllUsers()));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success("User retrieved.", adminService.getUserById(id)));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            Authentication authentication) {
        UserResponse user = adminService.createUser(request, currentUserId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully.", user));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("User updated.",
                adminService.updateUser(id, request, currentUserId(authentication))));
    }

    // Directly (re)activate or deactivate an account — used to bring a guest judge
    // (or any inactive account) back for a new season.
    @PostMapping("/users/{id}/activate")
    public ResponseEntity<ApiResponse<UserResponse>> activateUser(
            @PathVariable Integer id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("User activated.",
                adminService.setUserActive(id, true, currentUserId(authentication))));
    }

    @PostMapping("/users/{id}/deactivate")
    public ResponseEntity<ApiResponse<UserResponse>> deactivateUser(
            @PathVariable Integer id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("User deactivated.",
                adminService.setUserActive(id, false, currentUserId(authentication))));
    }

    // ── Role grants ───────────────────────────────────────────────────

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<UserEventRoleResponse>>> getAllRoleGrants() {
        return ResponseEntity.ok(ApiResponse.success("Role grants retrieved.", adminService.getAllRoleGrants()));
    }

    @PostMapping("/roles/grant")
    public ResponseEntity<ApiResponse<UserResponse>> grantRole(
            @Valid @RequestBody GrantRoleRequest request,
            Authentication authentication) {
        UserResponse user = adminService.grantRole(request, currentUserId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Role granted successfully.", user));
    }

    @DeleteMapping("/roles/revoke")
    public ResponseEntity<ApiResponse<UserResponse>> revokeRole(
            @Valid @RequestBody GrantRoleRequest request,
            Authentication authentication) {
        UserResponse user = adminService.revokeRole(request, currentUserId(authentication));
        return ResponseEntity.ok(ApiResponse.success("Role revoked successfully.", user));
    }

    // ── System log ────────────────────────────────────────────────────

    @GetMapping("/system-logs")
    public ResponseEntity<ApiResponse<List<SystemLogResponse>>> getSystemLogs() {
        return ResponseEntity.ok(ApiResponse.success("System logs retrieved.", systemLogService.getAllLogs()));
    }

    // ── Event export ──────────────────────────────────────────────────

    @GetMapping(value = "/events/{eventId}/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCompletedEventCsv(@PathVariable Integer eventId) {
        AdminEventCsvExportService.ExportedCsv csv = eventCsvExportService.exportCompletedEvent(eventId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(csv.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .contentLength(csv.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(csv.content());
    }

    @GetMapping("/events/{eventId}/score-distribution")
    public ResponseEntity<ApiResponse<AdminScoreDistributionResponse>> getEventScoreDistribution(
            @PathVariable Integer eventId,
            @RequestParam(required = false) Integer roundId,
            @RequestParam(required = false) Integer trackId,
            @RequestParam(defaultValue = "JUDGE_EVALUATION") String metric,
            @RequestParam(required = false) Integer criteriaId) {
        return ResponseEntity.ok(ApiResponse.success("Score distribution retrieved.",
                scoreDistributionService.getDistribution(
                        eventId, roundId, trackId, metric, criteriaId)));
    }

    // ── Helper ────────────────────────────────────────────────────────

    private Integer currentUserId(Authentication authentication) {
        return ((UserPrincipal) authentication.getPrincipal()).getUserId();
    }
}
