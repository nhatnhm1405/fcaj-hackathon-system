package com.seal.hackathon.controller;

import com.seal.hackathon.config.seed.DemoWaves;
import com.seal.hackathon.config.seed.DemoWaves.EventWaves;
import com.seal.hackathon.config.seed.DemoWaves.Wave;
import com.seal.hackathon.config.seed.DemoWaves.WaveResult;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Demo waves: fill a running event with demo teams, submissions and scores, one wave
 * at a time. Off unless {@code app.demo.waves.enabled=true}; the endpoint does not
 * exist otherwise.
 */
@RestController
@RequestMapping("/api/coordinator/demo-waves")
@PreAuthorize("hasRole('EVENT_COORDINATOR')")
@ConditionalOnProperty(name = "app.demo.waves.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoWaveController {
    private final DemoWaves demoWaves;

    @GetMapping
    public ApiResponse<List<EventWaves>> list() {
        return ApiResponse.success("Demo waves.", demoWaves.list());
    }

    @PostMapping("/{eventId}/{wave}")
    public ApiResponse<WaveResult> run(
            @PathVariable int eventId,
            @PathVariable Wave wave,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Demo wave done.", demoWaves.run(eventId, wave, principal.getUserId()));
    }
}
