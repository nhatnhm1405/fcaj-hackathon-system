package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Read-only retrospective for a mentor: one entry per event they were assigned to,
 * grouped by the track(s) they mentored, with each approved team's final standing
 * and any prize. Used by the mentor "History" page.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorHistoryResponse {
    private Integer eventId;
    private String eventName;
    private String season;
    private Integer year;
    private String eventStatus;
    private List<TrackGroup> tracks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackGroup {
        private Integer trackId;
        private String trackName;
        private List<TeamResult> teams;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamResult {
        private Integer teamId;
        private String teamName;
        private String teamStatus;
        private Integer finalRank;   // rank in the final round if published, else null
        private String prizeName;    // announced prize for this team, else null
        private Integer memberCount;
        private List<MemberInfo> members;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberInfo {
        private String fullName;
        private String memberRole;   // LEADER | MEMBER
        private String studentId;    // MSSV
        private String userType;     // FPT_STUDENT | EXTERNAL_STUDENT | STAFF
        private String university;
    }
}
