package com.fcaj.hackathon.service;

import com.fcaj.hackathon.entity.*;
import com.fcaj.hackathon.repository.*;
import com.fcaj.hackathon.exception.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiJudgeAssistantAccessTest {
    @Mock SubmissionRepository submissions;
    @Mock ScoringCriteriaRepository criteria;
    @Mock GitHubRepoService github;
    @Mock RoundTimerService timers;
    @Mock UserRepository users;
    @InjectMocks AiJudgeAssistantService service;
    private User user;
    private Submission submission;
    @BeforeEach void setup() {
        user=User.builder().userId(1).isApproved(true).build();
        user.getUserEventRoles().add(UserEventRole.builder().role(Role.builder().roleName("EVENT_COORDINATOR").build()).eventId(99).build());
        submission=Submission.builder().submissionId(2).round(Round.builder().roundId(3).event(HackathonEvent.builder().eventId(4).build()).build()).build();
        when(users.findByIdWithRoles(1)).thenReturn(Optional.of(user));
    }
    @Test void oldSynchronousRouteCannotBypassEventScope() {
        when(submissions.findById(2)).thenReturn(Optional.of(submission));
        assertThatThrownBy(() -> service.analyzeSubmission(1,Set.of("ROLE_EVENT_COORDINATOR"),2)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(github,timers);
    }
    @Test void coordinatorAlsoRequiresOpenJudgingBeforeCallingProvider() {
        user.getUserEventRoles().getFirst().setEventId(4);when(submissions.findById(2)).thenReturn(Optional.of(submission));
        doThrow(new BadRequestException("Judging closed")).when(timers).assertJudgingOpen(3);
        assertThatThrownBy(() -> service.analyzeSubmission(1,Set.of("ROLE_EVENT_COORDINATOR"),2)).hasMessage("Judging closed");verifyNoInteractions(github);
    }
    @Test void revokedApprovalRejectsPreviouslyMintedAuthorities() {
        user.setIsApproved(false);assertThatThrownBy(() -> service.analyzeSubmission(1,Set.of("ROLE_EVENT_COORDINATOR"),2)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(submissions,github);
    }
}
