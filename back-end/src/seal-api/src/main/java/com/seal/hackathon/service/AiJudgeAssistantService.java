package com.seal.hackathon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.hackathon.dto.RepoDigest;
import com.seal.hackathon.dto.response.AiInsightResponse;
import com.seal.hackathon.entity.ScoringCriteria;
import com.seal.hackathon.entity.Submission;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ForbiddenException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.ScoringCriteriaRepository;
import com.seal.hackathon.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI Judge Assistant — generates an advisory reading of a submission to help a
 * judge orient before scoring. Backed by Google Gemini.
 *
 * Design constraints:
 * - Anonymity-safe: the prompt never includes team name or member identity, so
 *   it stays compatible with the anonymous judging flow.
 * - Advisory only: returns notes + suggested score RANGES; it never writes scores.
 */
@Service
@RequiredArgsConstructor
public class AiJudgeAssistantService {

    private static final String ROLE_EVENT_COORDINATOR = "ROLE_EVENT_COORDINATOR";
    private static final String ROLE_JUDGE = "ROLE_JUDGE";

    private final SubmissionRepository submissionRepository;
    private final ScoringCriteriaRepository scoringCriteriaRepository;
    private final GitHubRepoService gitHubRepoService;
    private final RoundTimerService roundTimerService;
    // Constructed directly (not injected): Spring Boot 4's webmvc starter does not
    // expose an ObjectMapper bean by default, and a plain mapper is all we need
    // for parsing Gemini's JSON. The initializer also excludes it from the
    // Lombok @RequiredArgsConstructor, so no bean lookup is attempted.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ai.gemini.api-key:}")
    private String apiKey;

    @Value("${app.ai.gemini.model:gemini-2.0-flash}")
    private String model;

    @Value("${app.ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    /** Transient errors (503/429) are retried this many times before failing. */
    private static final int MAX_ATTEMPTS = 3;

    private static final String DISCLAIMER =
            "AI (Gemini)-generated from the submission's description and links. "
            + "For reference only — it does not replace the judge's evaluation and does not auto-score.";

    @Transactional(readOnly = true)
    public AiInsightResponse analyzeSubmission(Integer requesterId, Set<String> authorities, Integer submissionId) {
        Submission submission;
        if (authorities != null && authorities.contains(ROLE_EVENT_COORDINATOR)) {
            submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + submissionId));
        } else if (authorities != null && authorities.contains(ROLE_JUDGE)) {
            submission = submissionRepository.findBySubmissionIdAndJudgeId(submissionId, requesterId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Submission not found or not assigned to this judge."));
            roundTimerService.assertJudgingOpen(submission.getRound().getRoundId());
        } else {
            throw new ForbiddenException("You do not have permission to analyze this submission.");
        }

        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("your-")) {
            throw new BadRequestException(
                    "AI Judge Assistant is not configured. Set GEMINI_API_KEY in the backend's .env file.");
        }

        if (isBlank(submission.getDescription())
                && isBlank(submission.getRepoUrl())
                && isBlank(submission.getDemoUrl())
                && isBlank(submission.getSlideUrl())) {
            throw new BadRequestException(
                    "This submission has no description or links for the AI to analyze.");
        }

        List<ScoringCriteria> criteria = scoringCriteriaRepository
                .findAllByRound_RoundIdOrderByOrderNumber(submission.getRound().getRoundId());

        // Read the participant's GitHub repo (anonymized). Never throws: on any
        // problem the digest reports analyzed=false and we fall back to text-only.
        RepoDigest digest = gitHubRepoService.analyze(submission.getRepoUrl());

