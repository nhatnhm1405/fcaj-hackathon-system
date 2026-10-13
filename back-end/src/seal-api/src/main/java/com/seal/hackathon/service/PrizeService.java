package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.AutoGeneratePrizesRequest;
import com.seal.hackathon.dto.request.UpdatePrizeRequest;
import com.seal.hackathon.dto.response.PrizeResponse;
import com.seal.hackathon.entity.*;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Event-wide prizes. Winners are the top N of the FINAL round's global ranking
 * (all teams across all tracks combined), which {@link RoundResultService}
 * already produces for {@code isFinal = true} rounds. Prizes are never split by
 * track here, so {@code Prize.track} stays NULL.
 */
@Service
@RequiredArgsConstructor
public class PrizeService {

    private final PrizeRepository prizeRepository;
    private final HackathonEventRepository eventRepository;
    private final RoundRepository roundRepository;
    private final RoundResultRepository resultRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    // ── Read ──────────────────────────────────────────────────────────

    /** Public view shows only announced prizes; coordinators see every slot. */
    @Transactional(readOnly = true)
    public List<PrizeResponse> getPrizes(Integer eventId, boolean includeUnannounced) {
        requireEvent(eventId);
        List<Prize> prizes = includeUnannounced
                ? prizeRepository.findAllByEvent_EventIdOrderByRankPosition(eventId)
                : prizeRepository.findAllByEvent_EventIdAndAwardedAtIsNotNullOrderByRankPosition(eventId);
        Map<Integer, BigDecimal> finalScores = finalScoreByTeam(eventId);
        return prizes.stream().map(p -> mapToResponse(p, finalScores)).collect(Collectors.toList());
    }

    // ── Update / delete a slot ─────────────────────────────────────────
    // There is no manual "create a slot" or "assign a team" path — every prize
    // + its winning team is produced by autoGenerate() from the final ranking.
    // A coordinator may only rename a slot or delete one before it's announced.

    @Transactional
    public PrizeResponse updatePrize(Integer eventId, Integer prizeId, UpdatePrizeRequest req) {
        Prize prize = requirePrize(eventId, prizeId);
        if (prize.getAwardedAt() != null) {
            throw new BadRequestException("Cannot edit a prize that has already been announced.");
        }

        if (req.getName() != null) prize.setName(req.getName());

        prizeRepository.save(prize);
        return mapToResponse(prize, finalScoreByTeam(eventId));
    }

    @Transactional
    public void deletePrize(Integer eventId, Integer prizeId) {
        Prize prize = requirePrize(eventId, prizeId);
        if (prize.getAwardedAt() != null) {
            throw new BadRequestException("Cannot delete a prize that has already been announced.");
        }
        prizeRepository.delete(prize);
    }

    // ── Auto-generate winners from the final ranking ──────────────────

    @Transactional
    public List<PrizeResponse> autoGenerate(Integer eventId, AutoGeneratePrizesRequest req) {
        requireEvent(eventId);

        Round finalRound = roundRepository.findFirstByEvent_EventIdAndIsFinalTrue(eventId)
                .orElseThrow(() -> new BadRequestException(
                        "This event has no final round. Mark a round as final before awarding prizes."));
        if (!"FINALIZED".equalsIgnoreCase(finalRound.getStatus())) {
            throw new BadRequestException("Finalize the final round before generating prizes.");
        }

        List<RoundResult> ranking = resultRepository
                .findAllByRound_RoundIdOrderByRankPosition(finalRound.getRoundId());
        if (ranking.isEmpty()) {
            throw new BadRequestException("The final round has no results to award.");
        }

        // Already-announced prizes are immutable — don't let a re-generate wipe them.
        List<Prize> existing = prizeRepository.findAllByEvent_EventIdOrderByRankPosition(eventId);
        if (existing.stream().anyMatch(p -> p.getAwardedAt() != null)) {
            throw new BadRequestException("Prizes are already announced for this event and cannot be regenerated.");
        }
        // Replace any previous draft slots with a fresh set.
        prizeRepository.deleteAll(existing);

        int topN = Math.min(req.getTopN(), ranking.size());
        List<Prize> created = new ArrayList<>();
        for (int i = 0; i < topN; i++) {
            RoundResult r = ranking.get(i);
            int rank = i + 1;
            created.add(prizeRepository.save(Prize.builder()
                    .event(finalRound.getEvent())
                    .track(null)
                    .name(defaultPrizeName(rank))
                    .description("Top " + rank + " — " + finalRound.getEvent().getName())
                    .rankPosition(rank)
                    .team(r.getTeam())
                    .build()));
        }

        Map<Integer, BigDecimal> finalScores = finalScoreByTeam(eventId);
        return created.stream().map(p -> mapToResponse(p, finalScores)).collect(Collectors.toList());
    }

