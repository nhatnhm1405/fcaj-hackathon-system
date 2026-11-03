package com.seal.hackathon.controller;

import com.seal.hackathon.dto.request.ApplyLeftoverGroupingRequest;
import com.seal.hackathon.dto.request.AssignTeamTrackRequest;
import com.seal.hackathon.dto.request.CoordinatorRemoveMemberRequest;
import com.seal.hackathon.dto.request.CreateTeamRequest;
import com.seal.hackathon.dto.request.ManualAssignLeftoverRequest;
import com.seal.hackathon.dto.request.RejectTeamRequest;
import com.seal.hackathon.dto.request.SelectTrackRequest;
import com.seal.hackathon.dto.request.UpdateTeamRequest;
import com.seal.hackathon.dto.response.ActiveEventResponse;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.GroupingCommitResponse;
import com.seal.hackathon.dto.response.GroupingPreviewResponse;
import com.seal.hackathon.dto.response.MyTeamResponse;
import com.seal.hackathon.dto.response.TeamDetailResponse;
import com.seal.hackathon.dto.response.TeamHistoryResponse;
import com.seal.hackathon.dto.response.TeamResponse;
import com.seal.hackathon.dto.response.UserResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.LeftoverGroupingService;
import com.seal.hackathon.service.TeamMembershipService;
import com.seal.hackathon.service.TeamModerationService;
import com.seal.hackathon.service.TeamQueryService;
import com.seal.hackathon.service.TeamTrackAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamQueryService teamQueryService;
    private final TeamMembershipService teamMembershipService;
    private final TeamModerationService teamModerationService;
    private final TeamTrackAssignmentService teamTrackAssignmentService;
    private final LeftoverGroupingService leftoverGroupingService;

    // ── Participant endpoints ────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<TeamResponse>> createTeam(
            @Valid @RequestBody CreateTeamRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                "Team created successfully. You are now the Team Leader.",
                teamMembershipService.createTeam(principal.getUserId(), request)));
    }

    /**
     * GET /api/teams/check-name?eventId=1&name=ByteBuilders
     * Returns true if a team with this (normalized) name already exists in the event.
     * Powers the live duplicate-name hint on the create-team form.
     */
    @GetMapping("/check-name")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<Boolean>> checkTeamName(
            @RequestParam Integer eventId,
            @RequestParam String name) {
        return ResponseEntity.ok(ApiResponse.success("OK", teamQueryService.teamNameExists(eventId, name)));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<MyTeamResponse>> getMyTeam(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("My team retrieved successfully.",
                teamQueryService.getMyTeam(principal.getUserId())));
    }

    @GetMapping("/my/history")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<List<MyTeamResponse>>> getMyTeamHistory(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("My team history retrieved successfully.",
                teamQueryService.getMyTeamHistory(principal.getUserId())));
    }

    @GetMapping("/my/result-history")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<List<TeamHistoryResponse>>> getMyResultHistory(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("My result history retrieved successfully.",
                teamQueryService.getMyResultHistory(principal.getUserId())));
    }

    @GetMapping("/my/event/{eventId}")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<MyTeamResponse>> getMyTeamByEvent(
            @PathVariable Integer eventId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("My team retrieved successfully.",
                teamQueryService.getMyTeamByEvent(principal.getUserId(), eventId)));
    }

    @GetMapping("/active-events")
    public ResponseEntity<ApiResponse<List<ActiveEventResponse>>> getActiveEvents() {
        return ResponseEntity.ok(ApiResponse.success("Active events retrieved successfully.",
                teamQueryService.getActiveEventsWithTracks()));
    }

    // SELF_SELECT events: the team leader picks the team's track during SETUP.
    @PutMapping("/{teamId}/track")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<MyTeamResponse>> selectTrack(
            @PathVariable Integer teamId,
            @Valid @RequestBody SelectTrackRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Track selected.",
                teamTrackAssignmentService.selectTrack(principal.getUserId(), teamId, request.getTrackId())));
    }

    // ── Participant: team management ─────────────────────────────────

    @GetMapping("/search-users")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> searchUsers(@RequestParam String query) {
        return ResponseEntity.ok(ApiResponse.success("Users retrieved.",
                teamQueryService.searchInvitableUsers(query)));
    }

    @PutMapping("/{teamId}")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<MyTeamResponse>> updateTeam(
            @PathVariable Integer teamId,
            @Valid @RequestBody UpdateTeamRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Team updated.",
                teamMembershipService.updateTeam(principal.getUserId(), teamId, request)));
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<MyTeamResponse>> removeMember(
            @PathVariable Integer teamId,
            @PathVariable Integer userId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Member removed.",
                teamMembershipService.removeMember(principal.getUserId(), teamId, userId)));
    }

    @PutMapping("/{teamId}/transfer/{newLeaderUserId}")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<MyTeamResponse>> transferLeadership(
            @PathVariable Integer teamId,
            @PathVariable Integer newLeaderUserId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Leadership transferred.",
                teamMembershipService.transferLeadership(principal.getUserId(), teamId, newLeaderUserId)));
    }

    @PostMapping("/{teamId}/leave")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<Void>> leaveTeam(
            @PathVariable Integer teamId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        teamMembershipService.leaveTeam(principal.getUserId(), teamId);
        return ResponseEntity.ok(ApiResponse.success("You have left the team.", null));
    }

    // A teamless participant opts out of the current (still-OPEN) season entirely —
    // distinct from leaveTeam, which only makes you teamless and keeps you active.
    @PostMapping("/event/{eventId}/leave-event")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<ApiResponse<Void>> leaveEvent(
            @PathVariable Integer eventId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        teamMembershipService.leaveEvent(principal.getUserId(), eventId);
        return ResponseEntity.ok(ApiResponse.success("You have left this event.", null));
    }

    // ── Coordinator endpoints ────────────────────────────────────────

    @GetMapping("/event/{eventId}")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<TeamDetailResponse>>> getTeamsByEvent(@PathVariable Integer eventId) {
        return ResponseEntity.ok(ApiResponse.success("Teams retrieved successfully.",
                teamQueryService.getTeamsByEvent(eventId)));
    }

    // Backs the Coordinator sidebar's "Teams" badge — a cross-event count so it's
    // accurate even before the Teams page itself has been opened.
    @GetMapping("/pending-count")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getPendingTeamsCount() {
        return ResponseEntity.ok(ApiResponse.success("Pending teams count retrieved.",
                Map.of("count", teamQueryService.getPendingTeamsCount())));
    }

    @GetMapping("/{teamId}")
    @PreAuthorize("hasAnyRole('EVENT_COORDINATOR', 'MENTOR')")
    public ResponseEntity<ApiResponse<TeamDetailResponse>> getTeamById(@PathVariable Integer teamId) {
        return ResponseEntity.ok(ApiResponse.success("Team retrieved successfully.",
                teamQueryService.getTeamById(teamId)));
    }

    // Leftover-team grouping — SETUP phase, run BEFORE the track draw. Preview is a
    // read-only dry run; commit applies the (deterministic) plan.
    @GetMapping("/event/{eventId}/leftover-grouping/preview")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<GroupingPreviewResponse>> previewLeftoverGrouping(
            @PathVariable Integer eventId) {
        return ResponseEntity.ok(ApiResponse.success("Leftover grouping preview generated.",
                leftoverGroupingService.preview(eventId)));
    }

    @PostMapping("/event/{eventId}/leftover-grouping/commit")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<GroupingCommitResponse>> commitLeftoverGrouping(
            @PathVariable Integer eventId,
            @RequestParam(required = false) String reason,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Leftover grouping applied.",
                leftoverGroupingService.commit(eventId, principal.getUserId(), reason)));
    }

    // Applies a coordinator-edited version of the preview's Proposed Teams (people
    // dragged between team cards before committing) instead of blindly re-running
    // the planner. Every touched team must land on 0 or >= MIN members.
    @PostMapping("/event/{eventId}/leftover-grouping/apply")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<GroupingCommitResponse>> applyLeftoverGroupingPlan(
            @PathVariable Integer eventId,
            @Valid @RequestBody ApplyLeftoverGroupingRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Leftover grouping applied.",
                leftoverGroupingService.applyPlan(eventId, principal.getUserId(), request)));
    }

    // Manual override for the two gaps the automatic planner can't close on its own:
    // placing a specific leftover person onto a specific team, or force-approving a
    // team below the recommended minimum (including a solo team). SETUP-only; may be
    // called any number of times, interleaved with preview/commit.
    @PostMapping("/event/{eventId}/leftover-grouping/manual-assign")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<GroupingCommitResponse>> manualAssignLeftover(
            @PathVariable Integer eventId,
            @Valid @RequestBody ManualAssignLeftoverRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Leftover person(s) placed.",
                leftoverGroupingService.manualAssign(eventId, principal.getUserId(), request)));
    }

    @PostMapping("/event/{eventId}/draw-tracks")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<TeamResponse>>> drawTracks(
            @PathVariable Integer eventId,
            @RequestParam(defaultValue = "false") boolean includeAssigned,
            @RequestParam(required = false) String reason,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Tracks drawn successfully.",
                teamTrackAssignmentService.drawTracks(eventId, includeAssigned, principal.getUserId(), reason)));
    }

    // Coordinator drag-and-drop: (re)assign a team to a track, or unassign it
    // (trackId = null). SETUP-only; capacity is intentionally NOT hard-capped here.
    @PutMapping("/{teamId}/track-assignment")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<TeamDetailResponse>> assignTeamTrack(
            @PathVariable Integer teamId,
            @RequestBody(required = false) AssignTeamTrackRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Integer trackId = request != null ? request.getTrackId() : null;
        return ResponseEntity.ok(ApiResponse.success("Team assignment updated.",
                teamTrackAssignmentService.assignTeamToTrack(principal.getUserId(), teamId, trackId)));
    }

    @PutMapping("/{teamId}/approve")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<TeamDetailResponse>> approveTeam(@PathVariable Integer teamId) {
        return ResponseEntity.ok(ApiResponse.success("Team approved successfully.",
                teamModerationService.approveTeam(teamId)));
    }

    @PutMapping("/{teamId}/reject")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<TeamDetailResponse>> rejectTeam(
            @PathVariable Integer teamId,
            @RequestBody(required = false) RejectTeamRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Team rejected.",
                teamModerationService.rejectTeam(teamId, request)));
    }

    @PutMapping("/{teamId}/disqualify")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<TeamDetailResponse>> disqualifyTeam(
            @PathVariable Integer teamId,
            @RequestBody(required = false) RejectTeamRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Team disqualified.",
                teamModerationService.disqualifyTeam(teamId, request)));
    }

    // Coordinator removes one specific member during a live (IN_PROGRESS) competition
    // — e.g. absence at a roll call. Requires a reason; auto-promotes a new leader or
    // disqualifies the team if that was the last remaining member.
    @PutMapping("/{teamId}/members/{userId}/coordinator-remove")
    @PreAuthorize("hasRole('EVENT_COORDINATOR')")
    public ResponseEntity<ApiResponse<TeamDetailResponse>> coordinatorRemoveMember(
            @PathVariable Integer teamId,
            @PathVariable Integer userId,
            @Valid @RequestBody CoordinatorRemoveMemberRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Member removed from the competition.",
                teamModerationService.coordinatorRemoveMember(principal.getUserId(), teamId, userId, request.getReason())));
    }
}
