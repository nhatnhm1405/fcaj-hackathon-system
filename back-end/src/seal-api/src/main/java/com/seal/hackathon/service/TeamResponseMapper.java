package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.MyTeamResponse;
import com.seal.hackathon.dto.response.TeamDetailResponse;
import com.seal.hackathon.dto.response.TeamResponse;
import com.seal.hackathon.entity.Round;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.repository.RoundRepository;
import com.seal.hackathon.repository.RoundResultRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TeamRejoinRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TeamResponseMapper {

    private final TeamMemberRepository teamMemberRepository;
    private final RoundRepository roundRepository;
    private final RoundResultRepository roundResultRepository;
    private final TeamRejoinRequestRepository teamRejoinRequestRepository;
    private final TeamAccessGuard teamAccessGuard;

    public TeamResponse mapToTeamResponse(Team team, TeamEventEntry entry) {
        return TeamResponse.builder()
                .teamId(team.getTeamId())
                .eventId(entry.getEvent().getEventId())
                .eventName(entry.getEvent().getName())
                .trackId(entry.getTrack() != null ? entry.getTrack().getTrackId() : null)
                .trackName(entry.getTrack() != null ? entry.getTrack().getName() : null)
                .name(team.getName())
                .description(team.getDescription())
                .status(entry.getStatus())
                .createdAt(team.getCreatedAt())
                .build();
    }

    public MyTeamResponse mapToMyTeamResponse(TeamMember membership) {
        return mapToMyTeamResponse(membership, teamAccessGuard.requireCurrentEntry(membership.getTeam()));
    }

    public MyTeamResponse mapToMyTeamResponse(TeamMember membership, TeamEventEntry entry) {
        Team team = membership.getTeam();
        List<TeamMember> allMembers = teamMemberRepository.findByTeam_TeamId(team.getTeamId());

        List<MyTeamResponse.TeamMemberInfo> memberInfos = allMembers.stream()
                .map(m -> MyTeamResponse.TeamMemberInfo.builder()
                        .userId(m.getUser().getUserId())
                        .memberName(m.getUser().getFullName())
                        .email(m.getUser().getEmail())
                        .studentType(m.getUser().getUserType())
                        .studentId(m.getUser().getStudentId())
                        .role(m.getMemberRole())
                        .joinedAt(m.getJoinedAt())
                        .isActive(m.getUser().getIsActive())
                        .build())
                .collect(Collectors.toList());

        boolean hasPendingRejoinRequest = teamRejoinRequestRepository
                .findByTeam_TeamIdAndStatus(team.getTeamId(), "PENDING").isPresent();

        return MyTeamResponse.builder()
                .teamId(team.getTeamId())
                .eventId(entry.getEvent().getEventId())
                .eventName(entry.getEvent().getName())
                .eventTopic(entry.getEvent().getTopic())
                .trackId(entry.getTrack() != null ? entry.getTrack().getTrackId() : null)
                .trackName(entry.getTrack() != null ? entry.getTrack().getName() : null)
                .trackDescription(entry.getTrack() != null ? entry.getTrack().getDescription() : null)
                .name(team.getName())
                .eventStatus(entry.getEvent().getStatus())
                .trackSelectionMode(entry.getEvent().getTrackSelectionMode())
                .status(entry.getStatus())
                .round(resolveTeamRound(team, entry))
                .myRole(membership.getMemberRole())
                .members(memberInfos)
                .hasPendingRejoinRequest(hasPendingRejoinRequest)
                .build();
    }

    public TeamDetailResponse mapToDetailResponse(Team team, TeamEventEntry entry) {
        List<TeamMember> members = teamMemberRepository.findByTeam_TeamId(team.getTeamId());
        List<TeamDetailResponse.MemberInfo> memberInfos = members.stream()
                .map(m -> TeamDetailResponse.MemberInfo.builder()
                        .userId(m.getUser().getUserId())
                        .fullName(m.getUser().getFullName())
                        .email(m.getUser().getEmail())
                        .memberRole(m.getMemberRole())
                        .joinedAt(m.getJoinedAt())
                        .studentId(m.getUser().getStudentId())
                        .userType(m.getUser().getUserType())
                        .university(m.getUser().getUniversity())
                        .isActive(m.getUser().getIsActive())
                        .build())
                .collect(Collectors.toList());

        return TeamDetailResponse.builder()
                .teamId(team.getTeamId())
                .eventId(entry.getEvent().getEventId())
                .eventName(entry.getEvent().getName())
                .trackId(entry.getTrack() != null ? entry.getTrack().getTrackId() : null)
                .trackName(entry.getTrack() != null ? entry.getTrack().getName() : null)
                .name(team.getName())
                .description(team.getDescription())
                .status(entry.getStatus())
                .disqualifiedReason(entry.getDisqualifiedReason())
                .disqualifiedAt(entry.getDisqualifiedAt())
                .createdAt(team.getCreatedAt())
                .members(memberInfos)
                .build();
    }

    private MyTeamResponse.RoundInfo resolveTeamRound(Team team, TeamEventEntry entry) {
        List<Round> rounds = roundRepository.findAllByEvent_EventIdOrderByOrderNumber(entry.getEvent().getEventId());
        if (rounds == null || rounds.isEmpty()) {
            return null;
        }

        if ("DISQUALIFIED".equalsIgnoreCase(entry.getStatus())) {
            return resolveDisqualificationRound(team, entry, rounds);
        }

        if (!"APPROVED".equalsIgnoreCase(entry.getStatus())) {
            return null;
        }

        return rounds.stream()
                .filter(round -> isActiveRound(round.getStatus()))
                .filter(round -> teamCanParticipateInRound(team, round, rounds))
                .findFirst()
                .map(this::mapToMyTeamRoundInfo)
                .orElse(null);
    }

    private MyTeamResponse.RoundInfo resolveDisqualificationRound(Team team, TeamEventEntry entry, List<Round> rounds) {
        LocalDateTime disqualifiedAt = entry.getDisqualifiedAt();
        if (disqualifiedAt != null) {
            return rounds.stream()
                    .filter(round -> contains(round, disqualifiedAt))
                    .findFirst()
                    .or(() -> rounds.stream()
                            .filter(round -> round.getStartTime() != null && !round.getStartTime().isAfter(disqualifiedAt))
                            .max(Comparator.comparing(Round::getOrderNumber, Comparator.nullsLast(Comparator.naturalOrder()))))
                    .map(this::mapToMyTeamRoundInfo)
                    .orElseGet(() -> mapToMyTeamRoundInfo(rounds.get(0)));
        }

        return rounds.stream()
                .filter(round -> isActiveRound(round.getStatus()))
                .findFirst()
                .map(this::mapToMyTeamRoundInfo)
                .orElseGet(() -> mapToMyTeamRoundInfo(rounds.get(rounds.size() - 1)));
    }

    private boolean teamCanParticipateInRound(Team team, Round round, List<Round> rounds) {
        Round previous = rounds.stream()
                .filter(candidate -> candidate.getOrderNumber() != null && round.getOrderNumber() != null)
                .filter(candidate -> candidate.getOrderNumber() < round.getOrderNumber())
                .max(Comparator.comparing(Round::getOrderNumber))
                .orElse(null);

        if (previous == null || !"FINALIZED".equalsIgnoreCase(previous.getStatus())
                || previous.getTopNAdvance() == null) {
            return true;
        }

        return roundResultRepository
                .findByTeam_TeamIdAndRound_RoundId(team.getTeamId(), previous.getRoundId())
                .map(result -> result.getRankPosition() != null
                        && result.getRankPosition() <= previous.getTopNAdvance())
                .orElse(false);
    }

    private boolean contains(Round round, LocalDateTime at) {
        LocalDateTime start = round.getStartTime();
        LocalDateTime end = round.getEndTime();
        if (start == null || end == null) {
            return false;
        }
        return !at.isBefore(start) && !at.isAfter(end);
    }

    private boolean isActiveRound(String status) {
        return "ACTIVE".equalsIgnoreCase(status)
                || "OPEN".equalsIgnoreCase(status)
                || "IN_PROGRESS".equalsIgnoreCase(status);
    }

    private MyTeamResponse.RoundInfo mapToMyTeamRoundInfo(Round round) {
        return MyTeamResponse.RoundInfo.builder()
                .roundId(round.getRoundId())
                .name(round.getName())
                .orderNumber(round.getOrderNumber())
                .status(round.getStatus())
                .isFinal(round.getIsFinal())
                .startTime(round.getStartTime())
                .endTime(round.getEndTime())
                .submissionDeadline(round.getSubmissionDeadline())
                .build();
    }
}
