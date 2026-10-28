package com.fcaj.hackathon.aijob;

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
}
