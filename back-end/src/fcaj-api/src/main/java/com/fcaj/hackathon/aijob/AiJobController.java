package com.fcaj.hackathon.aijob;

import java.util.UUID;
import com.fcaj.hackathon.dto.response.AiInsightResponse;
import com.fcaj.hackathon.dto.response.ApiResponse;
import com.fcaj.hackathon.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasAnyRole('JUDGE','EVENT_COORDINATOR')")
@ConditionalOnProperty(name="app.ai.jobs.enabled", havingValue="true")
@RequiredArgsConstructor
public class AiJobController {
    private final AiJobService jobs;
    @PostMapping("/submissions/{submissionId}/jobs")
    public ResponseEntity<ApiResponse<AiJobService.Status>> create(@PathVariable int submissionId,Authentication auth) {
        return ResponseEntity.accepted().body(ApiResponse.success("AI job accepted.",jobs.create(user(auth),submissionId)));
    }
    @GetMapping("/jobs/{jobId}")
    public ApiResponse<AiJobService.Status> get(@PathVariable UUID jobId,Authentication auth) {
        return ApiResponse.success("AI job.",jobs.get(user(auth),jobId));
    }
    @GetMapping("/jobs/{jobId}/result")
    public ApiResponse<AiInsightResponse> result(@PathVariable UUID jobId,Authentication auth) {
        return ApiResponse.success("AI assistance result.",jobs.result(user(auth),jobId));
    }
    private int user(Authentication auth) { return ((UserPrincipal)auth.getPrincipal()).getUserId(); }
}
