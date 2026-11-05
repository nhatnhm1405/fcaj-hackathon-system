package com.seal.hackathon.controller;

import com.seal.hackathon.dto.request.AutoGeneratePrizesRequest;
import com.seal.hackathon.dto.request.UpdatePrizeRequest;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.PrizeResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.PrizeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Event-wide prizes. URL-level access is public via the {@code /api/events/**}
 * rule; coordinator-only actions are enforced per-method with {@code @PreAuthorize},
 * mirroring {@link RoundResultController}.
 */
@RestController
@RequestMapping("/api/events/{eventId}/prizes")
@RequiredArgsConstructor
public class PrizeController {

    private final PrizeService prizeService;

    /** Public: announced prizes only. Coordinator: pass nothing special — service
     *  decides visibility from the caller's role via {@code all}. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PrizeResponse>>> getPrizes(
            @PathVariable Integer eventId,
            Authentication authentication) {
        boolean includeUnannounced = hasCoordinatorRole(authentication);
        return ResponseEntity.ok(ApiResponse.success("Prizes retrieved successfully.",
                prizeService.getPrizes(eventId, includeUnannounced)));
    }

    @PutMapping("/{prizeId}")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<PrizeResponse>> updatePrize(
            @PathVariable Integer eventId,
            @PathVariable Integer prizeId,
            @Valid @RequestBody UpdatePrizeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Prize updated successfully.",
                prizeService.updatePrize(eventId, prizeId, request)));
    }

    @DeleteMapping("/{prizeId}")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<Void>> deletePrize(
            @PathVariable Integer eventId,
            @PathVariable Integer prizeId) {
        prizeService.deletePrize(eventId, prizeId);
        return ResponseEntity.ok(ApiResponse.success("Prize deleted successfully."));
    }

    @PostMapping("/auto-generate")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<PrizeResponse>>> autoGenerate(
            @PathVariable Integer eventId,
            @Valid @RequestBody AutoGeneratePrizesRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Prizes generated from the final ranking.",
                prizeService.autoGenerate(eventId, request)));
    }

    @PostMapping("/announce")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<PrizeResponse>>> announce(
            @PathVariable Integer eventId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Prizes announced successfully.",
                prizeService.announce(eventId, principal.getUserId())));
    }

    private boolean hasCoordinatorRole(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_EVENT_COORDINATOR".equals(a.getAuthority()));
    }
}
