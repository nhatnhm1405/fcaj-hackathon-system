package com.seal.hackathon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.hackathon.dto.RepoDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a participant's GitHub repository via the GitHub REST API and distils it
 * into an anonymized {@link RepoDigest} for the AI Judge Assistant.
 *
 * Design notes:
 * - GitHub-only by design (owner/repo URLs). Non-GitHub or unparsable URLs return
 *   a digest with {@code analyzed=false} and a short note — never an exception.
 * - Never throws: any HTTP/parse failure degrades to {@code analyzed=false} so the
 *   assistant can still produce a text-only reading.
 * - Anonymity-safe: we deliberately do NOT read commit author names/emails, and we
 *   scrub the README of emails and @handles before it leaves this class.
 */
@Service
public class GitHubRepoService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.github.token:}")
    private String token;

    @Value("${app.github.api-base-url:https://api.github.com}")
    private String apiBaseUrl;

    /** Cap the README we forward to the model (chars). */
    private static final int README_MAX_CHARS = 4000;
    /** Cap how many tree entries we inspect (huge repos get truncated by GitHub anyway). */
    private static final int TREE_SCAN_LIMIT = 4000;
    private static final int TOP_LEVEL_LIMIT = 40;

    private static final Pattern GITHUB_URL = Pattern.compile(
            "github\\.com[/:]([\\w.-]+)/([\\w.-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    private static final Pattern HANDLE = Pattern.compile("(?<![\\w/])@[A-Za-z0-9_-]{2,}");

    /**
     * Build an anonymized digest of the given repo URL. Always returns a non-null
     * digest; on any problem {@code analyzed} is false and {@code note} explains why.
     */
    public RepoDigest analyze(String repoUrl) {
        String[] ownerRepo = parseOwnerRepo(repoUrl);
        if (ownerRepo == null) {
            return RepoDigest.builder()
                    .analyzed(false)
                    .note(isBlank(repoUrl)
                            ? "The submission has no repository link."
                            : "The repo link is not a GitHub URL, so the AI skipped source-code analysis.")
                    .build();
        }

        String owner = ownerRepo[0];
        String repo = ownerRepo[1];
        RestClient client = buildClient();

        // 1) Core metadata. If this fails, we can't do anything useful.
        JsonNode meta;
        try {
            meta = getJson(client, "/repos/" + owner + "/" + repo);
        } catch (RestClientResponseException e) {
            return RepoDigest.builder().analyzed(false).note(describeError(e)).build();
        } catch (Exception e) {
            return RepoDigest.builder().analyzed(false)
                    .note("Could not read the repo from GitHub (" + shorten(e.getMessage()) + ").").build();
        }
        if (meta == null) {
            return RepoDigest.builder().analyzed(false)
                    .note("GitHub returned no data for this repo.").build();
        }

        String defaultBranch = text(meta, "default_branch", "main");

        RepoDigest.RepoDigestBuilder b = RepoDigest.builder()
                .analyzed(true)
                .fullName(text(meta, "full_name", owner + "/" + repo))
                .description(emptyToNull(text(meta, "description", "")))
                .defaultBranch(defaultBranch)
                .fork(meta.path("fork").asBoolean(false))
                .archived(meta.path("archived").asBoolean(false))
                .sizeKb(meta.path("size").asLong(0))
                .license(licenseOf(meta))
                .topics(stringList(meta.path("topics")));

        // 2) Languages (best-effort).
        b.languages(fetchLanguages(client, owner, repo));

        // 3) File tree → structure signals (best-effort).
        applyTreeSignals(client, owner, repo, defaultBranch, b);

        // 4) README (best-effort, scrubbed).
        applyReadme(client, owner, repo, b);

        // 5) Commit count + date range, NO author info (best-effort).
        applyCommitStats(client, owner, repo, defaultBranch, b);

        return b.build();
    }

    // ── URL parsing ─────────────────────────────────────────────────────

    /** Returns {owner, repo} or null if the URL isn't a GitHub repo URL. */
    String[] parseOwnerRepo(String url) {
        if (isBlank(url)) return null;
        Matcher m = GITHUB_URL.matcher(url.trim());
        if (!m.find()) return null;
        String owner = m.group(1);
        String repo = m.group(2);
        if (repo.endsWith(".git")) repo = repo.substring(0, repo.length() - 4);
        // Strip anything after the repo segment that the regex might have captured edge cases of.
        if (owner.isBlank() || repo.isBlank()) return null;
        return new String[]{owner, repo};
    }

    // ── GitHub calls ────────────────────────────────────────────────────

    private RestClient buildClient() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("User-Agent", "SEAL-Hackathon-AI-Judge");
        if (!isBlank(token) && !token.startsWith("your-")) {
            builder.defaultHeader("Authorization", "Bearer " + token.trim());
        }
        return builder.build();
    }

    private JsonNode getJson(RestClient client, String path) throws Exception {
        String body = client.get().uri(path).retrieve().body(String.class);
        return body == null ? null : objectMapper.readTree(body);
    }

    private List<String> fetchLanguages(RestClient client, String owner, String repo) {
        try {
            JsonNode langs = getJson(client, "/repos/" + owner + "/" + repo + "/languages");
            if (langs == null || !langs.isObject()) return List.of();
            // languages come as {lang: bytes}; sort by bytes desc.
            List<String> names = new ArrayList<>();
            langs.fieldNames().forEachRemaining(names::add);
            names.sort((a, c) -> Long.compare(langs.path(c).asLong(), langs.path(a).asLong()));
            return names.size() > 8 ? names.subList(0, 8) : names;
        } catch (Exception e) {
            return List.of();
        }
    }

    private void applyTreeSignals(RestClient client, String owner, String repo,
                                  String branch, RepoDigest.RepoDigestBuilder b) {
        try {
            JsonNode tree = getJson(client,
                    "/repos/" + owner + "/" + repo + "/git/trees/" + branch + "?recursive=1");
            JsonNode nodes = tree == null ? null : tree.path("tree");
            if (nodes == null || !nodes.isArray() || nodes.isEmpty()) {
                b.fileCount(0).topLevelEntries(List.of());
                return;
            }

            boolean hasTests = false, hasCi = false, hasDocs = false, hasReadme = false;
            int mdCount = 0, fileCount = 0;
            Set<String> manifests = new LinkedHashSet<>();
            Set<String> topLevel = new LinkedHashSet<>();

            int scanned = 0;
            for (JsonNode n : nodes) {
                if (scanned++ >= TREE_SCAN_LIMIT) break;
                String path = n.path("path").asText("");
                String type = n.path("type").asText("");
                if (path.isEmpty()) continue;

                boolean isBlob = "blob".equals(type);
                if (isBlob) fileCount++;

                String lower = path.toLowerCase();
                String base = baseName(lower);

                if (!path.contains("/")) {
                    topLevel.add("tree".equals(type) ? path + "/" : path);
                }

                if (isTestPath(lower, base)) hasTests = true;
                if (isCiPath(lower)) hasCi = true;
                if (lower.startsWith("docs/")) hasDocs = true;
                if (base.equals("readme.md") || base.startsWith("readme.")) hasReadme = true;
                if (base.endsWith(".md")) mdCount++;

                String manifest = manifestName(base);
                if (manifest != null) manifests.add(manifest);
            }
            if (mdCount > 1) hasDocs = true;

            List<String> top = new ArrayList<>(topLevel);
            if (top.size() > TOP_LEVEL_LIMIT) top = top.subList(0, TOP_LEVEL_LIMIT);

            b.hasTests(hasTests).hasCi(hasCi).hasDocs(hasDocs).hasReadme(hasReadme)
             .fileCount(fileCount)
             .manifests(new ArrayList<>(manifests))
             .topLevelEntries(top);
        } catch (Exception e) {
            // Leave structure signals at defaults; not fatal.
            b.topLevelEntries(List.of());
        }
    }

    private void applyReadme(RestClient client, String owner, String repo,
                             RepoDigest.RepoDigestBuilder b) {
        try {
            JsonNode readme = getJson(client, "/repos/" + owner + "/" + repo + "/readme");
            if (readme == null) return;
            String encoded = readme.path("content").asText("");
            if (encoded.isBlank()) return;
            String decoded = new String(
                    Base64.getMimeDecoder().decode(encoded.replaceAll("\\s", "")),
                    StandardCharsets.UTF_8);
            b.hasReadme(true).readmeExcerpt(scrub(decoded));
        } catch (Exception e) {
            // No README or unreadable — fine.
        }
    }

    private void applyCommitStats(RestClient client, String owner, String repo,
                                  String branch, RepoDigest.RepoDigestBuilder b) {
        try {
            ResponseEntity<String> first = client.get()
                    .uri("/repos/" + owner + "/" + repo + "/commits?sha=" + branch + "&per_page=1")
                    .retrieve().toEntity(String.class);
            JsonNode arr = first.getBody() == null ? null : objectMapper.readTree(first.getBody());
            if (arr == null || !arr.isArray() || arr.isEmpty()) return;

            // Last (most recent) commit date is on page 1 — author info ignored on purpose.
            b.lastCommitDate(commitDate(arr.get(0)));

            int total = totalFromLinkHeader(first.getHeaders().getFirst("Link"));
            if (total <= 0) total = arr.size(); // single page → it's the count
            b.commitCount(total);

            // First commit date lives on the last page.
            if (total > 1) {
                JsonNode lastPage = getJson(client,
                        "/repos/" + owner + "/" + repo + "/commits?sha=" + branch
                        + "&per_page=1&page=" + total);
                if (lastPage != null && lastPage.isArray() && !lastPage.isEmpty()) {
                    b.firstCommitDate(commitDate(lastPage.get(0)));
                }
            } else {
                b.firstCommitDate(commitDate(arr.get(0)));
            }
        } catch (Exception e) {
            // Commit stats are best-effort.
        }
    }

    // ── Parsing helpers ─────────────────────────────────────────────────

    private static String commitDate(JsonNode commitNode) {
        // Use committer date (timestamp only); never read author name/email.
        String d = commitNode.path("commit").path("committer").path("date").asText("");
        if (d.isBlank()) d = commitNode.path("commit").path("author").path("date").asText("");
        return d.isBlank() ? null : (d.length() >= 10 ? d.substring(0, 10) : d);
    }

    /** Parse the {@code rel="last"} page number out of a GitHub Link header. */
    static int totalFromLinkHeader(String link) {
        if (link == null || link.isBlank()) return 0;
        Matcher m = Pattern.compile("[?&]page=(\\d+)[^>]*>;\\s*rel=\"last\"").matcher(link);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static boolean isTestPath(String lower, String base) {
        if (lower.contains("/test/") || lower.contains("/tests/")
                || lower.contains("/__tests__/") || lower.contains("/spec/")
                || lower.startsWith("test/") || lower.startsWith("tests/")) {
            return true;
        }
        return base.matches(".*\\.(test|spec)\\.[jt]sx?$")
                || base.matches(".*_test\\.(go|py)$")
                || base.matches("test_.*\\.py$")
                || base.matches(".*test\\.java$");
    }

    private static boolean isCiPath(String lower) {
        return lower.startsWith(".github/workflows/")
                || lower.equals(".gitlab-ci.yml")
                || lower.equals(".circleci/config.yml")
                || lower.startsWith(".circleci/")
                || lower.equals("azure-pipelines.yml")
                || lower.equals("jenkinsfile")
                || lower.equals(".travis.yml");
    }

    private static String manifestName(String base) {
        return switch (base) {
            case "package.json", "pom.xml", "build.gradle", "build.gradle.kts",
                 "requirements.txt", "pyproject.toml", "pipfile", "go.mod",
                 "cargo.toml", "composer.json", "gemfile", "pubspec.yaml" -> base;
            default -> base.endsWith(".csproj") ? base : null;
        };
    }

    private static String baseName(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? path : path.substring(i + 1);
    }

    private static String licenseOf(JsonNode meta) {
        JsonNode lic = meta.path("license");
        if (lic.isNull() || lic.isMissingNode()) return null;
        String spdx = lic.path("spdx_id").asText("");
        if (!spdx.isBlank() && !"NOASSERTION".equals(spdx)) return spdx;
        String name = lic.path("name").asText("");
        return name.isBlank() ? null : name;
    }

    private static List<String> stringList(JsonNode arr) {
        if (arr == null || !arr.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        arr.forEach(n -> out.add(n.asText()));
        return out;
    }

    /** Remove emails and @handles, then truncate. Keeps the README anonymity-safe. (package-private for tests) */
    static String scrub(String text) {
        if (text == null) return null;
        String cleaned = EMAIL.matcher(text).replaceAll("[email]");
        cleaned = HANDLE.matcher(cleaned).replaceAll("[user]");
        cleaned = cleaned.trim();
        if (cleaned.length() > README_MAX_CHARS) {
            cleaned = cleaned.substring(0, README_MAX_CHARS) + "\n…(truncated)";
        }
        return cleaned;
    }

    private String describeError(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String remaining = e.getResponseHeaders() == null ? null
                : e.getResponseHeaders().getFirst("X-RateLimit-Remaining");
        if ((status == 403 || status == 429) && "0".equals(remaining)) {
            return isBlank(token)
                    ? "Hit the GitHub API rate limit (no token configured). Set GITHUB_TOKEN in .env to raise the limit."
                    : "Hit the GitHub API rate limit. Please try again in a few minutes.";
        }
        return switch (status) {
            case 404 -> "The repo does not exist or is private (the current token cannot access it).";
            case 401, 403 -> "GitHub denied access to the repo (check GITHUB_TOKEN or the token's permissions).";
            default -> "GitHub returned error " + status + " while reading the repo.";
        };
    }

    // ── tiny utils ──────────────────────────────────────────────────────

    private static String text(JsonNode node, String field, String dflt) {
        String v = node.path(field).asText("");
        return v.isBlank() ? dflt : v;
    }

    private static String emptyToNull(String s) {
        return isBlank(s) ? null : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String shorten(String s) {
        if (s == null) return "unknown";
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() <= 120 ? one : one.substring(0, 120) + "…";
    }
}
