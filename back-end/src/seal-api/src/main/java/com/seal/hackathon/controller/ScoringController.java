package com.seal.hackathon.controller;

import com.seal.hackathon.dto.request.CreateCriteriaRequest;
import com.seal.hackathon.dto.request.CreateTemplateRequest;
import com.seal.hackathon.dto.request.SubmitScoresRequest;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.ScoreResponse;
import com.seal.hackathon.dto.response.ScoringCriteriaResponse;
import com.seal.hackathon.dto.response.ScoringCriteriaTemplateResponse;
import com.seal.hackathon.dto.response.SubmissionScoringProgressResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.ScoringService;
import com.seal.hackathon.service.JudgeScoringCompletenessService;
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
@RequestMapping("/api")
@RequiredArgsConstructor
public class ScoringController {

    private final ScoringService scoringService;
    private final JudgeScoringCompletenessService completenessService;

    @GetMapping("/events/{eventId}/rounds/{roundId}/scoring-progress")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<SubmissionScoringProgressResponse>>> getScoringProgress(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId) {
        return ResponseEntity.ok(ApiResponse.success("Scoring progress retrieved successfully.",
                completenessService.getProgress(eventId, roundId)));
    }

    // ── Criteria ──────────────────────────────────────────────────────

    @GetMapping("/events/{eventId}/rounds/{roundId}/criteria")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ScoringCriteriaResponse>>> getCriteria(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId) {
        return ResponseEntity.ok(ApiResponse.success("Criteria retrieved successfully.",
                scoringService.getCriteriaByRound(roundId)));
    }

    @PostMapping("/events/{eventId}/rounds/{roundId}/criteria")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<ScoringCriteriaResponse>> createCriteria(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId,
            @Valid @RequestBody CreateCriteriaRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Criteria created successfully.",
                scoringService.createCriteria(eventId, roundId, request)));
    }

    @PutMapping("/events/{eventId}/rounds/{roundId}/criteria/{criteriaId}")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<ScoringCriteriaResponse>> updateCriteria(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId,
            @PathVariable Integer criteriaId,
            @RequestBody CreateCriteriaRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Criteria updated successfully.",
                scoringService.updateCriteria(eventId, roundId, criteriaId, request)));
    }

    @DeleteMapping("/events/{eventId}/rounds/{roundId}/criteria/{criteriaId}")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<Void>> deleteCriteria(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId,
            @PathVariable Integer criteriaId) {
        scoringService.deleteCriteria(eventId, roundId, criteriaId);
        return ResponseEntity.ok(ApiResponse.success("Criteria deleted successfully."));
    }

    // ── Criteria templates ────────────────────────────────────────────

    @GetMapping("/criteria-templates")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<ScoringCriteriaTemplateResponse>>> listTemplates() {
        return ResponseEntity.ok(ApiResponse.success("Templates retrieved successfully.",
                scoringService.listTemplates()));
    }

    @PostMapping("/events/{eventId}/rounds/{roundId}/criteria/apply-template/{templateId}")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<ScoringCriteriaResponse>>> applyTemplate(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId,
            @PathVariable Integer templateId) {
        return ResponseEntity.ok(ApiResponse.success("Template applied successfully.",
                scoringService.applyTemplate(eventId, roundId, templateId)));
    }

    @PostMapping("/events/{eventId}/rounds/{roundId}/criteria/save-as-template")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<ScoringCriteriaTemplateResponse>> saveAsTemplate(
            @PathVariable Integer eventId,
            @PathVariable Integer roundId,
            @Valid @RequestBody CreateTemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Template saved successfully.",
                scoringService.createTemplateFromRound(eventId, roundId, request)));
    }

    // ── Scores ────────────────────────────────────────────────────────

    @PostMapping("/scores")
    @PreAuthorize("hasRole('JUDGE')")
    public ResponseEntity<ApiResponse<List<ScoreResponse>>> submitScores(
            @Valid @RequestBody SubmitScoresRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Scores saved successfully.",
                scoringService.submitScores(principal.getUserId(), request)));
    }

    @GetMapping("/scores/submission/{submissionId}")
    @PreAuthorize("hasAnyRole('JUDGE', 'EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<ScoreResponse>>> getScoresBySubmission(
            @PathVariable Integer submissionId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Scores retrieved successfully.",
                scoringService.getScoresBySubmission(principal.getUserId(), authorities(authentication), submissionId)));
    }

    @GetMapping("/scores/my/round/{roundId}")
    @PreAuthorize("hasRole('JUDGE')")
    public ResponseEntity<ApiResponse<List<ScoreResponse>>> getMyScoresByRound(
            @PathVariable Integer roundId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("My scores retrieved successfully.",
                scoringService.getMyScoresByRound(principal.getUserId(), roundId)));
    }

    private Set<String> authorities(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
