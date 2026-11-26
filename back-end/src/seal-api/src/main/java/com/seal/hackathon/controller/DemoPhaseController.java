package com.seal.hackathon.controller;

import com.seal.hackathon.config.seed.DemoScenario.Phase;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.DemoPhaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coordinator/demo-phases")
@PreAuthorize("hasRole('EVENT_COORDINATOR')")
@Profile("local & !aws")
@ConditionalOnProperty(name = "app.demo.phases.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoPhaseController {
    private final DemoPhaseService demoPhaseService;

    @GetMapping
    public ApiResponse<List<Phase>> getPhases() {
        return ApiResponse.success("Demo phases available.", List.of(Phase.values()));
    }

    @PostMapping("/{phase}")
    public ApiResponse<DemoEvent> openPhase(
            @PathVariable Phase phase,
            @RequestParam(defaultValue = "false") boolean fresh,
            @AuthenticationPrincipal UserPrincipal principal) {
        int eventId = demoPhaseService.openPhase(phase, principal.getUserId(), fresh);
        return ApiResponse.success("Demo phase ready.", new DemoEvent(eventId));
    }

    public record DemoEvent(int eventId) {}
}
