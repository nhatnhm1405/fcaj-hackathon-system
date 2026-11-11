package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.CoordinatorEventHistoryResponse;
import com.seal.hackathon.entity.JudgeAssignment;
import com.seal.hackathon.entity.MentorAssignment;
import com.seal.hackathon.entity.Prize;
import com.seal.hackathon.entity.Round;
import com.seal.hackathon.entity.RoundResult;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.repository.JudgeAssignmentRepository;
import com.seal.hackathon.repository.MentorAssignmentRepository;
import com.seal.hackathon.repository.PrizeRepository;
import com.seal.hackathon.repository.RoundRepository;
import com.seal.hackathon.repository.RoundResultRepository;
import com.seal.hackathon.repository.SubmissionRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CoordinatorEventHistoryService {

    private final TrackRepository trackRepository;
    private final RoundRepository roundRepository;
    private final MentorAssignmentRepository mentorAssignmentRepository;
    private final JudgeAssignmentRepository judgeAssignmentRepository;
    private final PrizeRepository prizeRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final SubmissionRepository submissionRepository;
    private final RoundResultRepository roundResultRepository;
    private final TeamMemberRepository teamMemberRepository;

    /**
     * Full coordinator retrospective of one past event: overview stats, awarded
     * prizes, and every track with its mentor(s), the judges on each round, and
     * every approved team's final standing + members. Unlike {@code MentorAssignmentService#getMentorHistory}
     * (scoped to one mentor's tracks) this covers every track in the event, since
     * the coordinator ran the whole thing, not just one track.
     */
    @Transactional(readOnly = true)
    public CoordinatorEventHistoryResponse getEventHistory(Integer eventId) {
        List<Track> tracks = trackRepository.findAllByEvent_EventId(eventId);
        List<Round> orderedRounds = roundRepository.findAllByEvent_EventIdOrderByOrderNumber(eventId);
        Round finalRound = orderedRounds.stream().filter(Round::getIsFinal).findFirst().orElse(null);

        Map<Integer, List<String>> mentorNamesByTrack = new LinkedHashMap<>();
        for (MentorAssignment ma : mentorAssignmentRepository.findActiveByEvent(eventId)) {
            mentorNamesByTrack.computeIfAbsent(ma.getTrack().getTrackId(), k -> new ArrayList<>()).add(ma.getMentor().getFullName());
        }

        // Judges scoped to a track (preliminary rounds) vs. event-wide (the final round, track = null).
        Map<Integer, Map<Integer, List<String>>> judgeNamesByTrackThenRound = new LinkedHashMap<>();
        Map<Integer, List<String>> finalJudgeNamesByRound = new LinkedHashMap<>();
        for (JudgeAssignment ja : judgeAssignmentRepository.findActiveByEvent(eventId)) {
            String judgeName = ja.getJudge().getFullName();
            Integer roundId = ja.getRound().getRoundId();
            if (ja.getTrack() == null) {
                finalJudgeNamesByRound.computeIfAbsent(roundId, k -> new ArrayList<>()).add(judgeName);
            } else {
                judgeNamesByTrackThenRound
                        .computeIfAbsent(ja.getTrack().getTrackId(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(roundId, k -> new ArrayList<>())
                        .add(judgeName);
            }
        }

        List<CoordinatorEventHistoryResponse.PrizeInfo> prizeInfos = new ArrayList<>();
        Map<Integer, String> prizeByTeam = new HashMap<>();
        for (Prize prize : prizeRepository.findAllByEvent_EventIdAndAwardedAtIsNotNullOrderByRankPosition(eventId)) {
            prizeInfos.add(CoordinatorEventHistoryResponse.PrizeInfo.builder()
                    .rankPosition(prize.getRankPosition())
                    .prizeName(prize.getName())
                    .teamName(prize.getTeam() != null ? prize.getTeam().getName() : null)
                    .build());
            if (prize.getTeam() != null) {
                prizeByTeam.putIfAbsent(prize.getTeam().getTeamId(), prize.getName());
            }
        }

        int totalTeams = 0;
        int submittedTeams = 0;
        List<CoordinatorEventHistoryResponse.TrackGroup> trackGroups = new ArrayList<>();

        for (Track track : tracks) {
            List<TeamEventEntry> approvedEntries = teamEventEntryRepository.findAllByTrack_TrackIdAndStatus(track.getTrackId(), "APPROVED");
            List<CoordinatorEventHistoryResponse.TeamResult> teamResults = new ArrayList<>();

            for (TeamEventEntry teamEntry : approvedEntries) {
                Team team = teamEntry.getTeam();
                totalTeams++;
                boolean hasSubmission = submissionRepository.findAllByTeam_TeamId(team.getTeamId()).stream()
                        .anyMatch(s -> !"DRAFT".equalsIgnoreCase(s.getStatus()));
                if (hasSubmission) {
                    submittedTeams++;
                }

                Integer finalRank = finalRound == null ? null : roundResultRepository
                        .findByTeam_TeamIdAndRound_RoundId(team.getTeamId(), finalRound.getRoundId())
                        .filter(rr -> Boolean.TRUE.equals(rr.getIsPublished()))
                        .map(RoundResult::getRankPosition)
                        .orElse(null);

                List<TeamMember> teamMembers = teamMemberRepository.findByTeam_TeamId(team.getTeamId());
                List<CoordinatorEventHistoryResponse.MemberInfo> members = new ArrayList<>();
                for (TeamMember tm : teamMembers) {
                    members.add(CoordinatorEventHistoryResponse.MemberInfo.builder()
                            .fullName(tm.getUser().getFullName())
                            .memberRole(tm.getMemberRole())
                            .studentId(tm.getUser().getStudentId())
                            .userType(tm.getUser().getUserType())
                            .university(tm.getUser().getUniversity())
                            .build());
                }

                teamResults.add(CoordinatorEventHistoryResponse.TeamResult.builder()
                        .teamId(team.getTeamId())
                        .teamName(team.getName())
                        .teamStatus(teamEntry.getStatus())
                        .finalRank(finalRank)
                        .prizeName(prizeByTeam.get(team.getTeamId()))
                        .memberCount(teamMembers.size())
                        .members(members)
                        .build());
            }

            List<CoordinatorEventHistoryResponse.RoundJudges> trackRoundJudges = new ArrayList<>();
            for (Map.Entry<Integer, List<String>> entry : judgeNamesByTrackThenRound.getOrDefault(track.getTrackId(), Map.of()).entrySet()) {
                Round round = findRoundById(orderedRounds, entry.getKey());
                trackRoundJudges.add(CoordinatorEventHistoryResponse.RoundJudges.builder()
                        .roundName(round != null ? round.getName() : "Round")
                        .judgeNames(entry.getValue())
                        .build());
            }

            trackGroups.add(CoordinatorEventHistoryResponse.TrackGroup.builder()
                    .trackId(track.getTrackId())
                    .trackName(track.getName())
                    .mentorNames(mentorNamesByTrack.getOrDefault(track.getTrackId(), List.of()))
                    .roundJudges(trackRoundJudges)
                    .teams(teamResults)
                    .build());
        }

        List<CoordinatorEventHistoryResponse.RoundJudges> finalRoundJudges = new ArrayList<>();
        for (Map.Entry<Integer, List<String>> entry : finalJudgeNamesByRound.entrySet()) {
            Round round = findRoundById(orderedRounds, entry.getKey());
            finalRoundJudges.add(CoordinatorEventHistoryResponse.RoundJudges.builder()
                    .roundName(round != null ? round.getName() : "Final")
                    .judgeNames(entry.getValue())
                    .build());
        }

        return CoordinatorEventHistoryResponse.builder()
                .totalTeams(totalTeams)
                .submittedTeams(submittedTeams)
                .prizes(prizeInfos)
                .tracks(trackGroups)
                .finalRoundJudges(finalRoundJudges)
                .build();
    }

    private Round findRoundById(List<Round> rounds, Integer roundId) {
        return rounds.stream().filter(r -> r.getRoundId().equals(roundId)).findFirst().orElse(null);
    }
}
