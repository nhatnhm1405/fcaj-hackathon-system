package com.seal.hackathon.controller;

import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.TrackProblemResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.TrackProblemService;
import com.seal.hackathon.service.TrackProblemService.ProblemDownload;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * "Đề thi" (problem statement) endpoints for tracks. The Coordinator uploads,
 * releases/retracts and removes the single problem file per track; participants of
 * an approved team in the track download a released problem. File access is gated
 * in {@link TrackProblemService}, never served from the public /uploads/** path.
 */
@RestController
@RequestMapping("/api/events/{eventId}")
@RequiredArgsConstructor
public class TrackProblemController {

    private static final Set<String> PRIVILEGED_ROLES =
            Set.of("ROLE_EVENT_COORDINATOR", "ROLE_SYSTEM_ADMIN");

    private final TrackProblemService trackProblemService;

    // ── Coordinator management ────────────────────────────────────────────────

    @PostMapping("/tracks/{trackId}/problem")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<TrackProblemResponse>> uploadProblem(
            @PathVariable Integer eventId,
            @PathVariable Integer trackId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Problem uploaded successfully.",
                trackProblemService.uploadProblem(principal.getUserId(), eventId, trackId, file)));
    }

    @PutMapping("/tracks/{trackId}/problem/release")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<TrackProblemResponse>> releaseProblem(
            @PathVariable Integer eventId,
            @PathVariable Integer trackId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Problem released to participants.",
                trackProblemService.releaseProblem(principal.getUserId(), eventId, trackId)));
    }

    @PutMapping("/tracks/{trackId}/problem/retract")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<TrackProblemResponse>> retractProblem(
            @PathVariable Integer eventId,
            @PathVariable Integer trackId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Problem retracted from participants.",
                trackProblemService.retractProblem(principal.getUserId(), eventId, trackId)));
    }

    @DeleteMapping("/tracks/{trackId}/problem")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<Void>> removeProblem(
            @PathVariable Integer eventId,
            @PathVariable Integer trackId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        trackProblemService.removeProblem(principal.getUserId(), eventId, trackId);
        return ResponseEntity.ok(ApiResponse.success("Problem removed successfully."));
    }

    @GetMapping("/problems")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<TrackProblemResponse>>> listProblems(
            @PathVariable Integer eventId) {
        return ResponseEntity.ok(ApiResponse.success("Problems retrieved successfully.",
                trackProblemService.listProblemsForEvent(eventId)));
    }

    // ── Shared (Coordinator preview · participant of the track) ───────────────

    @GetMapping("/tracks/{trackId}/problem")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TrackProblemResponse>> getProblem(
            @PathVariable Integer eventId,
            @PathVariable Integer trackId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Problem retrieved successfully.",
                trackProblemService.getProblem(eventId, trackId,
                        principal.getUserId(), isPrivileged(authentication))));
    }

    @GetMapping("/tracks/{trackId}/problem/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadProblem(
            @PathVariable Integer eventId,
            @PathVariable Integer trackId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        ProblemDownload download = trackProblemService.downloadProblem(
                eventId, trackId, principal.getUserId(), isPrivileged(authentication));

        String encodedName = URLEncoder.encode(
                        download.fileName() != null ? download.fileName() : "problem",
                        StandardCharsets.UTF_8)
                .replace("+", "%20");
        String disposition = "attachment; filename=\"" + asciiFallback(download.fileName())
                + "\"; filename*=UTF-8''" + encodedName;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.size())
                .body(download.resource());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isPrivileged(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(PRIVILEGED_ROLES::contains);
    }

    /** Best-effort ASCII filename for the legacy `filename=` part (non-ASCII → "problem"). */
    private String asciiFallback(String name) {
        if (name == null || name.isBlank()) {
            return "problem";
        }
        String ascii = name.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "_");
        return ascii.isBlank() ? "problem" : ascii;
    }
}
