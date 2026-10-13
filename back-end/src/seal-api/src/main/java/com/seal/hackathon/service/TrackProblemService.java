package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.TrackProblemResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ForbiddenException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the "đề thi" (problem statement) of a track: ONE file per track that the
 * Coordinator uploads (while the event is SETUP/IN_PROGRESS), explicitly releases,
 * and that members of an APPROVED team in that track may then download.
 *
 * Problem files are access-controlled, so they are stored OUTSIDE the public
 * {@code app.upload.dir} (which is served openly at /uploads/**) and streamed only
 * through {@link #downloadProblem}. Only the internal storage key is persisted.
 */
@Service
@RequiredArgsConstructor
public class TrackProblemService {

    // Upload/replace/remove a problem only while the event is being set up or run.
    private static final Set<String> PROBLEM_MUTATION_ALLOWED_EVENT_STATUSES =
            Set.of("SETUP", "IN_PROGRESS");

    // Releasing ("phát đề") hands the problem to participants, so it is only allowed
    // once the event has actually STARTED (IN_PROGRESS) — never during SETUP.
    private static final String PROBLEM_RELEASE_REQUIRED_EVENT_STATUS = "IN_PROGRESS";

    // Allowed problem file extensions (PDF / Word / ZIP-with-assets).
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "zip");
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024; // 20MB (mirrors multipart limit)

    private final TrackRepository trackRepository;
    private final HackathonEventRepository eventRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AuditLogService auditLogService;

    @Value("${app.problem.dir:protected/problems}")
    private String problemDir;

    /** Stream + metadata for a download response. */
    public record ProblemDownload(Resource resource, String fileName, String contentType, long size) {}

    // ── Coordinator: upload / replace ─────────────────────────────────────────

    @Transactional
    public TrackProblemResponse uploadProblem(Integer actorUserId, Integer eventId,
                                              Integer trackId, MultipartFile file) {
        Track track = requireTrackInEvent(eventId, trackId);
        requireEventAllowsProblemMutation(track.getEvent(), "upload a problem");

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No problem file was uploaded.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("Problem file must be 20MB or smaller.");
        }
        String originalName = sanitizeFileName(file.getOriginalFilename());
        String ext = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BadRequestException("Only PDF, DOC, DOCX or ZIP files are allowed.");
        }

        try {
            Path dir = problemDirPath();
            Files.createDirectories(dir);
            String storageKey = "track_" + trackId + "_" + System.currentTimeMillis() + "." + ext;
            Path target = dir.resolve(storageKey);
            file.transferTo(target.toFile());

            // Replacing an existing file: best-effort delete the previous one.
            deleteStoredFileQuietly(track.getProblemStorageKey());

            track.setProblemStorageKey(storageKey);
            track.setProblemFileName(originalName);
            track.setProblemFileSize(file.getSize());
            track.setProblemContentType(resolveContentType(file.getContentType(), ext));
            track.setProblemUploadedAt(LocalDateTime.now());
            // released flag is intentionally left unchanged: replacing a typo in an
            // already-published problem keeps it published; hiding it is a separate
            // explicit "retract" action.
            trackRepository.save(track);
        } catch (IOException e) {
            throw new BadRequestException("Failed to store the uploaded problem file.");
        }

        auditLogService.record(actorUserId, "UPLOAD_PROBLEM", "TRACK", trackId, null,
                Map.of("eventId", eventId, "fileName", track.getProblemFileName()));
        return mapToResponse(track);
    }

    // ── Coordinator: release / retract ────────────────────────────────────────

    @Transactional
    public TrackProblemResponse releaseProblem(Integer actorUserId, Integer eventId, Integer trackId) {
        Track track = requireTrackInEvent(eventId, trackId);
        requireEventStartedForRelease(track.getEvent());
        if (track.getProblemStorageKey() == null) {
            throw new BadRequestException("Upload a problem file before releasing it.");
        }
        if (!Boolean.TRUE.equals(track.getProblemReleased())) {
            track.setProblemReleased(true);
            track.setProblemReleasedAt(LocalDateTime.now());
            trackRepository.save(track);
            auditLogService.record(actorUserId, "RELEASE_PROBLEM", "TRACK", trackId, null,
                    Map.of("eventId", eventId));
        }
        return mapToResponse(track);
    }

    @Transactional
    public TrackProblemResponse retractProblem(Integer actorUserId, Integer eventId, Integer trackId) {
        Track track = requireTrackInEvent(eventId, trackId);
        if (Boolean.TRUE.equals(track.getProblemReleased())) {
            track.setProblemReleased(false);
            track.setProblemReleasedAt(null);
            trackRepository.save(track);
            auditLogService.record(actorUserId, "RETRACT_PROBLEM", "TRACK", trackId, null,
                    Map.of("eventId", eventId));
        }
        return mapToResponse(track);
    }

    // ── Coordinator: remove entirely ──────────────────────────────────────────

    @Transactional
    public void removeProblem(Integer actorUserId, Integer eventId, Integer trackId) {
        Track track = requireTrackInEvent(eventId, trackId);
        requireEventAllowsProblemMutation(track.getEvent(), "remove a problem");
        if (track.getProblemStorageKey() == null) {
            throw new BadRequestException("This track has no problem to remove.");
        }
        deleteStoredFileQuietly(track.getProblemStorageKey());
        track.setProblemStorageKey(null);
        track.setProblemFileName(null);
        track.setProblemFileSize(null);
        track.setProblemContentType(null);
        track.setProblemReleased(false);
        track.setProblemUploadedAt(null);
        track.setProblemReleasedAt(null);
        trackRepository.save(track);
        auditLogService.record(actorUserId, "REMOVE_PROBLEM", "TRACK", trackId, null,
                Map.of("eventId", eventId));
    }

    // ── Coordinator: status of every track in the event ───────────────────────

    @Transactional(readOnly = true)
    public List<TrackProblemResponse> listProblemsForEvent(Integer eventId) {
        requireEvent(eventId);
        return trackRepository.findAllByEvent_EventId(eventId).stream()
                .sorted(Comparator.comparing(Track::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ── Shared (Coordinator or participant in the track): metadata ────────────

    @Transactional(readOnly = true)
    public TrackProblemResponse getProblem(Integer eventId, Integer trackId,
                                           Integer userId, boolean privileged) {
        Track track = requireTrackInEvent(eventId, trackId);
        // Coordinator/Admin: full visibility, released or not.
        if (privileged) {
            return mapToResponse(track);
        }
        // Participant must belong to an APPROVED team in this track.
        requireTrackMembership(userId, trackId);
        // Hide unreleased problems from participants — they shouldn't even know one
        // exists yet, so report it as "no problem".
        if (!Boolean.TRUE.equals(track.getProblemReleased()) || track.getProblemStorageKey() == null) {
            return TrackProblemResponse.builder()
                    .trackId(track.getTrackId())
                    .trackName(track.getName())
                    .hasProblem(false)
                    .released(false)
                    .build();
        }
        return mapToResponse(track);
    }

    // ── Shared (gated): download the file ─────────────────────────────────────

    @Transactional(readOnly = true)
    public ProblemDownload downloadProblem(Integer eventId, Integer trackId,
                                           Integer userId, boolean privileged) {
        Track track = requireTrackInEvent(eventId, trackId);
        if (track.getProblemStorageKey() == null) {
            throw new ResourceNotFoundException("This track has no problem file.");
        }
        if (!privileged) {
            requireTrackMembership(userId, trackId);
            if (!Boolean.TRUE.equals(track.getProblemReleased())) {
                throw new ForbiddenException("The problem for this track has not been released yet.");
            }
        }
        Path file = resolveStoredFile(track.getProblemStorageKey());
        if (!Files.exists(file)) {
            throw new ResourceNotFoundException("The stored problem file is missing.");
        }
        Resource resource = new FileSystemResource(file);
        String contentType = track.getProblemContentType() != null
                ? track.getProblemContentType()
                : "application/octet-stream";
        long size = track.getProblemFileSize() != null ? track.getProblemFileSize() : 0L;
        return new ProblemDownload(resource, track.getProblemFileName(), contentType, size);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Track requireTrackInEvent(Integer eventId, Integer trackId) {
        if (eventId == null || trackId == null) {
            throw new BadRequestException("Event ID and Track ID are required.");
        }
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));
        if (!track.getEvent().getEventId().equals(eventId)) {
            throw new BadRequestException("Track does not belong to event " + eventId + ".");
        }
        return track;
    }

    private HackathonEvent requireEvent(Integer eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
    }

    private void requireTrackMembership(Integer userId, Integer trackId) {
        boolean member = teamMemberRepository
                .existsByUser_UserIdAndTeam_Track_TrackIdAndTeam_StatusIgnoreCase(userId, trackId, "APPROVED");
        if (!member) {
            throw new ForbiddenException("Only members of an approved team in this track can access its problem.");
        }
    }

    private void requireEventAllowsProblemMutation(HackathonEvent event, String action) {
        String status = normalizeEventStatus(event.getStatus());
        if (!PROBLEM_MUTATION_ALLOWED_EVENT_STATUSES.contains(status)) {
            throw new BadRequestException("Cannot " + action + " when event status is " + status
                    + ". The event must be in SETUP or IN_PROGRESS.");
        }
    }

    private void requireEventStartedForRelease(HackathonEvent event) {
        String status = normalizeEventStatus(event.getStatus());
        if (!PROBLEM_RELEASE_REQUIRED_EVENT_STATUS.equals(status)) {
            throw new BadRequestException("Cannot release a problem when event status is " + status
                    + ". The event must be started (IN_PROGRESS) before the problem can be handed to participants.");
        }
    }

    private String normalizeEventStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BadRequestException("Event status is required.");
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        return "SET_UP".equals(normalized) ? "SETUP" : normalized;
    }

    private TrackProblemResponse mapToResponse(Track track) {
        boolean hasProblem = track.getProblemStorageKey() != null;
        return TrackProblemResponse.builder()
                .trackId(track.getTrackId())
                .trackName(track.getName())
                .hasProblem(hasProblem)
                .fileName(hasProblem ? track.getProblemFileName() : null)
                .fileSize(hasProblem ? track.getProblemFileSize() : null)
                .contentType(hasProblem ? track.getProblemContentType() : null)
                .released(Boolean.TRUE.equals(track.getProblemReleased()))
                .uploadedAt(track.getProblemUploadedAt())
                .releasedAt(track.getProblemReleasedAt())
                .build();
    }

    private Path problemDirPath() {
        return Paths.get(problemDir).toAbsolutePath().normalize();
    }

    /** Resolves a storage key against the problem dir, guarding against path traversal. */
    private Path resolveStoredFile(String storageKey) {
        Path base = problemDirPath();
        Path resolved = base.resolve(storageKey).normalize();
        if (!resolved.startsWith(base)) {
            throw new BadRequestException("Invalid problem file reference.");
        }
        return resolved;
    }

    private void deleteStoredFileQuietly(String storageKey) {
        if (storageKey == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolveStoredFile(storageKey));
        } catch (IOException | RuntimeException ignored) {
            // A leftover file is harmless; clearing the DB reference is what matters.
        }
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "problem";
        }
        // Keep only the leaf name; strip any directory components a client may send.
        String leaf = Paths.get(name).getFileName().toString().trim();
        return leaf.isEmpty() ? "problem" : leaf;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveContentType(String provided, String ext) {
        if (provided != null && !provided.isBlank()
                && !"application/octet-stream".equalsIgnoreCase(provided)) {
            return provided;
        }
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }
}
