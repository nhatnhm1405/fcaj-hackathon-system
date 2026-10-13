package com.seal.hackathon.controller;

import com.seal.hackathon.dto.request.AssignJudgeRequest;
import com.seal.hackathon.dto.request.AssignMentorRequest;
import com.seal.hackathon.dto.request.ReplaceJudgeRequest;
import com.seal.hackathon.dto.response.ApiResponse;
import com.seal.hackathon.dto.response.JudgeAssignmentResponse;
import com.seal.hackathon.dto.response.JudgeRosterItemResponse;
import com.seal.hackathon.dto.response.MentorAssignmentResponse;
import com.seal.hackathon.dto.response.MentorRosterItemResponse;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.JudgeAssignmentService;
import com.seal.hackathon.service.MentorAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Competition work assignments — EVENT_COORDINATOR only.
 *
 * The coordinator assigns judges to rounds/tracks and mentors to tracks for the
 * event they run. Creating the global account and granting COORDINATOR is the
 * admin's job (/api/admin); here we wire judges/mentors into the competition.
 */
@RestController
@RequestMapping("/api/coordinator/assignments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EVENT_COORDINATOR')")
public class CoordinatorAssignmentController {

    private final MentorAssignmentService mentorAssignmentService;
    private final JudgeAssignmentService judgeAssignmentService;

    @PostMapping("/mentors")
    public ResponseEntity<ApiResponse<MentorAssignmentResponse>> assignMentor(
            @Valid @RequestBody AssignMentorRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        MentorAssignmentResponse response = mentorAssignmentService.assignMentor(request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Mentor assigned successfully.", response));
    }

    /** Coordinator roster: list every active mentor assignment in an event. */
    @GetMapping("/mentors")
    public ResponseEntity<ApiResponse<List<MentorRosterItemResponse>>> listMentorAssignments(
            @RequestParam Integer eventId) {
        return ResponseEntity.ok(ApiResponse.success("Mentor assignments retrieved.",
                mentorAssignmentService.listMentorAssignmentsByEvent(eventId)));
    }

    @DeleteMapping("/mentors/{id}")
    public ResponseEntity<ApiResponse<Void>> removeMentorAssignment(@PathVariable Integer id) {
        mentorAssignmentService.removeMentorAssignment(id);
        return ResponseEntity.ok(ApiResponse.success("Mentor assignment removed.", null));
    }

    @PostMapping("/judges")
    public ResponseEntity<ApiResponse<JudgeAssignmentResponse>> assignJudge(
            @Valid @RequestBody AssignJudgeRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        JudgeAssignmentResponse response = judgeAssignmentService.assignJudge(request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Judge assigned successfully.", response));
    }

    /** Coordinator roster: list every active judge assignment in an event. */
    @GetMapping("/judges")
    public ResponseEntity<ApiResponse<List<JudgeRosterItemResponse>>> listJudgeAssignments(
            @RequestParam Integer eventId) {
        return ResponseEntity.ok(ApiResponse.success("Judge assignments retrieved.",
                judgeAssignmentService.listJudgeAssignmentsByEvent(eventId)));
    }

    @DeleteMapping("/judges/{id}")
    public ResponseEntity<ApiResponse<Void>> removeJudgeAssignment(@PathVariable Integer id) {
        judgeAssignmentService.removeJudgeAssignment(id);
        return ResponseEntity.ok(ApiResponse.success("Judge assignment removed.", null));
    }

    @PutMapping("/judges/{id}/replace")
    public ResponseEntity<ApiResponse<JudgeAssignmentResponse>> replaceJudgeAssignment(
            @PathVariable Integer id,
            @Valid @RequestBody ReplaceJudgeRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Judge replaced successfully.",
                judgeAssignmentService.replaceJudgeAssignment(id, request, principal.getUserId())));
    }
}
