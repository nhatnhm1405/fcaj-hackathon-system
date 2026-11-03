package com.seal.hackathon.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic tests for {@link GitHubRepoService}: URL parsing, the GitHub Link
 * header page-count trick, and the anonymity scrubber. No network access.
 */
class GitHubRepoServiceTest {

    private final GitHubRepoService service = new GitHubRepoService();

    // ── parseOwnerRepo ──────────────────────────────────────────────────

    @Test
    void parsesPlainGitHubUrl() {
        assertArrayEquals(new String[]{"spring-projects", "spring-petclinic"},
                service.parseOwnerRepo("https://github.com/spring-projects/spring-petclinic"));
    }

    @Test
    void stripsDotGitSuffix() {
        assertArrayEquals(new String[]{"octocat", "Hello-World"},
                service.parseOwnerRepo("https://github.com/octocat/Hello-World.git"));
    }

    @Test
    void parsesUrlWithTreeBranchPath() {
        assertArrayEquals(new String[]{"owner", "repo"},
                service.parseOwnerRepo("https://github.com/owner/repo/tree/main/src"));
    }

    @Test
    void parsesUrlWithTrailingSlash() {
        assertArrayEquals(new String[]{"owner", "repo"},
                service.parseOwnerRepo("https://github.com/owner/repo/"));
    }

    @Test
    void parsesSshStyleUrl() {
        assertArrayEquals(new String[]{"owner", "repo"},
                service.parseOwnerRepo("git@github.com:owner/repo.git"));
    }

    @Test
    void returnsNullForNonGitHubUrl() {
        assertNull(service.parseOwnerRepo("https://gitlab.com/owner/repo"));
        assertNull(service.parseOwnerRepo("https://example.com/whatever"));
    }

    @Test
    void returnsNullForBlankOrNull() {
        assertNull(service.parseOwnerRepo(null));
        assertNull(service.parseOwnerRepo("   "));
    }

    // ── totalFromLinkHeader ─────────────────────────────────────────────

    @Test
    void readsLastPageNumberFromLinkHeader() {
        String link = "<https://api.github.com/repositories/1/commits?per_page=1&page=2>; rel=\"next\", "
                + "<https://api.github.com/repositories/1/commits?per_page=1&page=137>; rel=\"last\"";
        assertEquals(137, GitHubRepoService.totalFromLinkHeader(link));
    }

    @Test
    void returnsZeroWhenNoLastRel() {
        // Single page of results has no Link header at all.
        assertEquals(0, GitHubRepoService.totalFromLinkHeader(null));
        assertEquals(0, GitHubRepoService.totalFromLinkHeader(""));
        // A header with only rel="next" but no rel="last" shouldn't be misread.
        assertEquals(0, GitHubRepoService.totalFromLinkHeader(
                "<https://api.github.com/x?page=2>; rel=\"next\""));
    }

    // ── scrub (anonymity) ───────────────────────────────────────────────

    @Test
    void scrubRemovesEmails() {
        String out = GitHubRepoService.scrub("Liên hệ tác giả: nguyen.van.a@fpt.edu.vn để biết thêm.");
        assertFalse(out.contains("@fpt.edu.vn"));
        assertTrue(out.contains("[email]"));
    }

    @Test
    void scrubRemovesHandles() {
        String out = GitHubRepoService.scrub("Made by @khanhnlh and @trang-nhk, thanks!");
        assertFalse(out.contains("@khanhnlh"));
        assertFalse(out.contains("@trang-nhk"));
        assertTrue(out.contains("[user]"));
    }

    @Test
    void scrubKeepsNormalText() {
        String out = GitHubRepoService.scrub("SmartDorm quản lý ký túc xá, backend Spring Boot.");
        assertTrue(out.contains("SmartDorm"));
        assertTrue(out.contains("Spring Boot"));
    }

    @Test
    void scrubTruncatesLongText() {
        String huge = "a".repeat(10_000);
        String out = GitHubRepoService.scrub(huge);
        assertTrue(out.length() < huge.length());
        assertTrue(out.contains("(truncated)"));
    }

    @Test
    void scrubHandlesNull() {
        assertNull(GitHubRepoService.scrub(null));
    }
}
