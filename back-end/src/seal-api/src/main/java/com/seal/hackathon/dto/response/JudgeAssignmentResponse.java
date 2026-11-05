package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO cho Judge xem danh sách các team mình chấm điểm.
 *
 * Cấu trúc:
 * - judgeId, judgeName: thông tin của Judge
 * - eventName: sự kiện hackathon
 * - teams: danh sách các team được gán cho judge
 *   - mỗi team bao gồm: teamId, teamName, trackName, roundId (vòng đấu), thành viên, thời gian gán
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeAssignmentResponse {

    private Integer judgeId;
    private String judgeName;
    private Integer eventId;
    private String eventName;
    private List<AssignedTeamInfo> teams;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignedTeamInfo {
        private Integer teamId;
        private String teamName;
        private String trackName;
        private Integer roundId;  // Vòng đấu cần chấm
        private Integer assignedJudgeCount; // panel size for this (round, track) cell
        private List<TeamMemberInfo> members;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMemberInfo {
        private Integer userId;
        private String fullName;
        private String email;
        private String memberRole; // LEADER hoặc MEMBER
        // Thông tin đầy đủ để judge xem chi tiết thành viên trong modal.
        private String studentId;   // MSSV
        private String userType;    // FPT_STUDENT | EXTERNAL_STUDENT | STAFF
        private String university;  // trường
    }
}
