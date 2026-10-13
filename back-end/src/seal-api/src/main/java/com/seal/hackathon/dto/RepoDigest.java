package com.seal.hackathon.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Anonymized, structured summary of a participant's GitHub repository, built by
 * {@code GitHubRepoService} and fed into the AI Judge Assistant prompt.
 *
 * Anonymity contract: this object MUST NOT carry any person/team identity. We
 * deliberately omit commit author names/emails and scrub the README of emails
 * and @handles, so the judging flow stays anonymous (see AiJudgeAssistantService).
 * Only technical facts about the code go in here.
 */
@Data
@Builder
public class RepoDigest {

    /** True when the repo was fetched and digested successfully. */
    private boolean analyzed;

    /** When {@link #analyzed} is false, a short human reason (non-GitHub, private/404, rate-limited…). */
    private String note;

    /** owner/repo, kept for the prompt's context (not an identity — it's the submission's own link). */
    private String fullName;

    /** Repo's own short description, if any. */
    private String description;

    /** Default branch name (e.g. "main"). */
    private String defaultBranch;

    /** True if this repo is a fork of another. */
    private boolean fork;

    /** True if the repo is archived. */
    private boolean archived;

    /** Repo size in KB (GitHub-reported). 0 / very small can signal an empty repo. */
    private long sizeKb;

    /** License SPDX id or name, if detected. */
    private String license;

    /** GitHub topics/tags set on the repo. */
    private List<String> topics;

    /** Languages by usage, most-used first (e.g. ["Java", "TypeScript"]). */
    private List<String> languages;

    /** Detected dependency/build manifests (e.g. "package.json", "pom.xml"). */
    private List<String> manifests;

    /** Total commits on the default branch (count only — no authors). */
    private Integer commitCount;

    /** ISO date of the first commit (timestamp only — no author). */
    private String firstCommitDate;

    /** ISO date of the most recent commit. */
    private String lastCommitDate;

    /** Whether the tree shows a test suite (test/ dirs, *.test.*, *_test.go, etc.). */
    private boolean hasTests;

    /** Whether CI config is present (.github/workflows, .gitlab-ci.yml, etc.). */
    private boolean hasCi;

    /** Whether docs beyond the README exist (docs/ folder, multiple .md files). */
    private boolean hasDocs;

    /** Whether a README was found. */
    private boolean hasReadme;

    /** Number of files in the tree (truncated count if the tree was large). */
    private int fileCount;

    /** A compact view of the top-level / notable directory layout for the prompt. */
    private List<String> topLevelEntries;

    /** README text, scrubbed of emails/@handles and truncated. May be null. */
    private String readmeExcerpt;
}
