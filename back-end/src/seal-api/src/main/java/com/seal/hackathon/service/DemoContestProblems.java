package com.seal.hackathon.service;

import com.seal.hackathon.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
@RequiredArgsConstructor
class DemoContestProblems {

    private static final String SEED_ACTION = "SEED_DEMO_CONTEST_PROBLEMS";
    private static final Map<String, String> STATEMENTS = Map.of(
            "Web Application", "web-application",
            "AI Solution", "ai-solution",
            "Education Tech", "education-tech",
            "Social Impact", "social-impact");

    private final TrackProblemService problems;
    private final AuditLogRepository auditLogs;
    private final AuditLogService auditLogService;

    void seedMissing(int eventId, int actorId) {
        // Upgrade older Contest snapshots once, preserving uploaded files and later edits.
        if (auditLogs.existsByActionAndTargetTypeAndTargetId(SEED_ACTION, "EVENT", eventId)) {
            return;
        }
        for (var track : problems.listProblemsForEvent(eventId, actorId)) {
            if (track.isHasProblem()) {
                continue;
            }
            String slug = STATEMENTS.get(track.getTrackName());
            if (slug == null) {
                continue;
            }
            ClassPathResource statement = new ClassPathResource("demo/problems/" + slug + ".md");
            int trackId = track.getTrackId();
            problems.uploadDemoArchive(actorId, eventId, trackId, "seal-summer-" + slug + ".zip", archive(statement));
            problems.releaseProblem(actorId, eventId, trackId);
        }
        auditLogService.record(actorId, SEED_ACTION, "EVENT", eventId);
    }

    private byte[] archive(ClassPathResource statement) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output); var input = statement.getInputStream()) {
            zip.putNextEntry(new ZipEntry("DE-BAI.md"));
            input.transferTo(zip);
            zip.closeEntry();
        } catch (IOException error) {
            throw new UncheckedIOException("Could not package the demo contest problem.", error);
        }
        return output.toByteArray();
    }
}
