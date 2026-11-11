package com.seal.hackathon.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI Judge Assistant output for a single submission.
 *
 * Advisory only: this is a Gemini-generated reading of the submission's own
 * description + links, meant to help a judge orient quickly. It deliberately
 * carries NO team identity so it stays compatible with anonymous judging.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiInsightResponse {

    /** 2–3 sentence neutral overview of what the project appears to do. */
    private String summary;

    /** Notable strengths inferred from the submission. */
    private List<String> strengths;

    /** Risks / gaps / things a judge may want to probe. */
    private List<String> concerns;

    /** One advisory note per configured scoring criterion. */
    private List<CriteriaInsight> criteriaInsights;

    /** Anonymized analysis of the submission's GitHub repository, if one was readable. */
    private RepoAnalysis repo;

    /** Fixed reminder that this is AI-generated guidance, not a score. */
    private String disclaimer;

    /** Which model produced this (for transparency / RBL provenance). */
    private String model;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CriteriaInsight {
        private String criteriaName;
        /** Short note tying the submission to this criterion. */
        private String assessment;
        /** Suggested range as a string, e.g. "7-8 / 10". Advisory, never auto-applied. */
        private String suggestedScoreRange;
    }

    /**
     * Anonymized facts + AI reading of the participant's GitHub repository.
     * Stays identity-free (no commit authors / team names) like the rest of the flow.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RepoAnalysis {
        /** True when the repo was fetched and analyzed. */
        private boolean analyzed;
        /** When not analyzed, a short reason (non-GitHub link, private/404, rate-limited…). */
        private String note;
        /** Detected languages/frameworks, most-used first. */
        private List<String> techStack;
        /** Positive technical signals (tests, CI, docs, commit activity…). */
        private List<String> signals;
        /** Authenticity / red flags worth a judge probing (empty when none). */
        private List<String> redFlags;
    }
}
