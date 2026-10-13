package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.AnnouncementResponse;
import com.seal.hackathon.entity.Announcement;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ForbiddenException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.AnnouncementRepository;
import com.seal.hackathon.repository.MentorAssignmentRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TrackRepository;
import com.seal.hackathon.repository.UserEventRoleRepository;
import com.seal.hackathon.repository.UserRepository;
import com.seal.hackathon.repository.HackathonEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Composes announcements and fans them out into per-recipient notifications.
 *
 * - Mentor: scoped to one of their assigned TRACKs → participants (members of
 *   approved teams) in that track.
 * - Coordinator: scoped to a whole EVENT, targeting one audience —
 *   PARTICIPANT, JUDGE, MENTOR, or ALL.
 *
 * Audience resolution (only approved & active users receive):
 * - PARTICIPANT: members of APPROVED teams in the event (event-scoped).
 * - JUDGE / MENTOR: users holding that role for the event (UserEventRole).
 * - ALL: the union of the three above.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private static final Set<String> AUDIENCES = Set.of("PARTICIPANT", "JUDGE", "MENTOR", "ALL");

    // Announcements only make sense once an event is public and populated — block
    // DRAFT (not yet announced) and terminal states (COMPLETED / CANCELLED).
    private static final Set<String> ANNOUNCEABLE_STATUSES = Set.of("SETUP", "OPEN", "IN_PROGRESS");

    private final AnnouncementRepository announcementRepository;
    private final MentorAssignmentRepository mentorAssignmentRepository;
    private final UserEventRoleRepository userEventRoleRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TrackRepository trackRepository;
    private final HackathonEventRepository eventRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    // ── Mentor: announce to participants of one assigned track ────────

    @Transactional
    public AnnouncementResponse createMentorAnnouncement(Integer mentorId, Integer trackId,
                                                         String title, String content, String linkUrl) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));
        if (!mentorAssignmentRepository.existsByMentor_UserIdAndTrack_TrackId(mentorId, trackId)) {
            throw new ForbiddenException("You are not assigned to this track.");
        }
        User sender = userRepository.findById(mentorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + mentorId));

        Announcement ann = announcementRepository.save(Announcement.builder()
                .sender(sender)
                .senderRole("MENTOR")
                .scope("TRACK")
                .audience("PARTICIPANT")
                .event(track.getEvent())
                .track(track)
                .title(title)
                .content(content)
                .linkUrl(trimToNull(linkUrl))
                .recipientCount(0)
                .build());

        // Participants = approved & active members of approved teams in this track.
        Set<Integer> recipients = teamEventEntryRepository.findAllByTrack_TrackIdAndStatus(trackId, "APPROVED").stream()
                .flatMap(t -> teamMemberRepository.findByTeam_TeamId(t.getTeam().getTeamId()).stream())
                .map(m -> m.getUser())
                .filter(this::isApprovedRecipient)
                .map(User::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int count = deliver(recipients, mentorId, "[Mentor · " + track.getName() + "] " + title, content, ann);
        ann.setRecipientCount(count);
        announcementRepository.save(ann);

        auditLogService.record(mentorId, "MENTOR_ANNOUNCE", "TRACK", trackId, null,
                Map.of("announcement_id", ann.getAnnouncementId(), "recipient_count", count));

        return toResponse(ann);
    }

    // ── Coordinator: announce to an audience across a whole event ─────

    @Transactional
    public AnnouncementResponse createCoordinatorAnnouncement(Integer coordId, Integer eventId, String audience,
                                                             String title, String content, String linkUrl) {
        String aud = audience == null ? "" : audience.trim().toUpperCase();
        if (!AUDIENCES.contains(aud)) {
            throw new BadRequestException("audience must be one of PARTICIPANT, JUDGE, MENTOR, ALL.");
        }
        HackathonEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (!ANNOUNCEABLE_STATUSES.contains(event.getStatus())) {
            throw new BadRequestException(
                    "Announcements can only be sent while the event is in SETUP, OPEN, or IN_PROGRESS.");
        }
        User sender = userRepository.findById(coordId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + coordId));

        Announcement ann = announcementRepository.save(Announcement.builder()
                .sender(sender)
                .senderRole("COORDINATOR")
                .scope("EVENT")
                .audience(aud)
                .event(event)
                .track(null)
                .title(title)
                .content(content)
                .linkUrl(trimToNull(linkUrl))
                .recipientCount(0)
                .build());

        Set<Integer> recipients = resolveEventAudience(eventId, aud);
        int count = deliver(recipients, coordId, "[Coordinator] " + title, content, ann);
        ann.setRecipientCount(count);
        announcementRepository.save(ann);

        auditLogService.record(coordId, "COORD_ANNOUNCE", "EVENT", eventId, null,
                Map.of("announcement_id", ann.getAnnouncementId(), "audience", aud, "recipient_count", count));

        return toResponse(ann);
    }

    // ── Sent history (own announcements, newest first) ────────────────

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> listBySender(Integer senderId) {
        return announcementRepository.findBySender_UserIdOrderByCreatedAtDesc(senderId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /** Distinct approved & active recipient ids for an event audience. */
    private Set<Integer> resolveEventAudience(Integer eventId, String audience) {
        Set<Integer> ids = new LinkedHashSet<>();
        if (audience.equals("PARTICIPANT") || audience.equals("ALL")) {
            // Event-scoped: members of APPROVED teams in this event only.
            teamEventEntryRepository.findAllByEvent_EventIdAndStatus(eventId, "APPROVED").stream()
                    .flatMap(t -> teamMemberRepository.findByTeam_TeamId(t.getTeam().getTeamId()).stream())
                    .map(m -> m.getUser())
                    .filter(this::isApprovedRecipient)
                    .forEach(u -> ids.add(u.getUserId()));
        }
        if (audience.equals("JUDGE") || audience.equals("ALL")) {
            staffOf(eventId, "JUDGE").forEach(ids::add);
        }
        if (audience.equals("MENTOR") || audience.equals("ALL")) {
            staffOf(eventId, "MENTOR").forEach(ids::add);
        }
        return ids;
    }

    /** Approved & active users holding {@code roleName} for the event. */
    private Set<Integer> staffOf(Integer eventId, String roleName) {
        return userEventRoleRepository.findByRole_RoleNameAndEventId(roleName, eventId).stream()
                .map(uer -> uer.getUser())
                .filter(this::isApprovedRecipient)
                .map(User::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Creates one notification per recipient (excluding the author). */
    private int deliver(Set<Integer> recipientIds, Integer excludeUserId, String title, String content, Announcement ann) {
        recipientIds.remove(excludeUserId);
        recipientIds.forEach(uid ->
                notificationService.createNotification(uid, title, content, "ANNOUNCEMENT", ann));
        return recipientIds.size();
    }

    private boolean isApprovedRecipient(User u) {
        return Boolean.TRUE.equals(u.getIsApproved());
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private AnnouncementResponse toResponse(Announcement ann) {
        String scopeLabel = ann.getTrack() != null
                ? ann.getTrack().getName()
                : (ann.getEvent() != null ? ann.getEvent().getName() : null);
        return AnnouncementResponse.builder()
                .announcementId(ann.getAnnouncementId())
                .title(ann.getTitle())
                .content(ann.getContent())
                .linkUrl(ann.getLinkUrl())
                .senderName(ann.getSender() != null ? ann.getSender().getFullName() : null)
                .senderRole(ann.getSenderRole())
                .scope(ann.getScope())
                .audience(ann.getAudience())
                .eventId(ann.getEvent() != null ? ann.getEvent().getEventId() : null)
                .scopeLabel(scopeLabel)
                .recipientCount(ann.getRecipientCount())
                .createdAt(ann.getCreatedAt())
                .build();
    }
}
