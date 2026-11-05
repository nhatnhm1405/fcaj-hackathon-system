package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Full read-only retrospective of one past event for the coordinator who ran it:
 * overview stats, awarded prizes, and every track with its mentor(s), the judges
 * on each round, and every team's final standing + members. Used by the
 * coordinator "History" page (a superset of the mentor/judge history views,
 * since the coordinator managed the whole event, not just one track).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoordinatorEventHistoryResponse {
    private int totalTeams;
    private int submittedTeams;
    private List<PrizeInfo> prizes;
    private List<TrackGroup> tracks;
    private List<RoundJudges> finalRoundJudges; // event-wide (not track-scoped); usually one final round

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrizeInfo {
        private Integer rankPosition;
        private String prizeName;
        private String teamName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackGroup {
        private Integer trackId;
        private String trackName;
        private List<String> mentorNames;
        private List<RoundJudges> roundJudges; // preliminary rounds scoped to this track
        private List<TeamResult> teams;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoundJudges {
        private String roundName;
        private List<String> judgeNames;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamResult {
        private Integer teamId;
        private String teamName;
        private String teamStatus;
        private Integer finalRank;
        private String prizeName;
        private Integer memberCount;
        private List<MemberInfo> members;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberInfo {
        private String fullName;
        private String memberRole;
        private String studentId;
        private String userType;
        private String university;
    }
}
