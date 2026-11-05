package com.seal.hackathon.service;

import com.seal.hackathon.dto.RepoDigest;
import com.seal.hackathon.dto.response.AiInsightResponse.RepoAnalysis;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the deterministic mapping {@code RepoDigest → RepoAnalysis}
 * ({@link AiJudgeAssistantService#buildRepoAnalysis}). This is the part a judge
 * trusts as fact (tech stack / signals / red flags), so it must not depend on the
 * model — these assertions pin the rules.
 */
class AiJudgeAssistantRepoAnalysisTest {

    /** A healthy, fully-featured repo digest used as the "no red flags" baseline. */
    private RepoDigest.RepoDigestBuilder healthy() {
        return RepoDigest.builder()
                .analyzed(true)
                .fullName("owner/repo")
                .languages(List.of("Java", "TypeScript"))
                .manifests(List.of("pom.xml", "package.json"))
                .license("MIT")
                .hasReadme(true).hasTests(true).hasCi(true).hasDocs(true)
                .fileCount(120).sizeKb(2048)
                .fork(false).archived(false)
                .commitCount(47).firstCommitDate("2026-06-10").lastCommitDate("2026-06-19");
    }

    @Test
    void notAnalyzedDigestPassesNoteThrough() {
        RepoDigest d = RepoDigest.builder().analyzed(false).note("Repo private (404).").build();
        RepoAnalysis r = AiJudgeAssistantService.buildRepoAnalysis(d);

        assertFalse(r.isAnalyzed());
        assertEquals("Repo private (404).", r.getNote());
        assertTrue(r.getTechStack().isEmpty());
        assertTrue(r.getSignals().isEmpty());
        assertTrue(r.getRedFlags().isEmpty());
    }

    @Test
    void nullDigestIsHandled() {
        RepoAnalysis r = AiJudgeAssistantService.buildRepoAnalysis(null);
        assertFalse(r.isAnalyzed());
        assertEquals("Could not analyze the repository.", r.getNote());
    }

    @Test
    void healthyRepoHasSignalsAndNoRedFlags() {
        RepoAnalysis r = AiJudgeAssistantService.buildRepoAnalysis(healthy().build());

        assertTrue(r.isAnalyzed());
        assertEquals(List.of("Java", "TypeScript"), r.getTechStack());
        assertTrue(r.getRedFlags().isEmpty(), "healthy repo should have no red flags");

        String signals = String.join(" | ", r.getSignals());
        assertTrue(signals.contains("README"));
        assertTrue(signals.contains("test"));
        assertTrue(signals.contains("CI/CD"));
        assertTrue(signals.contains("MIT"));
        assertTrue(signals.contains("47 commit"));
        assertTrue(signals.contains("2026-06-10"));   // date range shown
    }

    @Test
    void forkIsFlagged() {
        RepoAnalysis r = AiJudgeAssistantService.buildRepoAnalysis(healthy().fork(true).build());
        assertTrue(r.getRedFlags().stream().anyMatch(f -> f.contains("FORK")));
    }

    @Test
    void emptyRepoIsFlagged() {
        RepoAnalysis r = AiJudgeAssistantService.buildRepoAnalysis(
                healthy().fileCount(1).sizeKb(0).build());
        assertTrue(r.getRedFlags().stream().anyMatch(f -> f.contains("EMPTY")));
    }

    @Test
    void singleCommitDumpIsFlagged() {
        RepoAnalysis r = AiJudgeAssistantService.buildRepoAnalysis(
                healthy().commitCount(1).firstCommitDate("2026-06-19").lastCommitDate("2026-06-19").build());
        assertTrue(r.getRedFlags().stream().anyMatch(f -> f.contains("squeezed")));
    }

    @Test
    void allCommitsSameDayIsFlagged() {
        RepoAnalysis r = AiJudgeAssistantService.buildRepoAnalysis(
                healthy().commitCount(8).firstCommitDate("2026-06-19").lastCommitDate("2026-06-19").build());
        assertTrue(r.getRedFlags().stream().anyMatch(f -> f.contains("same day")));
    }

    @Test
    void missingReadmeIsFlagged() {
        RepoAnalysis r = AiJudgeAssistantService.buildRepoAnalysis(healthy().hasReadme(false).build());
        assertTrue(r.getRedFlags().stream().anyMatch(f -> f.contains("README")));
    }
}
