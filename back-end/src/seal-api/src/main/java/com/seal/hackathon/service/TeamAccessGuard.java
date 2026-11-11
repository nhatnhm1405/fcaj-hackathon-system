package com.seal.hackathon.service;

import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class TeamAccessGuard {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;

    /** Loads the team and asserts the given user is its LEADER. */
    public Team requireLeader(Integer userId, Integer teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        TeamMember me = teamMemberRepository.findByTeam_TeamId(teamId).stream()
                .filter(m -> m.getUser().getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("You are not a member of this team."));
        if (!"LEADER".equalsIgnoreCase(me.getMemberRole())) {
            throw new BadRequestException("Only the team leader can perform this action.");
        }
        return team;
    }

    /**
     * Resolves the team's current {@link TeamEventEntry} — the most recently
     * created one. A team is only ever active in one live season at a time
     * (rejoin requires the prior entry's team to be dormant first, see
     * TeamRejoinRequestService), so "most recent" is unambiguous for every
     * teamId-only coordinator/leader route, live {@code /my} lookups, and the
     * "which team can I submit to" endpoint. Full cross-season history reads
     * (getMyResultHistory) intentionally do NOT use this — they walk every
     * entry a member played, not just the newest one.
     */
    public TeamEventEntry requireCurrentEntry(Team team) {
        return teamEventEntryRepository.findTopByTeam_TeamIdOrderByIdDesc(team.getTeamId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No season participation found for team: " + team.getTeamId()));
    }

    public void ensureTeamManageable(TeamEventEntry entry) {
        if ("REJECTED".equalsIgnoreCase(entry.getStatus()) || "DISQUALIFIED".equalsIgnoreCase(entry.getStatus())) {
            throw new BadRequestException("This team can no longer be managed.");
        }
    }

    public String normalizeName(String name) {
        return name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
    }

    /** The team's current LEADER — every team is expected to have exactly one. */
    public TeamMember findLeader(Team team) {
        return teamMemberRepository.findByTeam_TeamId(team.getTeamId()).stream()
                .filter(member -> "LEADER".equalsIgnoreCase(member.getMemberRole()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("This team does not have a leader."));
    }
}
