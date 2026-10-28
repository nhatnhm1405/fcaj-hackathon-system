package com.fcaj.hackathon.storage;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Component
@ConditionalOnProperty(name="app.storage.provider", havingValue="s3")
public class S3ObjectStorage implements ObjectStorage {
    private final S3Client client;
    private final String bucket;
    private final String prefix;
    public S3ObjectStorage(S3Client client, @Value("${app.storage.bucket}") String bucket,
                           @Value("${app.storage.prefix}") String prefix) {
        this.client = client;
        if (bucket.isBlank() || !bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"))
            throw new IllegalArgumentException("A private S3 bucket is required");
        this.bucket = bucket;
        this.prefix = ObjectKeys.valid(prefix.endsWith("/") ? prefix : prefix + "/");
    }
    private String key(String key) { return prefix + ObjectKeys.valid(key); }
    @Override public Ref put(String key, byte[] bytes, String type) {
        var response = client.putObject(PutObjectRequest.builder().bucket(bucket).key(key(key))
                .ifNoneMatch("*").contentType(type).metadata(java.util.Map.of("sha256", ObjectKeys.sha256(bytes))).build(),
                RequestBody.fromBytes(bytes));
        return new Ref(key, ObjectKeys.sha256(bytes), bytes.length, type, response.versionId());
    }
    @Override public byte[] read(String key, String version, long max) {
        if (max < 0 || max > Integer.MAX_VALUE - 1) throw new IllegalArgumentException("Invalid object limit");
        try (var stream = client.getObject(GetObjectRequest.builder().bucket(bucket).key(key(key)).versionId(version).build())) {
            if (stream.response().contentLength() > max) { stream.abort(); throw new IllegalArgumentException("Private object exceeds size limit"); }
            byte[] bytes = stream.readNBytes((int)max + 1);
            if (bytes.length > max) { stream.abort(); throw new IllegalArgumentException("Private object exceeds size limit"); }
            return bytes;
        } catch (IOException e) { throw new IllegalStateException("Private object read failed", e); }
    }
    @Override public void delete(String key, String version) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key(key)).versionId(version).build());
    }
}
