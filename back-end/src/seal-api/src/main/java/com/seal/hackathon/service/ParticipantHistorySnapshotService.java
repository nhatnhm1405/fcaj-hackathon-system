package com.seal.hackathon.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.seal.hackathon.dto.response.TeamHistoryResponse;
import com.seal.hackathon.entity.*;
import com.seal.hackathon.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Computes and freezes a participant's per-event result — the archival
 * counterpart to {@link TeamQueryService}'s live team views. A {@code TeamMember}
 * row is hard-deleted on every departure path (leave, leader-remove,
 * coordinator-remove), so without a snapshot taken *before* that delete, a
 * participant who leaves early loses all history for that season. Snapshots
 * are also taken in bulk when an event completes, for whoever is still on a
 * team then.
 *
 * Deliberately not a live reference: {@link com.seal.hackathon.entity.ParticipantEventHistory}
 * has no FK to Team, and the payload is one serialized {@link TeamHistoryResponse}
 * blob — once written, it never changes just because the team's roster or
 * the event's data later changes.
 */
@Service
@RequiredArgsConstructor
public class ParticipantHistorySnapshotService {

    private final ParticipantEventHistoryRepository participantEventHistoryRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final RoundResultRepository roundResultRepository;
    private final SubmissionRepository submissionRepository;
    private final PrizeRepository prizeRepository;
    private final HackathonEventRepository hackathonEventRepository;
    // This app has no autoconfigured ObjectMapper bean to inject (confirmed —
    // wiring one in throws NoSuchBeanDefinitionException), so build our own,
    // same as AuditLogService does. Unlike AuditLogService's metadata (plain
    // String/Integer), TeamHistoryResponse has LocalDateTime fields
    // (submittedAt, awardedAt), so JavaTimeModule must be registered or
    // serialization throws.
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * Computes a participant's result view for one (membership, entry) pair —
     * single source of truth reused for both the live "My History" read path
     * (see {@link TeamQueryService#getMyResultHistory}) and the frozen snapshot
     * payload below. A team competing across multiple seasons (rejoin) has
     * round results/submissions/prizes from every season on the same team_id,
     * so every lookup here is filtered to this entry's own event.
     */
    TeamHistoryResponse buildHistoryView(TeamMember membership, TeamEventEntry entry) {
        Team team = membership.getTeam();
        HackathonEvent event = entry.getEvent();

        List<TeamHistoryResponse.MemberInfo> memberInfos = teamMemberRepository.findByTeam_TeamId(team.getTeamId()).stream()
                .map(m -> TeamHistoryResponse.MemberInfo.builder()
                        .fullName(m.getUser().getFullName())
                        .role(m.getMemberRole())
                        .studentId(m.getUser().getStudentId())
                        .userType(m.getUser().getUserType())
                        .university(m.getUser().getUniversity())
                        .build())
                .collect(Collectors.toList());

        List<TeamHistoryResponse.RoundInfo> roundInfos = roundResultRepository
                .findAllByTeamIdOrderByRoundOrder(team.getTeamId()).stream()
                .filter(result -> result.getRound().getEvent().getEventId().equals(event.getEventId()))
                .map(result -> {
                    Round round = result.getRound();
                    Integer topNAdvance = round.getTopNAdvance();
                    boolean advanced = topNAdvance != null && result.getRankPosition() != null
                            && result.getRankPosition() <= topNAdvance;

                    return TeamHistoryResponse.RoundInfo.builder()
                            .roundName(round.getName())
                            .isFinal(round.getIsFinal())
                            .rankPosition(result.getRankPosition())
                            .advanced(advanced)
                            .totalScore(result.getTotalScore())
                            .build();
                })
                .collect(Collectors.toList());

        List<Submission> submissions = submissionRepository.findAllByTeam_TeamId(team.getTeamId()).stream()
                .filter(s -> s.getRound().getEvent().getEventId().equals(event.getEventId()))
                .sorted(Comparator.comparing(s -> s.getRound().getOrderNumber() != null ? s.getRound().getOrderNumber() : 0))
                .collect(Collectors.toList());

        List<TeamHistoryResponse.SubmissionInfo> submissionInfos = submissions.stream()
                .map(submission -> TeamHistoryResponse.SubmissionInfo.builder()
                        .roundName(submission.getRound().getName())
                        .repoUrl(submission.getRepoUrl())
                        .demoUrl(submission.getDemoUrl())
                        .slideUrl(submission.getSlideUrl())
                        .submittedAt(submission.getSubmittedAt())
                        .status(submission.getStatus())
                        .build())
                .collect(Collectors.toList());

        TeamHistoryResponse.PrizeInfo prizeInfo = prizeRepository
                .findAllByEvent_EventIdAndAwardedAtIsNotNullOrderByRankPosition(event.getEventId()).stream()
                .filter(p -> p.getTeam() != null && p.getTeam().getTeamId().equals(team.getTeamId()))
                .findFirst()
                .map(p -> TeamHistoryResponse.PrizeInfo.builder()
                        .name(p.getName())
                        .rankPosition(p.getRankPosition())
                        .awardedAt(p.getAwardedAt())
                        .build())
                .orElse(null);

        return TeamHistoryResponse.builder()
                .eventId(event.getEventId())
                .eventName(event.getName())
                .season(event.getSeason())
                .year(event.getYear())
                .eventStatus(event.getStatus())
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .trackName(entry.getTrack() != null ? entry.getTrack().getName() : null)
                .teamStatus(entry.getStatus())
                .myRole(membership.getMemberRole())
                .members(memberInfos)
                .rounds(roundInfos)
                .submissions(submissionInfos)
                .prize(prizeInfo)
                .build();
    }

    /**
     * Every {@link TeamEventEntry} this membership is responsible for — every
     * season the team has ever run, filtered to the ones this member could
     * actually have been part of. {@code TeamMember} has no "left at"
     * timestamp, so the only bound available is: exclude a season already
     * superseded by a newer entry before this member ever joined. Newest
     * first. Shared by the live "My History" read path
     * ({@link TeamQueryService#getMyResultHistory}) and {@link #snapshotDeparture}
     * below — both need the identical set, or a departure snapshot could
     * cover a different (narrower) set of seasons than what was visible live
     * a moment earlier.
     */
    List<TeamEventEntry> resolveEntriesForMembership(TeamMember membership) {
        List<TeamEventEntry> ascending = teamEventEntryRepository
                .findAllByTeam_TeamId(membership.getTeam().getTeamId()).stream()
                .sorted(Comparator.comparing(TeamEventEntry::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        List<TeamEventEntry> relevant = new ArrayList<>();
        for (int i = 0; i < ascending.size(); i++) {
            TeamEventEntry entry = ascending.get(i);
            LocalDateTime nextEntryCreatedAt = (i + 1 < ascending.size()) ? ascending.get(i + 1).getCreatedAt() : null;
            if (nextEntryCreatedAt != null && !membership.getJoinedAt().isBefore(nextEntryCreatedAt)) {
                continue;
            }
            relevant.add(entry);
        }
        Collections.reverse(relevant); // newest first
        return relevant;
    }

    /**
     * Freezes this participant's result for EVERY season of this team they
     * were part of — not just the current one — called BEFORE the caller
     * deletes the shared {@code TeamMember} row. That row is the only link to
     * every past entry for this team, so deleting it without snapshotting all
     * of them (not just the newest) would silently drop older, still-
     * unsnapshotted seasons (e.g. a COMPLETED season predating this feature,
     * before the admin backfill ran) the moment this member leaves. Frozen
     * exactly as of this moment: a round result or prize published/announced
     * afterward will not be reflected.
     */
    @Transactional
    public void snapshotDeparture(TeamMember membership, String reason) {
        for (TeamEventEntry entry : resolveEntriesForMembership(membership)) {
            TeamHistoryResponse view = buildHistoryView(membership, entry);
            upsert(membership.getUser(), entry.getEvent().getEventId(), reason, view);
        }
    }

    /**
     * Freezes a result row for every participant still on a team in this
     * event — called when the event completes. Reuses the same
     * entry-then-members walk as {@code HackathonEventService#lockCompletedEventParticipantsReadOnly}.
     */
    @Transactional
    public void snapshotEventCompletion(Integer eventId) {
        List<TeamEventEntry> entries = teamEventEntryRepository.findAllByEvent_EventId(eventId);
        for (TeamEventEntry entry : entries) {
            List<TeamMember> members = teamMemberRepository.findByTeam_TeamId(entry.getTeam().getTeamId());
            for (TeamMember member : members) {
                TeamHistoryResponse view = buildHistoryView(member, entry);
                upsert(member.getUser(), eventId, "COMPLETED", view);
            }
        }
    }

    /** Reads every frozen snapshot for a user, keyed by event id. */
    @Transactional(readOnly = true)
    public Map<Integer, TeamHistoryResponse> getSnapshotsForUser(Integer userId) {
        Map<Integer, TeamHistoryResponse> byEvent = new HashMap<>();
        for (ParticipantEventHistory row : participantEventHistoryRepository.findByUser_UserId(userId)) {
            byEvent.put(row.getEventId(), fromJson(row.getResultJson()));
        }
        return byEvent;
    }

    /**
     * One-time maintenance: snapshots every already-COMPLETED event that
     * predates this feature (so it has no snapshot yet). Re-runnable safely
     * (upsert) if ever needed again. SYSTEM_ADMIN-only, see
     * {@link com.seal.hackathon.controller.ParticipantHistoryController}.
     */
    @Transactional
    public int backfillCompletedEvents() {
        List<HackathonEvent> completed = hackathonEventRepository.findAll().stream()
                .filter(e -> "COMPLETED".equalsIgnoreCase(e.getStatus()))
                .collect(Collectors.toList());
        completed.forEach(e -> snapshotEventCompletion(e.getEventId()));
        return completed.size();
    }

    private void upsert(User user, Integer eventId, String reason, TeamHistoryResponse view) {
        ParticipantEventHistory row = participantEventHistoryRepository
                .findByUser_UserIdAndEventId(user.getUserId(), eventId)
                .orElseGet(() -> ParticipantEventHistory.builder()
                        .user(user)
                        .eventId(eventId)
                        .build());
        row.setSnapshotReason(reason);
        row.setSnapshotAt(LocalDateTime.now());
        row.setResultJson(toJson(view));
        participantEventHistoryRepository.save(row);
    }

    private String toJson(TeamHistoryResponse view) {
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize participant history snapshot", e);
        }
    }

    private TeamHistoryResponse fromJson(String json) {
        try {
            return objectMapper.readValue(json, TeamHistoryResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize participant history snapshot", e);
        }
    }
}
