package com.fcaj.hackathon.mockdemo;

import com.fcaj.hackathon.entity.Submission;
import com.fcaj.hackathon.entity.User;
import com.fcaj.hackathon.exception.BadRequestException;
import com.fcaj.hackathon.repository.SubmissionRepository;
import com.fcaj.hackathon.repository.UserRepository;
import com.fcaj.hackathon.service.RoundTimerService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mock-demo")
@RequiredArgsConstructor
public class LocalMockJobAccess {
    private final UserRepository users;
    private final SubmissionRepository submissions;
    private final RoundTimerService timers;

    @Transactional(readOnly = true)
    public Submission forCreate(int requesterId, int submissionId) {
        User requester = approved(requesterId);
        Submission submission = submissions.findById(submissionId).orElseThrow(MockJobException::hidden);
        int eventId = submission.getRound().getEvent().getEventId();
        boolean coordinator = hasRole(requester, "EVENT_COORDINATOR", eventId, false);
        boolean judge = hasRole(requester, "JUDGE", eventId, true)
                && submissions.findBySubmissionIdAndJudgeId(submissionId, requesterId).isPresent();
        if (!coordinator && !judge) throw new MockJobException(HttpStatus.FORBIDDEN, "JOB_CREATE_FORBIDDEN");
        try { timers.assertJudgingOpen(submission.getRound().getRoundId()); }
        catch (BadRequestException closed) { throw new MockJobException(HttpStatus.CONFLICT, "JUDGING_CLOSED"); }
        return submission;
    }

    @Transactional(readOnly = true)
    public void forRead(int requesterId, LocalMockJobStore.Job job) {
        User requester = approved(requesterId);
        Submission submission = submissions.findById(job.submissionId()).orElseThrow(MockJobException::hidden);
        if (submission.getRound().getRoundId() != job.roundId()
                || submission.getRound().getEvent().getEventId() != job.eventId()) throw MockJobException.hidden();
        boolean coordinator = hasRole(requester, "EVENT_COORDINATOR", job.eventId(), false);
        boolean ownJudge = requesterId == job.requesterId() && hasRole(requester, "JUDGE", job.eventId(), true)
                && submissions.findBySubmissionIdAndJudgeId(job.submissionId(), requesterId).isPresent();
        if (!coordinator && !ownJudge) throw MockJobException.hidden();
        // Read-only history remains available after judging closes; no timer-open check here.
    }

    private User approved(int id) {
        User user = users.findByIdWithRoles(id).orElseThrow(MockJobException::hidden);
        if (!Boolean.TRUE.equals(user.getIsApproved())) throw MockJobException.hidden();
        return user;
    }

    private boolean hasRole(User user, String role, int eventId, boolean globalAllowed) {
        return user.getUserEventRoles().stream().anyMatch(grant -> role.equals(grant.getRole().getRoleName())
                && (Integer.valueOf(eventId).equals(grant.getEventId()) || globalAllowed && grant.getEventId() == null));
    }
}
