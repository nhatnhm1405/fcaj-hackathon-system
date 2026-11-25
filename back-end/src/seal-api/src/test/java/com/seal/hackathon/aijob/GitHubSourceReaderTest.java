package com.seal.hackathon.aijob;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
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
        doReturn(utf8("text")).when(reader).raw(eq("example/project"),eq(sha),eq("src/main.java"),anyLong());
        var source=reader.collect("https://github.com/example/project");assertThat(source.commitSha()).isEqualTo(sha);
        assertThat(source.document().path("files").get(0).path("content").asText()).isEqualTo("text");
        assertThat(source.document().path("omittedFileCount").asInt()).isZero();
        verify(reader).get("/repos/example/project/git/trees/"+tree+"?recursive=1",1_000_000);
        verify(reader,times(2)).get(anyString(),anyInt());
    }
    @Test void truncatedRepositoriesAndSubmodulesAreRejected() {
        var reader=spy(new GitHubSourceReader(mock(java.net.http.HttpClient.class)));String tree="b".repeat(40);
        doReturn(JobJson.parse("[{\"sha\":\""+"a".repeat(40)+"\",\"commit\":{\"tree\":{\"sha\":\""+tree+"\"}}}]")).when(reader).get(contains("/commits?"),anyInt());
        doReturn(JobJson.parse("{\"sha\":\""+tree+"\",\"truncated\":true,\"tree\":[]}")).when(reader).get(contains("/git/trees/"),anyInt());
        assertThatThrownBy(() -> reader.collect("https://github.com/example/project")).hasMessage("REPOSITORY_LIMIT_EXCEEDED");
        doReturn(JobJson.parse("{\"sha\":\""+tree+"\",\"truncated\":false,\"tree\":[{\"type\":\"commit\",\"mode\":\"160000\"}]}")).when(reader).get(contains("/git/trees/"),anyInt());
        assertThatThrownBy(() -> reader.collect("https://github.com/example/project")).hasMessage("SUBMODULES_NOT_SUPPORTED");
    }
    private static final String SHA="a".repeat(40), TREE="c".repeat(40);
    private static byte[] utf8(String text) { return text.getBytes(java.nio.charset.StandardCharsets.UTF_8); }
    private static JsonNode entry(String path,long size,String blob) {
        return JobJson.object().put("path",path).put("type","blob").put("mode","100644").put("size",size).put("sha",blob);
    }
    private static GitHubSourceReader reader(List<JsonNode> entries) {
        var reader=spy(new GitHubSourceReader(mock(java.net.http.HttpClient.class)));
        doReturn(JobJson.parse("[{\"sha\":\""+SHA+"\",\"commit\":{\"tree\":{\"sha\":\""+TREE+"\"}}}]")).when(reader).get(contains("/commits?"),anyInt());
        var tree=JobJson.object().put("sha",TREE).put("truncated",false);tree.putArray("tree").addAll(entries);
        doReturn(tree).when(reader).get(contains("/git/trees/"),anyInt());
        return reader;
    }
    @Test void repositoriesOverFortyFilesUseOnlyTwoApiRequestsAndReportWhatWasLeftOut() {
        var entries=new ArrayList<JsonNode>();
        for(int i=0;i<60;i++) entries.add(entry("src/f"+i+".py",4,GitHubSourceReader.gitBlobHash(utf8("text"))));
        entries.add(entry("src/__init__.py",0,GitHubSourceReader.gitBlobHash(new byte[0])));
        entries.add(entry("data/huge.json",GitHubSourceReader.MAX_FILE_BYTES+1L,"b".repeat(40)));
        var reader=reader(entries);doReturn(utf8("text")).when(reader).raw(anyString(),anyString(),anyString(),anyLong());
        var document=reader.collect("https://github.com/example/project").document();
        assertThat(document.path("files")).hasSize(60);
        assertThat(document.path("omittedFileCount").asInt()).isEqualTo(1);
        verify(reader,times(2)).get(anyString(),anyInt());
        verify(reader,never()).raw(anyString(),anyString(),eq("data/huge.json"),anyLong());
    }
    @Test void limitsKeepCodeBeforeDocumentsAndSmallFilesBeforeLargeOnes() {
        var many=new ArrayList<JsonNode>();
        for(int i=0;i<GitHubSourceReader.MAX_FILES;i++) many.add(entry("docs/a"+i+".md",1,"b".repeat(40)));
        many.add(entry("web/App.tsx",90_000,"b".repeat(40)));
        var kept=GitHubSourceReader.withinLimits(many);
        assertThat(kept).hasSize(GitHubSourceReader.MAX_FILES).anyMatch(e->e.path("path").asText().equals("web/App.tsx"));
        var large=new ArrayList<JsonNode>();
        for(int i=0;i<20;i++) large.add(entry("api/big"+i+".py",90_000,"b".repeat(40)));
        large.add(entry("web/small.ts",100,"b".repeat(40)));
        kept=GitHubSourceReader.withinLimits(large);
        assertThat(kept).hasSize(12).anyMatch(e->e.path("path").asText().equals("web/small.ts"));
        assertThat(kept.stream().mapToLong(e->e.path("size").asLong()).sum()).isLessThanOrEqualTo(GitHubSourceReader.MAX_TOTAL_BYTES);
    }
    @Test void changedContentAndFailedDownloadsRejectTheWholeSnapshot() {
        var entries=List.of(entry("src/a.py",4,GitHubSourceReader.gitBlobHash(utf8("text"))),entry("src/b.py",4,GitHubSourceReader.gitBlobHash(utf8("text"))));
        var changed=reader(entries);doReturn(utf8("text")).when(changed).raw(anyString(),anyString(),eq("src/a.py"),anyLong());
        doReturn(utf8("evil")).when(changed).raw(anyString(),anyString(),eq("src/b.py"),anyLong());
        assertThatThrownBy(()->changed.collect("https://github.com/example/project")).hasMessage("INVALID_REPOSITORY_SOURCE");
        var failed=reader(entries);doReturn(utf8("text")).when(failed).raw(anyString(),anyString(),eq("src/a.py"),anyLong());
        doThrow(JobException.invalid("REPOSITORY_FETCH_UNAVAILABLE")).when(failed).raw(anyString(),anyString(),eq("src/b.py"),anyLong());
        assertThatThrownBy(()->failed.collect("https://github.com/example/project")).hasMessage("REPOSITORY_FETCH_UNAVAILABLE");
    }
    @Test void binaryFilesAreCountedAsLeftOut() {
        byte[] binary={'a',0,'b'};
        var reader=reader(List.of(entry("src/a.py",4,GitHubSourceReader.gitBlobHash(utf8("text"))),entry("src/b.py",3,GitHubSourceReader.gitBlobHash(binary))));
        doReturn(utf8("text")).when(reader).raw(anyString(),anyString(),eq("src/a.py"),anyLong());
        doReturn(binary).when(reader).raw(anyString(),anyString(),eq("src/b.py"),anyLong());
        var document=reader.collect("https://github.com/example/project").document();
        assertThat(document.path("files")).hasSize(1);assertThat(document.path("omittedFileCount").asInt()).isEqualTo(1);
    }
    @Test void rawDownloadsUseTheFixedHostThePinnedCommitAndAnEncodedPath() {
        var client=mock(java.net.http.HttpClient.class);var sent=new java.util.concurrent.atomic.AtomicReference<java.net.URI>();
        when(client.sendAsync(any(),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<byte[]>>any())).thenAnswer(c->{
            sent.set(c.<java.net.http.HttpRequest>getArgument(0).uri());
            return java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("stop"));
        });
        assertThatThrownBy(()->new GitHubSourceReader(client).raw("example/project",SHA,"src/a b/[id]#?%/t\u1ec7p.ts",System.nanoTime()+5_000_000_000L))
                .isInstanceOf(JobException.class);
        assertThat(sent.get().toString()).isEqualTo("https://raw.githubusercontent.com/example/project/"+SHA+"/src/a%20b/%5Bid%5D%23%3F%25/t%E1%BB%87p.ts");
        assertThatThrownBy(()->new GitHubSourceReader(client).raw("example/project",SHA,"a.py",System.nanoTime()-1)).hasMessage("REPOSITORY_FETCH_TIMEOUT");
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
