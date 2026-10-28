package com.fcaj.hackathon.mockdemo;

import com.fcaj.hackathon.entity.*;
import com.fcaj.hackathon.exception.BadRequestException;
import com.fcaj.hackathon.repository.SubmissionRepository;
import com.fcaj.hackathon.repository.UserRepository;
import com.fcaj.hackathon.service.RoundTimerService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalMockJobAccessTest {
    private final UserRepository users=mock(UserRepository.class);
    private final SubmissionRepository submissions=mock(SubmissionRepository.class);
    private final RoundTimerService timers=mock(RoundTimerService.class);
    private final LocalMockJobAccess access=new LocalMockJobAccess(users,submissions,timers);
    private User user;
    private Submission submission;
    private LocalMockJobStore.Job job;

    @BeforeEach void setup() {
        user=User.builder().userId(1).isApproved(true).isActive(false).userEventRoles(new ArrayList<>()).build();
        submission=Submission.builder().submissionId(2).round(Round.builder().roundId(3)
                .event(HackathonEvent.builder().eventId(4).build()).build()).build();
        when(users.findByIdWithRoles(1)).thenReturn(Optional.of(user));
        when(submissions.findById(2)).thenReturn(Optional.of(submission));
        job=new LocalMockJobStore.Job(UUID.randomUUID(),1,2,4,3,"a".repeat(64),"QUEUED",OffsetDateTime.now(),null,null);
    }
    private void grant(String role,Integer event) {
        user.getUserEventRoles().add(UserEventRole.builder().user(user).role(Role.builder().roleName(role).build()).eventId(event).build());
    }
    @Test void coordinatorNeedsExactEventNotGlobalGrant() {
        grant("EVENT_COORDINATOR",null);
        assertThatThrownBy(()->access.forCreate(1,2)).isInstanceOf(MockJobException.class);
        assertThatThrownBy(()->access.forRead(1,job)).isInstanceOf(MockJobException.class);
        grant("EVENT_COORDINATOR",4);
        assertThat(access.forCreate(1,2)).isEqualTo(submission);
        assertThatCode(()->access.forRead(1,job)).doesNotThrowAnyException();
    }
    @Test void wrongEventAndAdminRoleDoNotGrantAccess() {
        grant("EVENT_COORDINATOR",9); grant("SYSTEM_ADMIN",null);
        assertThatThrownBy(()->access.forCreate(1,2)).isInstanceOf(MockJobException.class);
        assertThatThrownBy(()->access.forRead(1,job)).isInstanceOf(MockJobException.class);
    }
    @Test void judgeNeedsCurrentRoleAndMatchingAssignment() {
        grant("JUDGE",4);
        when(submissions.findBySubmissionIdAndJudgeId(2,1)).thenReturn(Optional.empty());
        assertThatThrownBy(()->access.forCreate(1,2)).isInstanceOf(MockJobException.class);
        when(submissions.findBySubmissionIdAndJudgeId(2,1)).thenReturn(Optional.of(submission));
        assertThat(access.forCreate(1,2)).isEqualTo(submission);
        user.getUserEventRoles().clear();
        assertThatThrownBy(()->access.forRead(1,job)).isInstanceOf(MockJobException.class);
    }
    @Test void judgeHistoryNeedsOwnershipAndAssignmentButNotOpenTimer() {
        grant("JUDGE",4);
        when(submissions.findBySubmissionIdAndJudgeId(2,1)).thenReturn(Optional.of(submission));
        assertThatCode(()->access.forRead(1,job)).doesNotThrowAnyException();
        var other=new LocalMockJobStore.Job(job.jobId(),99,2,4,3,job.inputRevision(),"QUEUED",job.createdAt(),null,null);
        assertThatThrownBy(()->access.forRead(1,other)).isInstanceOf(MockJobException.class);
        when(submissions.findBySubmissionIdAndJudgeId(2,1)).thenReturn(Optional.empty());
        assertThatThrownBy(()->access.forRead(1,job)).isInstanceOf(MockJobException.class);
        verifyNoInteractions(timers);
    }
    @Test void coordinatorAndJudgeMustCreateOnlyDuringOpenJudging() {
        grant("EVENT_COORDINATOR",4);
        doThrow(new BadRequestException("closed")).when(timers).assertJudgingOpen(3);
        assertThatThrownBy(()->access.forCreate(1,2)).isInstanceOf(MockJobException.class).hasMessage("JUDGING_CLOSED");
        assertThatCode(()->access.forRead(1,job)).doesNotThrowAnyException();
    }
    @Test void unapprovedUserAndReparentedSubmissionLoseHistoryAccess() {
        grant("EVENT_COORDINATOR",4);
        user.setIsApproved(false);
        assertThatThrownBy(()->access.forRead(1,job)).isInstanceOf(MockJobException.class);
        user.setIsApproved(true);
        submission.getRound().setEvent(HackathonEvent.builder().eventId(9).build());
        assertThatThrownBy(()->access.forRead(1,job)).isInstanceOf(MockJobException.class);
    }
}
