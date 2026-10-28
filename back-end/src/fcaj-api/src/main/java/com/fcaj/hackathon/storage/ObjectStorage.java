package com.fcaj.hackathon.storage;

/** Private immutable objects. A reference never contains a bucket, URL or credentials. */
public interface ObjectStorage {
    record Ref(String key, String sha256, long sizeBytes, String contentType, String versionId) {}
    Ref put(String key, byte[] content, String contentType);
    byte[] read(String key, String versionId, long maxBytes);
    void delete(String key, String versionId);
}
