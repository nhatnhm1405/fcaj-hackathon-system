package com.seal.hackathon.aijob;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Public GitHub only. Fixed HTTPS hosts, no redirects, credentials, archives or code execution.
 * Two API requests per snapshot (commit, tree) because unauthenticated clients share 60 per hour per IP address.
 * File bodies come from the raw host, pinned to the commit and checked against the blob hashes of the tree.
 */
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
    static final int MAX_FILES=300, MAX_FILE_BYTES=100_000, MAX_TOTAL_BYTES=1_000_000;
    private static final int PARALLEL_DOWNLOADS=6;
    /** Documents and data give way to code when a repository exceeds the limits. */
    private static final Set<String> SECONDARY=Set.of("md","txt","json","yml","yaml","xml","toml");
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
        var candidates=new ArrayList<JsonNode>();
        for(JsonNode entry:tree.path("tree")) {
            if("commit".equals(entry.path("type").asText()) || "160000".equals(entry.path("mode").asText()))
                throw JobException.invalid("SUBMODULES_NOT_SUPPORTED");
            if(!"blob".equals(entry.path("type").asText()) || !"100644".equals(entry.path("mode").asText()) && !"100755".equals(entry.path("mode").asText())) continue;
            if(!selectedPath(entry.path("path").asText()) || entry.path("size").asLong(Long.MAX_VALUE)==0) continue;
            if(!entry.path("sha").asText().matches("[0-9a-f]{40}")) throw JobException.invalid("INVALID_REPOSITORY_REVISION");
            candidates.add(entry);
        }
        var selected=withinLimits(candidates);
        // Files left out by a limit are counted so that the reader never claims to have seen the whole repository.
        int omitted=candidates.size()-selected.size();
        var bodies=download(repo,sha,selected);
        ObjectNode source=JobJson.object().put("schemaVersion","fcaj.ai.source.github.v1").put("fixtureOnly",false).put("commitSha",sha);
        var files=source.putArray("files");
        for(int i=0;i<selected.size();i++) {
            byte[] bytes=bodies.get(i);
            if(!selected.get(i).path("sha").asText().equals(gitBlobHash(bytes))) throw JobException.invalid("INVALID_REPOSITORY_SOURCE");
            String text;
            try { text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString(); }
            catch(CharacterCodingException bad) { omitted++; continue; }
            if(text.indexOf('\0')>=0) { omitted++; continue; }
            if(text.startsWith("version https://git-lfs.github.com/spec/")) throw JobException.invalid("GIT_LFS_NOT_SUPPORTED");
            files.addObject().put("path",selected.get(i).path("path").asText()).put("content",text);
        }
        if(files.isEmpty()) throw JobException.invalid("REPOSITORY_HAS_NO_SUPPORTED_SOURCE");
        source.put("omittedFileCount",omitted);
        return new Source(sha,source);
    }
    /** Code first, then small files first, so that one large part cannot push out the others. Result is sorted by path. */
    static List<JsonNode> withinLimits(List<JsonNode> candidates) {
        var ranked=new ArrayList<>(candidates);
        ranked.sort(Comparator.comparing((JsonNode e) -> SECONDARY.contains(extension(e.path("path").asText())))
                .thenComparingLong(e -> e.path("size").asLong(Long.MAX_VALUE)).thenComparing(e -> e.path("path").asText()));
        var selected=new ArrayList<JsonNode>(); long total=0;
        for(JsonNode entry:ranked) {
            long size=entry.path("size").asLong(Long.MAX_VALUE);
            if(size>MAX_FILE_BYTES || selected.size()>=MAX_FILES || total+size>MAX_TOTAL_BYTES) continue;
            total+=size; selected.add(entry);
        }
        selected.sort(Comparator.comparing(e -> e.path("path").asText()));
        return selected;
    }
    private List<byte[]> download(String repo,String sha,List<JsonNode> selected) {
        long deadline=budget.get();
        var pool=Executors.newFixedThreadPool(PARALLEL_DOWNLOADS,Thread.ofVirtual().factory());
        try {
            var results=new ExecutorCompletionService<byte[]>(pool);
            var pending=new ArrayList<Future<byte[]>>();
            for(JsonNode entry:selected) pending.add(results.submit(() -> raw(repo,sha,entry.path("path").asText(),deadline)));
            // Completion order: the first failure stops the remaining downloads.
            for(int i=0;i<pending.size();i++) results.take().get();
            var bodies=new ArrayList<byte[]>();
            for(var body:pending) bodies.add(body.resultNow());
            return bodies;
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw JobException.invalid("REPOSITORY_FETCH_UNAVAILABLE");
        } catch(ExecutionException e) {
            if(e.getCause() instanceof JobException job) throw job;
            throw JobException.invalid("REPOSITORY_FETCH_UNAVAILABLE");
        } finally { pool.shutdownNow(); }
    }
    byte[] raw(String repo,String sha,String path,long deadline) {
        long remaining=deadline-System.nanoTime();
        if(remaining<=0) throw BoundedHttp.timedOut();
        Duration timeout=Duration.ofNanos(Math.min(Duration.ofSeconds(15).toNanos(),remaining));
        String encoded=URLEncoder.encode(path,StandardCharsets.UTF_8).replace("+","%20").replace("%2F","/");
        var request=HttpRequest.newBuilder(URI.create("https://raw.githubusercontent.com/"+repo+"/"+sha+"/"+encoded))
                .timeout(Duration.ofSeconds(15)).header("User-Agent","fcaj-backend-snapshot").GET().build();
        return BoundedHttp.fetch(client,request,MAX_FILE_BYTES,timeout);
    }
    private static String extension(String path) {
        return path.substring(path.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
    }
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