        String prompt = buildPrompt(submission, criteria, digest);
        String rawJson = callGemini(prompt);
        AiInsightResponse insight = parseInsight(rawJson);
        insight.setRepo(buildRepoAnalysis(digest));
        insight.setDisclaimer(DISCLAIMER);
        insight.setModel(model);
        return insight;
    }

    // ── Repo analysis block (deterministic, built from real GitHub data) ──

    /**
     * Turn the GitHub digest into the response's {@code repo} block. These are
     * facts read straight from GitHub (not the model), so a judge can trust the
     * tech stack / signals / red flags without worrying about hallucination.
     */
    // Static + package-private: pure mapping from digest → response block, unit-tested directly.
    static AiInsightResponse.RepoAnalysis buildRepoAnalysis(RepoDigest d) {
        if (d == null || !d.isAnalyzed()) {
            return AiInsightResponse.RepoAnalysis.builder()
                    .analyzed(false)
                    .note(d == null ? "Could not analyze the repository." : d.getNote())
                    .techStack(List.of())
                    .signals(List.of())
                    .redFlags(List.of())
                    .build();
        }

        List<String> signals = new ArrayList<>();
        if (d.isHasReadme()) signals.add("Has a README");
        if (d.isHasTests()) signals.add("Has automated tests");
        if (d.isHasCi()) signals.add("Has CI/CD (automated build/test)");
        if (d.isHasDocs()) signals.add("Has documentation (a docs folder or multiple .md files)");
        if (d.getManifests() != null && !d.getManifests().isEmpty()) {
            signals.add("Dependency management: " + String.join(", ", d.getManifests()));
        }
        if (d.getLicense() != null) signals.add("License: " + d.getLicense());
        if (d.getCommitCount() != null) {
            signals.add(commitSignal(d));
        }
        if (d.getFileCount() > 0) signals.add(d.getFileCount() + " files in the repo");

        List<String> redFlags = new ArrayList<>();
        if (d.isFork()) {
            redFlags.add("The repo is a FORK — verify how much code the team actually wrote.");
        }
        if (d.isArchived()) {
            redFlags.add("The repo is ARCHIVED (frozen).");
        }
        if (d.getFileCount() <= 2 || d.getSizeKb() == 0) {
            redFlags.add("The repo is nearly EMPTY — very little source code.");
        }
        if (!d.isHasReadme()) {
            redFlags.add("No README describing the project.");
        }
        if (d.getCommitCount() != null && d.getCommitCount() <= 2) {
            redFlags.add("All code is squeezed into " + d.getCommitCount()
                    + " commit(s) — possibly a one-time upload that does not reflect the working process.");
        } else if (d.getCommitCount() != null && d.getCommitCount() > 2
                && d.getFirstCommitDate() != null
                && d.getFirstCommitDate().equals(d.getLastCommitDate())) {
            redFlags.add("All commits are on the same day (" + d.getLastCommitDate() + ").");
        }

        return AiInsightResponse.RepoAnalysis.builder()
                .analyzed(true)
                .note(null)
                .techStack(d.getLanguages() == null ? List.of() : d.getLanguages())
                .signals(signals)
                .redFlags(redFlags)
                .build();
    }

    private static String commitSignal(RepoDigest d) {
        StringBuilder sb = new StringBuilder(d.getCommitCount() + " commits");
        if (d.getFirstCommitDate() != null && d.getLastCommitDate() != null) {
            if (d.getFirstCommitDate().equals(d.getLastCommitDate())) {
                sb.append(" (").append(d.getLastCommitDate()).append(")");
            } else {
                sb.append(" (").append(d.getFirstCommitDate())
                  .append(" → ").append(d.getLastCommitDate()).append(")");
            }
        }
        return sb.toString();
    }

    // ── Prompt ──────────────────────────────────────────────────────────

    private String buildPrompt(Submission s, List<ScoringCriteria> criteria, RepoDigest digest) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a scoring assistant for a software-engineering hackathon. ")
          .append("Read the submission below — INCLUDING THE SOURCE-CODE DATA FROM THE REPOSITORY — and give an ")
          .append("objective, concise assessment in English to help the judge orient before scoring.\n\n")
          .append("IMPORTANT:\n")
          .append("- Judging is anonymous: do NOT guess or invent team names, member names, or schools. ")
          .append("If the README or code contains people's names, never repeat them in your answer.\n")
          .append("- Do NOT decide the final scores; only suggest reference score ranges.\n")
          .append("- Ground your reading in the real REPOSITORY DATA provided (folder structure, tech stack, README). ")
          .append("If the code does NOT seem to match the project description, raise it under 'concerns'.\n")
          .append("- If information is missing or the repo could not be read, say clearly that there is not enough data instead of making things up.\n\n");

        sb.append("SUBMISSION INFO\n");
        sb.append("Round: ").append(safe(s.getRound().getName())).append("\n");
        sb.append("Project description: ").append(isBlank(s.getDescription()) ? "(none)" : s.getDescription().trim()).append("\n");
        sb.append("Repository: ").append(isBlank(s.getRepoUrl()) ? "(none)" : s.getRepoUrl()).append("\n");
        sb.append("Demo: ").append(isBlank(s.getDemoUrl()) ? "(none)" : s.getDemoUrl()).append("\n");
        sb.append("Slides/Report: ").append(isBlank(s.getSlideUrl()) ? "(none)" : s.getSlideUrl()).append("\n\n");

        appendRepoSection(sb, digest);

        sb.append("SCORING CRITERIA FOR THIS ROUND\n");
        if (criteria.isEmpty()) {
            sb.append("(No criteria configured for this round — skip the per-criteria suggestions.)\n");
        } else {
            for (ScoringCriteria c : criteria) {
                sb.append("- ").append(c.getName())
                  .append(" (max score ").append(c.getMaxScore())
                  .append(", weight ").append(c.getWeight()).append("): ")
                  .append(isBlank(c.getDescription()) ? "" : c.getDescription().trim())
                  .append("\n");
            }
        }

        sb.append("\nRETURN ONLY a single JSON object with exactly this structure (no text outside the JSON):\n")
          .append("{\n")
          .append("  \"summary\": \"2-3 neutral sentences summarizing the project\",\n")
          .append("  \"strengths\": [\"strength 1\", \"strength 2\"],\n")
          .append("  \"concerns\": [\"a concern or a question to ask the team 1\", \"...\"],\n")
          .append("  \"criteriaInsights\": [\n")
          .append("    {\"criteriaName\": \"criteria name\", \"assessment\": \"short assessment for this criteria\", \"suggestedScoreRange\": \"e.g. 7-8 / 10\"}\n")
          .append("  ]\n")
          .append("}\n")
          .append("Each criteria above must have exactly one entry in criteriaInsights. ")
          .append("suggestedScoreRange must be within 0 to that criteria's max score. ")
          .append("Write all text values in English.");

        return sb.toString();
    }

    /** Append the anonymized GitHub repo digest so the model grounds its reading in real code. */
    private void appendRepoSection(StringBuilder sb, RepoDigest d) {
        sb.append("SOURCE-CODE DATA FROM THE REPOSITORY (anonymized — contains no author names)\n");
        if (d == null || !d.isAnalyzed()) {
            sb.append("(Could not read the source code: ")
              .append(d == null || isBlank(d.getNote()) ? "reason unknown" : d.getNote())
              .append(". Analysis is based only on the description and links.)\n\n");
            return;
        }

        if (!isBlank(d.getDescription())) {
            sb.append("GitHub description: ").append(d.getDescription().trim()).append("\n");
        }
        if (d.getLanguages() != null && !d.getLanguages().isEmpty()) {
            sb.append("Languages/tech stack: ").append(String.join(", ", d.getLanguages())).append("\n");
        }
        if (d.getManifests() != null && !d.getManifests().isEmpty()) {
            sb.append("Dependency manifests: ").append(String.join(", ", d.getManifests())).append("\n");
        }
        sb.append("Structure: ")
          .append(d.isHasTests() ? "has tests, " : "no tests found, ")
          .append(d.isHasCi() ? "has CI/CD, " : "no CI/CD, ")
          .append(d.isHasDocs() ? "has docs, " : "little/no docs, ")
          .append(d.isHasReadme() ? "has a README" : "no README").append("\n");
        if (d.isFork()) sb.append("NOTE: this repo is a FORK.\n");
        if (d.getCommitCount() != null) {
            sb.append("Commits: ").append(d.getCommitCount());
            if (d.getFirstCommitDate() != null && d.getLastCommitDate() != null) {
                sb.append(" (from ").append(d.getFirstCommitDate())
                  .append(" to ").append(d.getLastCommitDate()).append(")");
            }
            sb.append("\n");
        }
        if (d.getTopLevelEntries() != null && !d.getTopLevelEntries().isEmpty()) {
            sb.append("Top-level entries: ").append(String.join(", ", d.getTopLevelEntries())).append("\n");
        }
        if (!isBlank(d.getReadmeExcerpt())) {
            sb.append("\n--- README EXCERPT ---\n").append(d.getReadmeExcerpt()).append("\n--- END README ---\n");
        }
        sb.append("\n");
    }

    // ── Gemini call ─────────────────────────────────────────────────────

    private String callGemini(String prompt) {
        String url = baseUrl + "/models/" + model + ":generateContent?key=" + apiKey;

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.4,
                        "responseMimeType", "application/json"));

        // gemini-2.5-flash occasionally returns 503 (overloaded) or a transient
        // 429 under load. These usually clear in a second or two, so retry a few
        // times with a short backoff before giving up.
        String response = null;
        RestClientResponseException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                response = RestClient.create()
                        .post()
                        .uri(url)
                        .header("Content-Type", "application/json")
                        .body(body)
                        .retrieve()
                        .body(String.class);
                break; // success
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                // Only retry on 503 (transient overload). A 429 means the quota is
                // exhausted — retrying would just burn more quota without helping, so
                // fail fast and let the caller switch model / wait / enable billing.
                if (status == 503 && attempt < MAX_ATTEMPTS) {
                    sleepQuietly(attempt * 1500L);
                    lastError = e;
                    continue;
                }
                // Non-retryable, or out of attempts: surface a short, clear message.
                throw new BadRequestException(describeGeminiError(e));
            } catch (Exception e) {
                throw new BadRequestException("Could not reach the AI service (Gemini): " + e.getMessage());
            }
        }
        if (response == null) {
            // Exhausted retries on a transient error.
            throw new BadRequestException(describeGeminiError(lastError));
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode text = root.path("candidates").path(0)
                    .path("content").path("parts").path(0).path("text");
            if (text.isMissingNode() || text.asText().isBlank()) {
                throw new BadRequestException("The AI returned no content. Please try again.");
            }
            return text.asText();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("The AI returned an unreadable response. Please try again.");
        }
    }

    /** Turn a Gemini HTTP error into a short, user-facing hint. */
    private String describeGeminiError(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String apiMsg = null;
        try {
            JsonNode err = objectMapper.readTree(e.getResponseBodyAsString()).path("error");
            if (err.hasNonNull("message")) {
                apiMsg = err.path("message").asText();
            }
        } catch (Exception ignore) {
            // body wasn't JSON — fall through to the status-based message
        }

        return switch (status) {
            case 503 -> "Model \"" + model + "\" is overloaded (503). Please click AI ASSIST again in a few seconds, "
                    + "or change GEMINI_MODEL in .env (e.g. gemini-flash-latest).";
            case 429 -> "Gemini quota exceeded (429) for model \"" + model + "\". "
                    + "Change GEMINI_MODEL in .env (e.g. gemini-2.5-flash) or enable billing for the project.";
            case 404 -> "Model \"" + model + "\" is not available for this key (404). "
                    + "Change GEMINI_MODEL in .env to another model (e.g. gemini-2.5-flash).";
            case 400, 401, 403 -> (apiMsg != null && apiMsg.toLowerCase().contains("api key"))
                    ? "Invalid Gemini API key. Check GEMINI_API_KEY in .env."
                    : "Gemini rejected the request (" + status + "): " + shorten(apiMsg, 200);
            default -> "Gemini error (" + status + "): " + shorten(apiMsg, 200);
        };
    }

    private static String shorten(String s, int max) {
        if (s == null || s.isBlank()) return "no details";
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }

    private AiInsightResponse parseInsight(String json) {
        try {
            return objectMapper.readValue(json, AiInsightResponse.class);
        } catch (Exception e) {
            throw new BadRequestException("The AI returned invalid data. Please try again.");
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
