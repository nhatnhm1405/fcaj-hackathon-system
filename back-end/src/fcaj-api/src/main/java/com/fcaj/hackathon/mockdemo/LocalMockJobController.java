package com.fcaj.hackathon.mockdemo;

import java.util.UUID;
import com.fcaj.hackathon.dto.response.AiInsightResponse;
import com.fcaj.hackathon.dto.response.ApiResponse;
import com.fcaj.hackathon.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("mock-demo")
@RequestMapping("/api/ai")
@PreAuthorize("hasAnyRole('JUDGE','EVENT_COORDINATOR')")
@RequiredArgsConstructor
public class LocalMockJobController {
    private final LocalMockJobService jobs;

    @PostMapping("/submissions/{submissionId}/jobs")
    public ResponseEntity<ApiResponse<LocalMockJobService.Status>> create(@PathVariable int submissionId, Authentication authentication) {
        return ResponseEntity.accepted().body(ApiResponse.success("Local mock job created.", jobs.create(userId(authentication), submissionId)));
    }
    @GetMapping("/jobs/{jobId}")
    public ApiResponse<LocalMockJobService.Status> get(@PathVariable UUID jobId, Authentication authentication) {
        return ApiResponse.success("Local mock job.", jobs.get(userId(authentication), jobId));
    }
    @GetMapping("/jobs/{jobId}/result")
    public ApiResponse<AiInsightResponse> result(@PathVariable UUID jobId, Authentication authentication) {
        return ApiResponse.success("Synthetic local mock result.", jobs.result(userId(authentication), jobId));
    }
    private int userId(Authentication auth) { return ((UserPrincipal) auth.getPrincipal()).getUserId(); }
}