    // ── Announce (make public + notify winners) ───────────────────────

    @Transactional
    public List<PrizeResponse> announce(Integer eventId, Integer coordinatorId) {
        HackathonEvent event = requireEvent(eventId);

        List<Prize> prizes = prizeRepository.findAllByEvent_EventIdOrderByRankPosition(eventId);
        if (prizes.isEmpty()) {
            throw new BadRequestException("No prizes to announce. Generate or create prizes first.");
        }
        boolean missingTeam = prizes.stream().anyMatch(p -> p.getTeam() == null);
        if (missingTeam) {
            throw new BadRequestException("Every prize must have a winning team before announcing.");
        }

        LocalDateTime now = LocalDateTime.now();
        List<Prize> newlyAnnounced = prizes.stream()
                .filter(p -> p.getAwardedAt() == null)
                .collect(Collectors.toList());
        newlyAnnounced.forEach(p -> p.setAwardedAt(now));
        prizeRepository.saveAll(newlyAnnounced);

        // Notify each winning team (only for prizes announced in this call).
        newlyAnnounced.forEach(p -> teamMemberRepository.findByTeam_TeamId(p.getTeam().getTeamId())
                .forEach(m -> notificationService.createNotification(
                        m.getUser().getUserId(),
                        "Congratulations! 🏆",
                        "Your team \"" + p.getTeam().getName() + "\" won \"" + p.getName()
                                + "\" at " + event.getName() + ".",
                        "PRIZE")));

        // Audit trail — one business record for the whole award action.
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("eventId", eventId);
        metadata.put("winners", newlyAnnounced.stream()
                .map(p -> "#" + p.getRankPosition() + " " + p.getName() + " → " + p.getTeam().getName())
                .collect(Collectors.toList()));
        auditLogService.record(coordinatorId, "AWARD_PRIZE", "PRIZE", eventId, null, metadata);

        Map<Integer, BigDecimal> finalScores = finalScoreByTeam(eventId);
        return prizes.stream().map(p -> mapToResponse(p, finalScores)).collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private HackathonEvent requireEvent(Integer eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
    }

    private Prize requirePrize(Integer eventId, Integer prizeId) {
        Prize prize = prizeRepository.findById(prizeId)
                .orElseThrow(() -> new ResourceNotFoundException("Prize not found: " + prizeId));
        if (!prize.getEvent().getEventId().equals(eventId)) {
            throw new ResourceNotFoundException("Prize " + prizeId + " does not belong to event " + eventId);
        }
        return prize;
    }

    /** team_id → total score in the final round (for display); empty if no final round. */
    private Map<Integer, BigDecimal> finalScoreByTeam(Integer eventId) {
        return roundRepository.findFirstByEvent_EventIdAndIsFinalTrue(eventId)
                .map(round -> resultRepository
                        .findAllByRound_RoundIdOrderByRankPosition(round.getRoundId()).stream()
                        .collect(Collectors.toMap(r -> r.getTeam().getTeamId(), RoundResult::getTotalScore,
                                (a, b) -> a)))
                .orElseGet(HashMap::new);
    }

    private String defaultPrizeName(int rank) {
        switch (rank) {
            case 1:  return "Champion";
            case 2:  return "1st Runner-up";
            case 3:  return "2nd Runner-up";
            default: return "Top " + rank;
        }
    }

    private PrizeResponse mapToResponse(Prize p, Map<Integer, BigDecimal> finalScores) {
        Team team = p.getTeam();
        TeamEventEntry entry = team == null ? null : teamEventEntryRepository
                .findByTeam_TeamIdAndEvent_EventId(team.getTeamId(), p.getEvent().getEventId())
                .orElse(null);
        return PrizeResponse.builder()
                .prizeId(p.getPrizeId())
                .eventId(p.getEvent().getEventId())
                .name(p.getName())
                .description(p.getDescription())
                .rankPosition(p.getRankPosition())
                .teamId(team != null ? team.getTeamId() : null)
                .teamName(team != null ? team.getName() : null)
                .teamTrackName(entry != null && entry.getTrack() != null ? entry.getTrack().getName() : null)
                .finalScore(team != null ? finalScores.get(team.getTeamId()) : null)
                .awardedAt(p.getAwardedAt())
                .announced(p.getAwardedAt() != null)
                .build();
    }
}
