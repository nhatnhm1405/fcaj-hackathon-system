package com.fcaj.hackathon.aijob;

import com.fcaj.hackathon.entity.*;
import com.fcaj.hackathon.repository.*;
import com.fcaj.hackathon.service.RoundTimerService;
import com.fcaj.hackathon.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name="app.ai.jobs.enabled", havingValue="true")
@RequiredArgsConstructor
public class JobAccess {
    private final UserRepository users;
    private final SubmissionRepository submissions;
    private final RoundTimerService timers;
    @Transactional(readOnly=true)
    public Submission create(int requesterId, int submissionId) {
        User user = approved(requesterId);
        Submission submission = submissions.findById(submissionId).orElseThrow(JobException::hidden);
        int event = submission.getRound().getEvent().getEventId();
        if (!role(user,"EVENT_COORDINATOR",event,false) && !(role(user,"JUDGE",event,true)
                && submissions.findBySubmissionIdAndJudgeId(submissionId,requesterId).isPresent()))
            throw new JobException(HttpStatus.FORBIDDEN,"JOB_CREATE_FORBIDDEN");
        try { timers.assertJudgingOpen(submission.getRound().getRoundId()); }
        catch (BadRequestException closed) { throw new JobException(HttpStatus.CONFLICT,"JUDGING_CLOSED"); }
        return submission;
    }
    @Transactional(readOnly=true)
    public void read(int requesterId, AiJobStore.Job job) {
        User user = approved(requesterId);
        Submission submission = submissions.findById(job.submissionId()).orElseThrow(JobException::hidden);
        if (submission.getRound().getRoundId() != job.roundId() || submission.getRound().getEvent().getEventId() != job.eventId())
            throw JobException.hidden();
        if (!role(user,"EVENT_COORDINATOR",job.eventId(),false) && !(requesterId == job.requesterId()
                && role(user,"JUDGE",job.eventId(),true)
                && submissions.findBySubmissionIdAndJudgeId(job.submissionId(),requesterId).isPresent())) throw JobException.hidden();
    }
    private User approved(int id) {
        User user = users.findByIdWithRoles(id).orElseThrow(JobException::hidden);
        if (!Boolean.TRUE.equals(user.getIsApproved())) throw JobException.hidden();
        return user;
    }
    private boolean role(User user, String role, int event, boolean global) {
        return user.getUserEventRoles().stream().anyMatch(g -> role.equals(g.getRole().getRoleName())
                && (Integer.valueOf(event).equals(g.getEventId()) || global && g.getEventId() == null));
    }
}
