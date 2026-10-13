package com.seal.hackathon.controller;

import com.seal.hackathon.dto.response.AiInsightResponse;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.AiJudgeAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI-assisted helpers. Currently exposes the Judge Assistant, which produces an
 * advisory reading of a submission. Restricted to judges and coordinators.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiJudgeAssistantService aiJudgeAssistantService;

    @PostMapping("/submissions/{submissionId}/insights")
    @PreAuthorize("hasAnyRole('JUDGE', 'EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<AiInsightResponse>> getSubmissionInsights(
            @PathVariable Integer submissionId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return ResponseEntity.ok(ApiResponse.success("AI insights generated.",
                aiJudgeAssistantService.analyzeSubmission(principal.getUserId(), authorities, submissionId)));
    }
}
