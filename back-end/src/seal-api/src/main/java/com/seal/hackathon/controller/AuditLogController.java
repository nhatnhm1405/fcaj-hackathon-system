package com.seal.hackathon.controller;

import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.AuditLogResponse;
import com.seal.hackathon.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only audit trail for a single event. Visible to the System Admin and to the
 * event's Coordinator (AuditLog = competition business actions, unlike the
 * SYSTEM_ADMIN-only SystemLog). Writes happen in the services that perform the
 * action (see {@link com.seal.hackathon.service.AuditLogService#record}).
 */
@RestController
@RequestMapping("/api/events/{eventId}/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('EVENT_COORDINATOR','SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getEventAuditLogs(
            @PathVariable Integer eventId) {
        return ResponseEntity.ok(ApiResponse.success("Audit logs retrieved successfully.",
                auditLogService.getLogsForEvent(eventId)));
    }
}
