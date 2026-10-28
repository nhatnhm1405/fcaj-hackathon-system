package com.fcaj.hackathon.aijob;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fcaj.hackathon.repository.*;
import com.fcaj.hackathon.storage.ObjectKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name="app.ai.jobs.enabled", havingValue="true")
@RequiredArgsConstructor
public class JobSnapshotReader {
    private final JobAccess access;
    private final TeamEventEntryRepository entries;
    private final ScoringCriteriaRepository criteria;
    public record Input(int submissionId, int eventId, int roundId, Integer trackId, String repoUrl,
                        ObjectNode submission, ObjectNode rubric, String fingerprint) {}
    @Transactional(readOnly=true)
    public Input capture(int requesterId, int submissionId) {
        var s = access.create(requesterId, submissionId);
        if (!java.util.Set.of("SUBMITTED","LATE").contains(s.getStatus())) throw JobException.invalid("SUBMISSION_NOT_READY");
        int event = s.getRound().getEvent().getEventId(), round = s.getRound().getRoundId();
        var entry = entries.findByTeam_TeamIdAndEvent_EventId(s.getTeam().getTeamId(),event).orElseThrow(JobException::hidden);
        Integer track = entry.getTrack() == null ? null : entry.getTrack().getTrackId();
        ObjectNode submission = JobJson.object().put("description",s.getDescription()).put("repoUrl",s.getRepoUrl())
                .put("demoUrl",s.getDemoUrl()).put("slideUrl",s.getSlideUrl());
        ObjectNode rubric = JobJson.object().put("schemaVersion","fcaj.ai.rubric.v1");
        var list = rubric.putArray("criteria");
        for (var c : criteria.findAllByRound_RoundIdOrderByOrderNumber(round)) {
            list.addObject().put("criteriaId",c.getCriteriaId()).put("name",c.getName()).put("description",c.getDescription())
                    .put("maxScore",c.getMaxScore()).put("weight",c.getWeight());
        }
        if (list.isEmpty() || list.size() > 50) throw JobException.invalid("RUBRIC_NOT_READY");
        String rubricVersion = "sha256-" + ObjectKeys.sha256(JobJson.bytes(rubric));
        rubric.put("rubricVersion",rubricVersion);
        ObjectNode identity = JobJson.object().put("eventId",event).put("roundId",round).put("trackId",track)
                .put("status",s.getStatus()).put("submittedAt",s.getSubmittedAt().toString());
        identity.set("submission",submission); identity.set("rubric",rubric);
        return new Input(submissionId,event,round,track,s.getRepoUrl(),submission,rubric,ObjectKeys.sha256(JobJson.bytes(identity)));
    }
}
