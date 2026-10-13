package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionScoringProgressResponse {

    private Integer submissionId;
    private Integer teamId;
    private String teamName;
    private Integer trackId;
    private String trackName;
    private Integer assignedJudgeCount;
    private Integer completedJudgeCount;
    private Boolean complete;
    private List<JudgeProgress> judges;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JudgeProgress {
        private Integer judgeUserId;
        private String judgeName;
        /** NOT_STARTED | DRAFT | INCOMPLETE | FINAL */
        private String status;
        private List<String> missingCriteria;
    }
}
