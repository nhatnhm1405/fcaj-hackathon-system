package com.seal.hackathon.controller;

import com.seal.hackathon.dto.request.SubmitRequest;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.SubmissionEligibilityResponse;
import com.seal.hackathon.dto.response.SubmissionResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<SubmissionResponse>> submit(
            @Valid @RequestBody SubmitRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Submission saved successfully.",
                submissionService.submit(principal.getUserId(), request)));
    }

    @GetMapping("/my/round/{roundId}")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<SubmissionResponse>> getMySubmission(
            @PathVariable Integer roundId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Submission retrieved successfully.",
                submissionService.getMySubmission(principal.getUserId(), roundId)));
    }

    @GetMapping("/my/round/{roundId}/eligibility")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<SubmissionEligibilityResponse>> getMyEligibility(
            @PathVariable Integer roundId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Round eligibility retrieved successfully.",
                submissionService.getMyEligibility(principal.getUserId(), roundId)));
    }

    @GetMapping("/round/{roundId}")
    @PreAuthorize("hasAnyRole('JUDGE', 'EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<SubmissionResponse>>> getByRound(
            @PathVariable Integer roundId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Submissions retrieved successfully.",
                submissionService.getSubmissionsByRound(principal.getUserId(), authorities(authentication), roundId)));
    }

    @GetMapping("/{submissionId}")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'JUDGE', 'EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<SubmissionResponse>> getById(
            @PathVariable Integer submissionId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Submission retrieved successfully.",
                submissionService.getSubmissionById(principal.getUserId(), authorities(authentication), submissionId)));
    }

    private Set<String> authorities(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
