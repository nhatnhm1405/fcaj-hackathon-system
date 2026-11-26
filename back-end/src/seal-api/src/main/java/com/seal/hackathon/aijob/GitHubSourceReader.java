package com.seal.hackathon.aijob;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.*;
import java.time.Duration;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Public GitHub only. Fixed HTTPS hosts, no redirects, credentials, archives or code execution. */
@Component
@ConditionalOnProperty(name="app.ai.jobs.enabled", havingValue="true")
public class GitHubSourceReader {
    private final HttpClient client;
    private final Duration snapshotBudget;
    private final ThreadLocal<Long> budget = new ThreadLocal<>();
    public GitHubSourceReader() { this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()); }
    GitHubSourceReader(HttpClient client) { this(client,Duration.ofSeconds(45)); }
    GitHubSourceReader(HttpClient client,Duration snapshotBudget) { this.client=client; this.snapshotBudget=snapshotBudget; }
    @jakarta.annotation.PreDestroy
    void shutdown() { client.shutdownNow(); }
    static final int MAX_FILES=50;
    static final int MAX_FILE_BYTES=100_000;
    static final int MAX_TOTAL_BYTES=1_000_000;
    private static final Set<String> EXT=Set.of("java","kt","py","js","jsx","ts","tsx","json","md","txt","yml","yaml","sql","html","css","go","rs","c","cpp","h","cs","xml","toml");
    public record Source(String commitSha, ObjectNode document) {}
    static String repository(String url) {
        try {
            URI uri=URI.create(url);
            if(!"https".equals(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost()) || uri.getPort()!=-1
                    || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null) throw new IllegalArgumentException();
            String path=uri.getPath().replaceAll("/+$","").replaceAll("\\.git$","");
            if(!path.matches("/[a-zA-Z0-9_-]+/[a-zA-Z0-9_.-]+") || path.contains("..")) throw new IllegalArgumentException();
            return path.substring(1);
        } catch(Exception bad) { throw JobException.invalid("PUBLIC_GITHUB_REPOSITORY_REQUIRED"); }
    }
    JsonNode get(String path, int max) {
            long remaining=budget.get()==null?Duration.ofSeconds(15).toNanos():budget.get()-System.nanoTime();
            if(remaining<=0) throw BoundedHttp.timedOut();
            Duration timeout=Duration.ofNanos(Math.min(Duration.ofSeconds(15).toNanos(),remaining));
            var request=HttpRequest.newBuilder(URI.create("https://api.github.com"+path)).timeout(Duration.ofSeconds(15))
                    .header("Accept","application/vnd.github+json").header("X-GitHub-Api-Version","2022-11-28")
                    .header("User-Agent","fcaj-backend-snapshot").GET().build();
            return JobJson.parse(new String(BoundedHttp.fetch(client,request,max,timeout),StandardCharsets.UTF_8));
    }
    public Source collect(String url) {
        budget.set(System.nanoTime()+snapshotBudget.toNanos());
        try { return collectWithinBudget(url); }
        finally { budget.remove(); }
    }
    private Source collectWithinBudget(String url) {
        String repo=repository(url), base="/repos/"+repo;
        JsonNode commits=get(base+"/commits?per_page=1",32_768);
        String sha=commits.path(0).path("sha").asText();
        String treeSha=commits.path(0).path("commit").path("tree").path("sha").asText();
        if(!sha.matches("[0-9a-f]{40}") || !treeSha.matches("[0-9a-f]{40}")) throw JobException.invalid("INVALID_REPOSITORY_REVISION");
        JsonNode tree=get(base+"/git/trees/"+treeSha+"?recursive=1",1_000_000);
        if(!treeSha.equals(tree.path("sha").asText())) throw JobException.invalid("INVALID_REPOSITORY_REVISION");
        if(tree.path("truncated").asBoolean(true) || !tree.path("tree").isArray() || tree.path("tree").size()>2000)
            throw JobException.invalid("REPOSITORY_LIMIT_EXCEEDED");
        ObjectNode source=JobJson.object().put("schemaVersion","fcaj.ai.source.github.v1").put("fixtureOnly",false).put("commitSha",sha);
        var files=source.putArray("files"); int bytesTotal=0;
        var candidates=new ArrayList<JsonNode>();
        for(JsonNode entry:tree.path("tree")) {
            if("commit".equals(entry.path("type").asText()) || "160000".equals(entry.path("mode").asText()))
                throw JobException.invalid("SUBMODULES_NOT_SUPPORTED");
            String path=entry.path("path").asText();
            if(!"blob".equals(entry.path("type").asText()) || !"100644".equals(entry.path("mode").asText()) && !"100755".equals(entry.path("mode").asText())) continue;
            if(!selectedPath(path)) continue;
            // A file too large to send is skipped rather than failing the whole repository.
            if(entry.path("size").asLong(Long.MAX_VALUE)>MAX_FILE_BYTES) continue;
            candidates.add(entry);
        }
        var selected=selectMostRelevant(candidates);
        for(JsonNode entry:selected) {
            if(System.nanoTime()>budget.get()) throw BoundedHttp.timedOut();
            String blob=entry.path("sha").asText();
            if(!blob.matches("[0-9a-f]{40}")) throw JobException.invalid("INVALID_REPOSITORY_REVISION");
            JsonNode body=get(base+"/git/blobs/"+blob,150_000);
            if(!"base64".equals(body.path("encoding").asText())) throw JobException.invalid("INVALID_REPOSITORY_SOURCE");
            byte[] bytes;
            try { bytes=Base64.getDecoder().decode(body.path("content").asText().replace("\n", "").replace("\r", "")); }
            catch(IllegalArgumentException bad) { throw JobException.invalid("INVALID_REPOSITORY_SOURCE"); }
            bytesTotal+=bytes.length;
            if(bytes.length>MAX_FILE_BYTES || bytesTotal>MAX_TOTAL_BYTES) throw JobException.invalid("REPOSITORY_LIMIT_EXCEEDED");
            if(!blob.equals(gitBlobHash(bytes))) throw JobException.invalid("INVALID_REPOSITORY_SOURCE");
            String text;
            try { text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString(); }
            catch(CharacterCodingException bad) { continue; }
            if(text.indexOf('\0')>=0) continue;
            if(text.startsWith("version https://git-lfs.github.com/spec/")) throw JobException.invalid("GIT_LFS_NOT_SUPPORTED");
            files.addObject().put("path",entry.path("path").asText()).put("content",text);
        }
        if(files.isEmpty()) throw JobException.invalid("REPOSITORY_HAS_NO_SUPPORTED_SOURCE");
        return new Source(sha,source);
    }
    /**
     * Each file costs one GitHub API call, and anonymous calls are limited per hour, so a
     * large repository sends its most relevant files instead of being rejected: source code
     * and the root README first, then markup, then configuration and other text, shallow
     * paths before deep ones. The result is in path order.
     */
    static List<JsonNode> selectMostRelevant(List<JsonNode> candidates) {
        var ranked=new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingInt((JsonNode e) -> relevance(e.path("path").asText()))
                .thenComparingInt(e -> depth(e.path("path").asText()))
                .thenComparing(e -> e.path("path").asText()));
        var selected=new ArrayList<JsonNode>();
        long planned=0;
        for(JsonNode entry:ranked) {
            if(selected.size()>=MAX_FILES) break;
            long size=entry.path("size").asLong(Long.MAX_VALUE);
            if(planned+size>MAX_TOTAL_BYTES) continue;
            planned+=size;
            selected.add(entry);
        }
        selected.sort(Comparator.comparing(e -> e.path("path").asText()));
        return selected;
    }
    private static final Set<String> CODE=Set.of("java","kt","py","js","jsx","ts","tsx","go","rs","c","cpp","h","cs");
    private static final Set<String> MARKUP=Set.of("html","css","sql","xml");
    static int relevance(String path) {
        String lower=path.toLowerCase(Locale.ROOT);
        if(lower.equals("readme.md")) return 0;
        String ext=lower.substring(lower.lastIndexOf('.')+1);
        return CODE.contains(ext) ? 0 : MARKUP.contains(ext) ? 1 : 2;
    }
    private static int depth(String path) { return (int) path.chars().filter(c -> c=='/').count(); }
    static String gitBlobHash(byte[] bytes) {
        try {
            var digest=java.security.MessageDigest.getInstance("SHA-1");
            digest.update(("blob "+bytes.length+"\0").getBytes(StandardCharsets.US_ASCII));
            return java.util.HexFormat.of().formatHex(digest.digest(bytes));
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static boolean selectedPath(String path) {
        if(path.length()>300 || path.startsWith("/") || path.contains("..") || path.contains("\\") || path.chars().anyMatch(c -> c<32)) return false;
        String lower=path.toLowerCase(Locale.ROOT);
        if(Arrays.stream(lower.split("/")).anyMatch(p -> p.startsWith(".") || Set.of("node_modules","vendor","target","dist","build").contains(p))) return false;
        if(lower.endsWith("lock.json") || lower.endsWith("lock.yaml") || lower.endsWith("lock.yml")) return false;
        int dot=lower.lastIndexOf('.'); return dot>=0 && EXT.contains(lower.substring(dot+1));
    }
}
