package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO cho Mentor xem danh sách team mình đang quản lý.
 *
 * Cấu trúc:
 * - mentorId, mentorName: thông tin mentor
 * - eventName: sự kiện hackathon
 * - teams: danh sách các team được gán cho mentor
 *   - mỗi team bao gồm: teamId, teamName, trackName, thành viên, thời gian gán
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorAssignmentResponse {

    private Integer mentorId;
    private String mentorName;
    private String eventName;
    private List<AssignedTeamInfo> teams;
    // Mọi track mentor được phân công, KỂ CẢ track chưa có team nào — để FE liệt kê
    // đủ event/track trong dropdown thay vì chỉ những event đã có team APPROVED.
    private List<AssignedTrackInfo> tracks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignedTrackInfo {
        private Integer trackId;
        private String trackName;
        private Integer eventId;
        private String eventName;
        private String season;
        private Integer year;
        private String eventStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignedTeamInfo {
        private Integer teamId;
        private String teamName;
        private Integer trackId;
        private String trackName;
        // Event mà team/track thuộc về, để FE nhóm các track theo từng event
        // (một mentor có thể được phân công ở nhiều mùa hackathon).
        private Integer eventId;
        private String eventName;
        private String season;
        private Integer year;
        private String eventStatus;
        private List<TeamMemberInfo> members;
        // Trạng thái nộp bài: số submission (không tính DRAFT) + thời điểm nộp gần nhất.
        private long submissionCount;
        private LocalDateTime lastSubmittedAt;
        // Vòng team đang tham gia: round xa nhất team còn trụ lại. eliminated = true
        // nghĩa là team đã bị loại tại round này (không lọt Top N của vòng trước).
        private String currentRoundName;
        private boolean eliminated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMemberInfo {
        private Integer userId;
        private String fullName;
        private String email;
        private String memberRole;  // LEADER hoặc MEMBER
        // Thông tin đầy đủ để mentor xem chi tiết từng thành viên trong modal.
        private String studentId;   // MSSV (FPT hoặc trường ngoài)
        private String userType;    // FPT_STUDENT | EXTERNAL_STUDENT | STAFF
        private String university;  // trường (thường chỉ có với sinh viên ngoài)
    }
}
