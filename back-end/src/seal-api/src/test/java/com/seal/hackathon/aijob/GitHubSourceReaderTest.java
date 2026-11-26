package com.seal.hackathon.aijob;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GitHubSourceReaderTest {
    @Test void budgetConsumedByCommitResolutionPreventsSendingTheTreeRequest() {
        var client=mock(java.net.http.HttpClient.class);
        var reader=spy(new GitHubSourceReader(client,java.time.Duration.ofMillis(10)));
        doAnswer(c->{Thread.sleep(30);return JobJson.parse("[{\"sha\":\""+"a".repeat(40)+"\",\"commit\":{\"tree\":{\"sha\":\""+"b".repeat(40)+"\"}}}]");})
                .when(reader).get(contains("/commits?"),anyInt());
        assertThatThrownBy(()->reader.collect("https://github.com/example/project")).hasMessage("REPOSITORY_FETCH_TIMEOUT");
        verifyNoInteractions(client);
    }
    @Test void onlyCanonicalPublicGithubUrlsAreAccepted() {
        assertThat(GitHubSourceReader.repository("https://github.com/example/project.git")).isEqualTo("example/project");
        for(String url:java.util.List.of("http://github.com/example/project","https://127.0.0.1/example/project","https://github.com:8443/example/project",
                "https://user:password@github.com/example/project","https://github.com/example/project?x=1","https://github.com/example/project/tree/main"))
            assertThatThrownBy(() -> GitHubSourceReader.repository(url)).isInstanceOf(JobException.class).hasMessageNotContaining("password");
    }
    @Test void collectorPinsFullShaAndSelectsBoundedTextWithoutExecutingAnything() {
        var reader=spy(new GitHubSourceReader(mock(java.net.http.HttpClient.class)));String sha="a".repeat(40),tree="c".repeat(40),blob=GitHubSourceReader.gitBlobHash("text".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        doReturn(JobJson.parse("[{\"sha\":\""+sha+"\",\"commit\":{\"tree\":{\"sha\":\""+tree+"\"}}}]")).when(reader).get(contains("/commits?"),anyInt());
        doReturn(JobJson.parse("{\"sha\":\""+tree+"\",\"truncated\":false,\"tree\":[{\"path\":\"src/main.java\",\"type\":\"blob\",\"mode\":\"100644\",\"size\":4,\"sha\":\""+blob+"\"}]}")).when(reader).get(contains("/git/trees/"),anyInt());
        doReturn(JobJson.parse("{\"encoding\":\"base64\",\"content\":\""+Base64.getEncoder().encodeToString("text".getBytes(java.nio.charset.StandardCharsets.UTF_8))+"\"}")).when(reader).get(contains("/git/blobs/"),anyInt());
        var source=reader.collect("https://github.com/example/project");assertThat(source.commitSha()).isEqualTo(sha);
        assertThat(source.document().path("files").get(0).path("content").asText()).isEqualTo("text");
        verify(reader).get("/repos/example/project/git/trees/"+tree+"?recursive=1",1_000_000);
    }
    @Test void truncatedRepositoriesAndSubmodulesAreRejected() {
        var reader=spy(new GitHubSourceReader(mock(java.net.http.HttpClient.class)));String tree="b".repeat(40);
        doReturn(JobJson.parse("[{\"sha\":\""+"a".repeat(40)+"\",\"commit\":{\"tree\":{\"sha\":\""+tree+"\"}}}]")).when(reader).get(contains("/commits?"),anyInt());
        doReturn(JobJson.parse("{\"sha\":\""+tree+"\",\"truncated\":true,\"tree\":[]}")).when(reader).get(contains("/git/trees/"),anyInt());
        assertThatThrownBy(() -> reader.collect("https://github.com/example/project")).hasMessage("REPOSITORY_LIMIT_EXCEEDED");
        doReturn(JobJson.parse("{\"sha\":\""+tree+"\",\"truncated\":false,\"tree\":[{\"type\":\"commit\",\"mode\":\"160000\"}]}")).when(reader).get(contains("/git/trees/"),anyInt());
        assertThatThrownBy(() -> reader.collect("https://github.com/example/project")).hasMessage("SUBMODULES_NOT_SUPPORTED");
    }
    @Test void secretsBuildOutputAndTraversalPathsAreExcluded() {
        for(String path:java.util.List.of(".env",".git/config","node_modules/example/index.js","dist/app.js","../src/main.java","config/.secret.json"))
            assertThat(GitHubSourceReader.selectedPath(path)).isFalse();
        assertThat(GitHubSourceReader.selectedPath("src/main/java/Main.java")).isTrue();
    }
    @Test void parserRejectsDuplicateFieldsAndTrailingJson() {
        assertThatThrownBy(() -> JobJson.parse("{\"status\":\"FAILED\",\"status\":\"SUCCEEDED\"}")).isInstanceOf(JobException.class);
        assertThatThrownBy(() -> JobJson.parse("{} {}" )).isInstanceOf(JobException.class);
    }
    private static com.fasterxml.jackson.databind.JsonNode entry(String path,long size) {
        return JobJson.parse("{\"path\":\""+path+"\",\"size\":"+size+"}");
    }
    private static java.util.List<String> paths(java.util.List<com.fasterxml.jackson.databind.JsonNode> entries) {
        return entries.stream().map(e -> e.path("path").asText()).toList();
    }
    @Test void largeRepositorySendsItsMostRelevantFilesInsteadOfFailing() {
        var candidates=new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        for(int i=0;i<40;i++) candidates.add(entry("app/module"+i+".py",1_000));
        for(int i=0;i<6;i++) candidates.add(entry("docs/note"+i+".md",1_000));
        for(int i=0;i<6;i++) candidates.add(entry("web/src/deep/page"+i+".tsx",1_000));
        candidates.add(entry("README.md",1_000));
        candidates.add(entry("web/index.html",1_000));
        candidates.add(entry("package.json",1_000));
        candidates.add(entry("requirements.txt",1_000));

        var selected=paths(GitHubSourceReader.selectMostRelevant(candidates));

        assertThat(selected).hasSize(GitHubSourceReader.MAX_FILES).isSorted()
                .contains("README.md","app/module0.py","web/src/deep/page5.tsx","web/index.html","requirements.txt")
                .doesNotContain("docs/note0.md");
    }
    @Test void selectionStaysWithinTheTotalSizeBudget() {
        var candidates=java.util.List.of(entry("a.py",600_000),entry("b.py",600_000),entry("c.py",300_000));
        assertThat(paths(GitHubSourceReader.selectMostRelevant(candidates))).containsExactly("a.py","c.py");
    }
    @Test void oversizedFilesAreSkippedNotFatal() {
        var reader=spy(new GitHubSourceReader(mock(java.net.http.HttpClient.class)));String sha="a".repeat(40),tree="c".repeat(40),blob=GitHubSourceReader.gitBlobHash("text".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        doReturn(JobJson.parse("[{\"sha\":\""+sha+"\",\"commit\":{\"tree\":{\"sha\":\""+tree+"\"}}}]")).when(reader).get(contains("/commits?"),anyInt());
        doReturn(JobJson.parse("{\"sha\":\""+tree+"\",\"truncated\":false,\"tree\":["
                +"{\"path\":\"data/huge.json\",\"type\":\"blob\",\"mode\":\"100644\",\"size\":500000,\"sha\":\""+"d".repeat(40)+"\"},"
                +"{\"path\":\"src/main.py\",\"type\":\"blob\",\"mode\":\"100644\",\"size\":4,\"sha\":\""+blob+"\"}]}")).when(reader).get(contains("/git/trees/"),anyInt());
        doReturn(JobJson.parse("{\"encoding\":\"base64\",\"content\":\""+Base64.getEncoder().encodeToString("text".getBytes(java.nio.charset.StandardCharsets.UTF_8))+"\"}")).when(reader).get(contains("/git/blobs/"),anyInt());
        var files=reader.collect("https://github.com/example/project").document().path("files");
        assertThat(files).hasSize(1);
        assertThat(files.get(0).path("path").asText()).isEqualTo("src/main.py");
        verify(reader,never()).get(contains("/git/blobs/"+"d".repeat(40)),anyInt());
    }
    @Test void sendsTheGitHubTokenOnlyWhenConfigured() {
        var client=mock(java.net.http.HttpClient.class);
        var anonymous=new GitHubSourceReader(client,java.time.Duration.ofSeconds(1),"  ");
        assertThat(anonymous.authenticated()).isFalse();
        assertThat(anonymous.request("/repos/a/b").headers().firstValue("Authorization")).isEmpty();

        var withToken=new GitHubSourceReader(client,java.time.Duration.ofSeconds(1)," github_pat_ABC123_xyz ");
        var request=withToken.request("/repos/a/b");
        assertThat(request.uri().getHost()).isEqualTo("api.github.com");
        assertThat(request.headers().firstValue("Authorization")).contains("Bearer github_pat_ABC123_xyz");
    }
    @Test void malformedTokensAreIgnored() {
        assertThat(GitHubSourceReader.usableToken(null)).isNull();
        assertThat(GitHubSourceReader.usableToken("")).isNull();
        assertThat(GitHubSourceReader.usableToken("abc\r\nX-Evil: 1")).isNull();
        assertThat(GitHubSourceReader.usableToken("ab cd")).isNull();
        assertThat(GitHubSourceReader.usableToken("ghp_abc-1.2")).isEqualTo("ghp_abc-1.2");
    }
}
