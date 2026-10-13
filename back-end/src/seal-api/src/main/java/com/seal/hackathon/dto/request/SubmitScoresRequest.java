package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SubmitScoresRequest {
    @NotNull
    private Integer submissionId;

    private boolean draft = false; // true = save as draft, false = submit final

    @NotNull
    private List<ScoreEntry> scores;

    @Data
    public static class ScoreEntry {
        @NotNull
        private Integer criteriaId;
        @NotNull
        private BigDecimal value;
        private String comment;
    }
}
