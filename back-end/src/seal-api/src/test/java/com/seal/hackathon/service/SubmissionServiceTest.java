package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.SubmitRequest;
import com.seal.hackathon.dto.response.SubmissionEligibilityResponse;
import com.seal.hackathon.dto.response.SubmissionResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Round;
import com.seal.hackathon.entity.RoundResult;
import com.seal.hackathon.entity.Submission;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ForbiddenException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.RoundResultRepository;
import com.seal.hackathon.repository.RoundRepository;
import com.seal.hackathon.repository.SubmissionRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private RoundResultRepository resultRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoundTimerService roundTimerService;

    @Mock
    private TeamEventEntryRepository teamEventEntryRepository;

    @InjectMocks
    private SubmissionService submissionService;

    // ── submit ────────────────────────────────────────────────────────

    @Test
    void submit_shouldSaveSubmission_whenUserIsLeaderAndBeforeDeadline_NewSubmission() {
        User user = student(100, "Leader");
        Round round = round(1, "ACTIVE", LocalDateTime.now().plusDays(1));
        Team team = team(99, round.getEvent(), "APPROVED");
        TeamMember leader = member(1, team, user, "LEADER");

        when(userRepository.findById(100)).thenReturn(Optional.of(user));
        when(roundRepository.findById(1)).thenReturn(Optional.of(round));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(leader));
        when(submissionRepository.findByTeam_TeamIdAndRound_RoundId(99, 1)).thenReturn(Optional.empty());
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> {
            Submission sub = inv.getArgument(0);
            sub.setSubmissionId(10);
            return sub;
        });

        SubmitRequest request = request(1, " https://github.com/repo ",
                " https://demo.url ", " https://slide.url ", "  Demo project  ");
        SubmissionResponse response = submissionService.submit(100, request);

        assertEquals(10, response.getSubmissionId());
        assertEquals("https://github.com/repo", response.getRepoUrl());
        assertEquals("https://demo.url", response.getDemoUrl());
        assertEquals("https://slide.url", response.getSlideUrl());
        assertEquals("Demo project", response.getDescription());
        assertEquals("SUBMITTED", response.getStatus());

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(captor.capture());
        assertEquals(user, captor.getValue().getSubmittedBy());
    }

    @Test
    void submit_shouldUpdateSubmission_whenSubmissionAlreadyExists() {
        User user = student(100, "Leader");
        Round round = round(1, "ACTIVE", LocalDateTime.now().plusDays(1));
        Team team = team(99, round.getEvent(), "APPROVED");
        TeamMember leader = member(1, team, user, "LEADER");
        Submission existing = Submission.builder().submissionId(10).team(team).round(round)
                .repoUrl("https://old.url").status("SUBMITTED").build();

        when(userRepository.findById(100)).thenReturn(Optional.of(user));
        when(roundRepository.findById(1)).thenReturn(Optional.of(round));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(leader));
        when(submissionRepository.findByTeam_TeamIdAndRound_RoundId(99, 1)).thenReturn(Optional.of(existing));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        SubmitRequest request = request(1, " https://new.url ", " https://demo.url ", "", "  ");
        SubmissionResponse response = submissionService.submit(100, request);

        assertEquals(10, response.getSubmissionId());
        assertEquals("https://new.url", response.getRepoUrl());
        assertEquals("https://demo.url", response.getDemoUrl());
        assertNull(response.getSlideUrl());
        assertNull(response.getDescription());
    }

    @Test
    void submit_shouldThrowBadRequest_whenRequestIsNull() {
        assertThrows(BadRequestException.class, () -> submissionService.submit(100, null));
    }

    @Test
    void submit_shouldThrowBadRequest_whenRoundIdIsNull() {
        assertThrows(BadRequestException.class, () -> submissionService.submit(100, request(null, "url", null, null, null)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "ftp://repo", "http:// repo", "repo.com"})
    void submit_shouldThrowBadRequest_whenRepoUrlIsInvalid(String repoUrl) {
        assertThrows(BadRequestException.class, () -> submissionService.submit(100, request(1, repoUrl, null, null, null)));
    }

    @Test
    void submit_shouldThrowBadRequest_whenRepoUrlIsTooLong() {
        String longUrl = "http://" + "a".repeat(500);
        assertThrows(BadRequestException.class, () -> submissionService.submit(100, request(1, longUrl, null, null, null)));
    }

    @Test
    void submit_shouldThrowBadRequest_whenDemoUrlIsTooLong() {
        String longUrl = "http://" + "a".repeat(500);
        assertThrows(BadRequestException.class, () -> submissionService.submit(100, request(1, "http://repo", longUrl, null, null)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://demo", "http:// demo", "demo.com"})
    void submit_shouldThrowBadRequest_whenDemoUrlIsInvalid(String demoUrl) {
        assertThrows(BadRequestException.class,
                () -> submissionService.submit(100, request(1, "http://repo", demoUrl, null, null)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://slides", "https://slide deck", "slides.com"})
    void submit_shouldThrowBadRequest_whenSlideUrlIsInvalid(String slideUrl) {
        assertThrows(BadRequestException.class,
                () -> submissionService.submit(100, request(1, "http://repo", null, slideUrl, null)));
    }

    @Test
    void submit_shouldThrowBadRequest_whenSlideUrlIsTooLong() {
        String longUrl = "http://" + "a".repeat(500);
        assertThrows(BadRequestException.class,
                () -> submissionService.submit(100, request(1, "http://repo", null, longUrl, null)));
    }

    @Test
    void submit_shouldThrowBadRequest_whenDescriptionIsTooLong() {
        String longDesc = "a".repeat(5001);
        assertThrows(BadRequestException.class, () -> submissionService.submit(100, request(1, "http://repo", null, null, longDesc)));
    }

    @Test
    void submit_shouldThrowNotFound_whenUserDoesNotExist() {
        when(userRepository.findById(100)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> submissionService.submit(100, request(1, "http://repo", null, null, null)));
    }

    @Test
    void submit_shouldThrowForbidden_whenSubmitterIsNotStudent() {
        when(userRepository.findById(100)).thenReturn(Optional.of(user(100, "Staff Leader")));

        assertThrows(ForbiddenException.class,
                () -> submissionService.submit(100, request(1, "http://repo", null, null, null)));

        verify(roundRepository, never()).findById(any());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void submit_shouldThrowNotFound_whenRoundDoesNotExist() {
        when(userRepository.findById(100)).thenReturn(Optional.of(student(100, "Leader")));
        when(roundRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> submissionService.submit(100, request(1, "http://repo", null, null, null)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "CLOSED"})
    void submit_shouldThrowBadRequest_whenRoundStatusIsInvalid(String status) {
        Round round = round(1, status, LocalDateTime.now().plusDays(1));
        when(userRepository.findById(100)).thenReturn(Optional.of(student(100, "Leader")));
        when(roundRepository.findById(1)).thenReturn(Optional.of(round));

        assertThrows(BadRequestException.class, () -> submissionService.submit(100, request(1, "http://repo", null, null, null)));
    }

    @Test
    void submit_shouldAllowSubmit_whenRoundStatusIsOpen() {
        User user = student(100, "Leader");
        Round round = round(1, "OPEN", LocalDateTime.now().plusDays(1));
        Team team = team(99, round.getEvent(), "APPROVED");
        TeamMember leader = member(1, team, user, "LEADER");

        when(userRepository.findById(100)).thenReturn(Optional.of(user));
        when(roundRepository.findById(1)).thenReturn(Optional.of(round));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(leader));
        when(submissionRepository.findByTeam_TeamIdAndRound_RoundId(99, 1)).thenReturn(Optional.empty());
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> {
            Submission sub = inv.getArgument(0);
            sub.setSubmissionId(10);
            return sub;
        });

        SubmissionResponse response = submissionService.submit(100, request(1, "http://repo", null, null, null));
        assertEquals(10, response.getSubmissionId());
    }

    @Test
    void submit_shouldThrowBadRequest_whenUserNotInTeamForEvent() {
        User user = student(100, "Leader");
        Round round = round(1, "ACTIVE", LocalDateTime.now().plusDays(1));

        when(userRepository.findById(100)).thenReturn(Optional.of(user));
        when(roundRepository.findById(1)).thenReturn(Optional.of(round));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> submissionService.submit(100, request(1, "http://repo", null, null, null)));
    }

    @Test
    void submit_shouldThrowBadRequest_whenTeamStatusIsNotApproved() {
        User user = student(100, "Leader");
        Round round = round(1, "ACTIVE", LocalDateTime.now().plusDays(1));
        Team team = team(99, round.getEvent(), "PENDING");
        TeamMember leader = member(1, team, user, "LEADER");

        when(userRepository.findById(100)).thenReturn(Optional.of(user));
        when(roundRepository.findById(1)).thenReturn(Optional.of(round));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(leader));

        assertThrows(BadRequestException.class, () -> submissionService.submit(100, request(1, "http://repo", null, null, null)));
    }

    @Test
    void submit_shouldThrowForbidden_whenUserIsNotLeader() {
        User user = student(100, "Member");
        Round round = round(1, "ACTIVE", LocalDateTime.now().plusDays(1));
        Team team = team(99, round.getEvent(), "APPROVED");
        TeamMember member = member(1, team, user, "MEMBER");

        when(userRepository.findById(100)).thenReturn(Optional.of(user));
        when(roundRepository.findById(1)).thenReturn(Optional.of(round));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(member));

        assertThrows(ForbiddenException.class, () -> submissionService.submit(100, request(1, "http://repo", null, null, null)));
    }

    @Test
    void submit_shouldThrowBadRequest_whenDeadlineHasPassed() {
        User user = student(100, "Leader");
        Round round = round(1, "ACTIVE", LocalDateTime.now().minusMinutes(1));
        Team team = team(99, round.getEvent(), "APPROVED");
        TeamMember leader = member(1, team, user, "LEADER");

        when(userRepository.findById(100)).thenReturn(Optional.of(user));
        when(roundRepository.findById(1)).thenReturn(Optional.of(round));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(leader));

        assertThrows(BadRequestException.class, () -> submissionService.submit(100, request(1, "http://repo", null, null, null)));
    }

    @Test
    void submit_shouldThrowBadRequest_whenTeamWasEliminated() {
        User user = student(100, "Leader");
        Round previous = round(1, "FINALIZED", LocalDateTime.now().minusDays(1));
        previous.setTopNAdvance(2);
        Round current = round(2, "ACTIVE", LocalDateTime.now().plusDays(1));
        Team team = team(99, current.getEvent(), "APPROVED");
        TeamMember leader = member(1, team, user, "LEADER");
        RoundResult result = result(team, previous, 3, true);

        when(userRepository.findById(100)).thenReturn(Optional.of(user));
        when(roundRepository.findById(2)).thenReturn(Optional.of(current));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(leader));
        when(roundRepository.findAllByEvent_EventIdOrderByOrderNumber(1))
                .thenReturn(List.of(previous, current));
        when(resultRepository.findByTeam_TeamIdAndRound_RoundId(99, 1))
                .thenReturn(Optional.of(result));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> submissionService.submit(100, request(2, "http://repo", null, null, null)));

        assertTrue(exception.getMessage().contains("ranked #3"));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void submit_shouldAllowTeamThatAdvanced() {
        User user = student(100, "Leader");
        Round previous = round(1, "FINALIZED", LocalDateTime.now().minusDays(1));
        previous.setTopNAdvance(2);
        Round current = round(2, "ACTIVE", LocalDateTime.now().plusDays(1));
        Team team = team(99, current.getEvent(), "APPROVED");
        TeamMember leader = member(1, team, user, "LEADER");

        when(userRepository.findById(100)).thenReturn(Optional.of(user));
        when(roundRepository.findById(2)).thenReturn(Optional.of(current));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(leader));
        when(roundRepository.findAllByEvent_EventIdOrderByOrderNumber(1))
                .thenReturn(List.of(previous, current));
        when(resultRepository.findByTeam_TeamIdAndRound_RoundId(99, 1))
                .thenReturn(Optional.of(result(team, previous, 2, true)));
        when(submissionRepository.findByTeam_TeamIdAndRound_RoundId(99, 2))
                .thenReturn(Optional.empty());
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission submission = invocation.getArgument(0);
            submission.setSubmissionId(10);
            return submission;
        });

        SubmissionResponse response = submissionService.submit(
                100, request(2, "http://repo", null, null, null));

        assertEquals(10, response.getSubmissionId());
    }

    // ── getMyEligibility ────────────────────────────────────────────────

    @Test
    void getMyEligibility_shouldAllowFirstRound() {
        Round current = round(1, "ACTIVE", LocalDateTime.now().plusDays(1));
        Team team = team(99, current.getEvent(), "APPROVED");
        TeamMember membership = member(1, team, student(100, "Leader"), "LEADER");
        when(roundRepository.findById(1)).thenReturn(Optional.of(current));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(membership));
        when(roundRepository.findAllByEvent_EventIdOrderByOrderNumber(1))
                .thenReturn(List.of(current));

        SubmissionEligibilityResponse response = submissionService.getMyEligibility(100, 1);

        assertTrue(response.getEligible());
        assertEquals("ELIGIBLE", response.getStatus());
        assertNull(response.getPreviousRoundId());
    }

    @Test
    void getMyEligibility_shouldWaitWithoutLeakingRank_whenResultIsUnpublished() {
        EligibilityFixture fixture = eligibilityFixture();
        when(resultRepository.findByTeam_TeamIdAndRound_RoundId(99, 1))
                .thenReturn(Optional.of(result(fixture.team, fixture.previous, 3, false)));

        SubmissionEligibilityResponse response = submissionService.getMyEligibility(100, 2);

        assertFalse(response.getEligible());
        assertEquals("WAITING_FOR_RESULTS", response.getStatus());
        assertNull(response.getRankPosition());
    }

    @Test
    void getMyEligibility_shouldReturnAdvanced_whenPublishedRankIsWithinCutoff() {
        EligibilityFixture fixture = eligibilityFixture();
        when(resultRepository.findByTeam_TeamIdAndRound_RoundId(99, 1))
                .thenReturn(Optional.of(result(fixture.team, fixture.previous, 2, true)));

        SubmissionEligibilityResponse response = submissionService.getMyEligibility(100, 2);

        assertTrue(response.getEligible());
        assertEquals("ADVANCED", response.getStatus());
        assertEquals(2, response.getRankPosition());
        assertEquals(2, response.getTopNAdvance());
    }

    @Test
    void getMyEligibility_shouldReturnEliminated_whenPublishedRankMissesCutoff() {
        EligibilityFixture fixture = eligibilityFixture();
        when(resultRepository.findByTeam_TeamIdAndRound_RoundId(99, 1))
                .thenReturn(Optional.of(result(fixture.team, fixture.previous, 3, true)));

        SubmissionEligibilityResponse response = submissionService.getMyEligibility(100, 2);

        assertFalse(response.getEligible());
        assertEquals("ELIMINATED", response.getStatus());
        assertEquals(3, response.getRankPosition());
        assertTrue(response.getReason().contains("top 2"));
    }

    @Test
    void getMyEligibility_shouldEliminateUnrankedTeam_afterRoundResultsArePublished() {
        EligibilityFixture fixture = eligibilityFixture();
        Team otherTeam = team(88, fixture.current.getEvent(), "APPROVED");
        when(resultRepository.findByTeam_TeamIdAndRound_RoundId(99, 1))
                .thenReturn(Optional.empty());
        when(resultRepository.findAllByRound_RoundIdAndIsPublishedTrueOrderByRankPosition(1))
                .thenReturn(List.of(result(otherTeam, fixture.previous, 1, true)));

        SubmissionEligibilityResponse response = submissionService.getMyEligibility(100, 2);

        assertFalse(response.getEligible());
        assertEquals("ELIMINATED", response.getStatus());
        assertNull(response.getRankPosition());
    }

    // ── getMySubmission ───────────────────────────────────────────────

    @Test
    void getMySubmission_shouldReturnSubmission_whenExists() {
        Round round = round(1, "ACTIVE", LocalDateTime.now().plusDays(1));
        Team team = team(99, round.getEvent(), "APPROVED");
        TeamMember member = member(1, team, user(100, "Member"), "MEMBER");
        Submission submission = Submission.builder().submissionId(10).team(team).round(round)
                .submittedBy(user(10, "Submitter")).repoUrl("http://repo").status("SUBMITTED").build();

        when(roundRepository.findById(1)).thenReturn(Optional.of(round));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(member));
        when(submissionRepository.findByTeam_TeamIdAndRound_RoundId(99, 1)).thenReturn(Optional.of(submission));

        SubmissionResponse response = submissionService.getMySubmission(100, 1);

        assertEquals(10, response.getSubmissionId());
        assertEquals("http://repo", response.getRepoUrl());
    }

    @Test
    void getMySubmission_shouldThrowNotFound_whenRoundNotFound() {
        when(roundRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> submissionService.getMySubmission(100, 1));
    }

    @Test
    void getMySubmission_shouldThrowNotFound_whenUserNotInTeam() {
        Round round = round(1, "ACTIVE", LocalDateTime.now().plusDays(1));
        when(roundRepository.findById(1)).thenReturn(Optional.of(round));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> submissionService.getMySubmission(100, 1));
    }

    @Test
    void getMySubmission_shouldThrowNotFound_whenSubmissionDoesNotExist() {
        Round round = round(1, "ACTIVE", LocalDateTime.now().plusDays(1));
        Team team = team(99, round.getEvent(), "APPROVED");
        TeamMember member = member(1, team, user(100, "Member"), "MEMBER");

        when(roundRepository.findById(1)).thenReturn(Optional.of(round));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(member));
        when(submissionRepository.findByTeam_TeamIdAndRound_RoundId(99, 1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> submissionService.getMySubmission(100, 1));
    }

    // ── getSubmissionsByRound ─────────────────────────────────────────

    @Test
    void getSubmissionsByRound_shouldReturnAllSubmissions_whenCoordinator() {
        Round round = round(1, "ACTIVE", LocalDateTime.now());
        Submission sub = Submission.builder().submissionId(10).team(team(99, round.getEvent(), "APPROVED"))
                .submittedBy(user(10, "Submitter")).round(round).repoUrl("http://repo").status("SUBMITTED").build();

        when(roundRepository.findById(1)).thenReturn(Optional.of(round));
        when(submissionRepository.findAllByRound_RoundId(1)).thenReturn(List.of(sub));

        List<SubmissionResponse> responses = submissionService.getSubmissionsByRound(20, Set.of("ROLE_EVENT_COORDINATOR"), 1);

        assertEquals(1, responses.size());
        verify(submissionRepository).findAllByRound_RoundId(1);
        verify(submissionRepository, never()).findAllByRoundIdAndJudgeId(anyInt(), anyInt());
    }

    @Test
    void getSubmissionsByRound_shouldReturnOnlyAssignedSubmissionsForJudge() {
        Round round = round(1, "ACTIVE", LocalDateTime.now());
        Submission sub = Submission.builder().submissionId(10).team(team(99, round.getEvent(), "APPROVED"))
                .submittedBy(user(10, "Submitter")).round(round).repoUrl("http://repo").status("SUBMITTED").build();

        when(roundRepository.findById(1)).thenReturn(Optional.of(round));
        when(submissionRepository.findAllByRoundIdAndJudgeId(1, 20)).thenReturn(List.of(sub));

        List<SubmissionResponse> responses = submissionService.getSubmissionsByRound(20, Set.of("ROLE_JUDGE"), 1);

        assertEquals(1, responses.size());
        verify(submissionRepository).findAllByRoundIdAndJudgeId(1, 20);
        verify(submissionRepository, never()).findAllByRound_RoundId(anyInt());
    }

    @Test
    void getSubmissionsByRound_shouldThrowNotFound_whenRoundDoesNotExist() {
        when(roundRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> submissionService.getSubmissionsByRound(20, Set.of("ROLE_EVENT_COORDINATOR"), 1));
    }

    @Test
    void getSubmissionsByRound_shouldThrowForbidden_whenOtherRole() {
        Round round = round(1, "ACTIVE", LocalDateTime.now());
        when(roundRepository.findById(1)).thenReturn(Optional.of(round));

        assertThrows(ForbiddenException.class,
                () -> submissionService.getSubmissionsByRound(20, Set.of("ROLE_PARTICIPANT"), 1));
    }

    // ── getSubmissionById ─────────────────────────────────────────────

    @Test
    void getSubmissionById_shouldAllowCoordinatorToReadAll() {
        User coordinator = user(10, "Coord");
        Round round = round(2, "ACTIVE", LocalDateTime.now());
        Submission sub = Submission.builder().submissionId(99).team(team(5, event(1), "APPROVED"))
                .submittedBy(user(10, "Submitter")).round(round).repoUrl("http://repo").build();

        when(userRepository.findById(10)).thenReturn(Optional.of(coordinator));
        when(submissionRepository.findById(99)).thenReturn(Optional.of(sub));

        SubmissionResponse response = submissionService.getSubmissionById(10, Set.of("ROLE_EVENT_COORDINATOR"), 99);

        assertEquals(99, response.getSubmissionId());
        verify(submissionRepository).findById(99);
    }

    @Test
    void getSubmissionById_shouldAllowStudent_whenSubmissionBelongsToTeam() {
        User student = User.builder().userId(100).userType("FPT_STUDENT").build();
        Team team = team(5, event(1), "APPROVED");
        Round round = round(2, "ACTIVE", LocalDateTime.now());
        Submission sub = Submission.builder().submissionId(99).team(team).round(round)
                .submittedBy(user(10, "Submitter")).repoUrl("http://repo").build();

        when(userRepository.findById(100)).thenReturn(Optional.of(student));
        when(submissionRepository.findById(99)).thenReturn(Optional.of(sub));
        when(teamMemberRepository.existsByUser_UserIdAndTeam_TeamId(100, 5)).thenReturn(true);

        SubmissionResponse response = submissionService.getSubmissionById(100, Set.of("ROLE_PARTICIPANT"), 99);

        assertEquals(99, response.getSubmissionId());
    }

    @Test
    void getSubmissionById_shouldThrowForbidden_whenStudentDoesNotOwnTeam() {
        User student = User.builder().userId(100).userType("EXTERNAL_STUDENT").build();
        Team team = team(5, event(1), "APPROVED");
        Round round = round(2, "ACTIVE", LocalDateTime.now());
        Submission sub = Submission.builder().submissionId(99).team(team).round(round)
                .submittedBy(user(10, "Submitter")).repoUrl("http://repo").build();

        when(userRepository.findById(100)).thenReturn(Optional.of(student));
        when(submissionRepository.findById(99)).thenReturn(Optional.of(sub));
        when(teamMemberRepository.existsByUser_UserIdAndTeam_TeamId(100, 5)).thenReturn(false);

        assertThrows(ForbiddenException.class,
                () -> submissionService.getSubmissionById(100, Set.of("ROLE_PARTICIPANT"), 99));
    }

    @Test
    void getSubmissionById_shouldUseJudgeAssignment_whenRequesterIsJudge() {
        User judge = user(20, "Judge");
        Round round = round(2, "ACTIVE", LocalDateTime.now());
        Submission sub = Submission.builder().submissionId(99).team(team(5, event(1), "APPROVED"))
                .submittedBy(user(10, "Submitter")).round(round).repoUrl("http://repo").build();

        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(sub));

        SubmissionResponse response = submissionService.getSubmissionById(20, Set.of("ROLE_JUDGE"), 99);

        assertEquals(99, response.getSubmissionId());
        verify(submissionRepository).findBySubmissionIdAndJudgeId(99, 20);
        verify(submissionRepository, never()).findById(anyInt());
    }

    @Test
    void getSubmissionById_shouldThrowNotFound_whenJudgeIsNotAssigned() {
        User judge = user(20, "Judge");
        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> submissionService.getSubmissionById(20, Set.of("ROLE_JUDGE"), 99));

        assertTrue(ex.getMessage().contains("not assigned"));
    }

    @Test
    void getSubmissionById_shouldThrowForbidden_whenOtherRole() {
        User staff = user(30, "Staff"); // No FPT/EXTERNAL_STUDENT, not coord, not judge
        Round round = round(2, "ACTIVE", LocalDateTime.now());
        Submission sub = Submission.builder().submissionId(99).team(team(5, event(1), "APPROVED"))
                .round(round).repoUrl("http://repo").build();

        when(userRepository.findById(30)).thenReturn(Optional.of(staff));

        assertThrows(ForbiddenException.class,
                () -> submissionService.getSubmissionById(30, Set.of("ROLE_STAFF"), 99));
    }

    @Test
    void getSubmissionById_shouldThrowNotFound_whenRequesterNotFound() {
        when(userRepository.findById(30)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> submissionService.getSubmissionById(30, Set.of("ROLE_STAFF"), 99));
    }

    @Test
    void getSubmissionById_shouldThrowNotFound_whenCoordinatorAndSubmissionNotFound() {
        User coordinator = user(10, "Coord");
        when(userRepository.findById(10)).thenReturn(Optional.of(coordinator));
        when(submissionRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> submissionService.getSubmissionById(10, Set.of("ROLE_EVENT_COORDINATOR"), 99));
    }

    // ── Helper methods ────────────────────────────────────────────────

    private SubmitRequest request(Integer roundId, String repoUrl, String demoUrl, String slideUrl, String description) {
        SubmitRequest req = new SubmitRequest();
        req.setRoundId(roundId);
        req.setRepoUrl(repoUrl);
        req.setDemoUrl(demoUrl);
        req.setSlideUrl(slideUrl);
        req.setDescription(description);
        return req;
    }

    private User user(Integer id, String fullName) {
        return User.builder()
                .userId(id)
                .fullName(fullName)
                .email(id + "@seal.test")
                .userType("STAFF")
                .build();
    }

    private User student(Integer id, String fullName) {
        return User.builder()
                .userId(id)
                .fullName(fullName)
                .email(id + "@seal.test")
                .userType("FPT_STUDENT")
                .build();
    }

    private HackathonEvent event(Integer eventId) {
        return HackathonEvent.builder()
                .eventId(eventId)
                .name("Hackathon")
                .status("IN_PROGRESS")
                .build();
    }

    private Round round(Integer roundId, String status, LocalDateTime deadline) {
        return Round.builder()
                .roundId(roundId)
                .event(event(1))
                .name("Round " + roundId)
                .orderNumber(roundId)
                .status(status)
                .submissionDeadline(deadline)
                .build();
    }

    private RoundResult result(Team team, Round round, Integer rank, boolean published) {
        return RoundResult.builder()
                .resultId(rank)
                .team(team)
                .round(round)
                .rankPosition(rank)
                .isPublished(published)
                .build();
    }

    private EligibilityFixture eligibilityFixture() {
        Round previous = round(1, "FINALIZED", LocalDateTime.now().minusDays(1));
        previous.setTopNAdvance(2);
        Round current = round(2, "ACTIVE", LocalDateTime.now().plusDays(1));
        Team team = team(99, current.getEvent(), "APPROVED");
        TeamMember membership = member(1, team, student(100, "Leader"), "LEADER");
        when(roundRepository.findById(2)).thenReturn(Optional.of(current));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(membership));
        when(roundRepository.findAllByEvent_EventIdOrderByOrderNumber(1))
                .thenReturn(List.of(previous, current));
        return new EligibilityFixture(previous, current, team);
    }

    private record EligibilityFixture(Round previous, Round current, Team team) {}

    private Team team(Integer teamId, HackathonEvent event, String status) {
        Team team = Team.builder()
                .teamId(teamId)
                .name("Team " + teamId)
                .build();
        TeamEventEntry entry = TeamEventEntry.builder()
                .id(teamId)
                .team(team)
                .event(event)
                .status(status)
                .build();
        lenient().when(teamEventEntryRepository.existsByTeam_TeamIdAndEvent_EventId(teamId, event.getEventId()))
                .thenReturn(true);
        lenient().when(teamEventEntryRepository.findByTeam_TeamIdAndEvent_EventId(teamId, event.getEventId()))
                .thenReturn(Optional.of(entry));
        return team;
    }

    private TeamMember member(Integer id, Team team, User user, String role) {
        return TeamMember.builder()
                .id(id)
                .team(team)
                .user(user)
                .memberRole(role)
                .build();
    }
}
