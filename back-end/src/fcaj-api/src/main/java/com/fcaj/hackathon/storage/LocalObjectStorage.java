package com.fcaj.hackathon.storage;

import java.io.IOException;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.storage.provider", havingValue="local", matchIfMissing=true)
public class LocalObjectStorage implements ObjectStorage {
    private final Path root;
    public LocalObjectStorage(@Value("${app.problem.dir:protected/problems}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }
    private Path path(String key) {
        Path path = root.resolve(ObjectKeys.valid(key)).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Invalid private object key");
        return path;
    }
    @Override public Ref put(String key, byte[] bytes, String type) {
        Path path = path(key);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
            return new Ref(key, ObjectKeys.sha256(bytes), bytes.length, type, null);
        } catch (IOException e) { throw new IllegalStateException("Private object write failed", e); }
    }
    @Override public byte[] read(String key, String version, long max) {
        if (version != null) throw new IllegalArgumentException("Local objects do not have versions");
        try (var input = Files.newInputStream(path(key))) {
            if (max < 0 || max > Integer.MAX_VALUE - 1 || Files.size(path(key)) > max)
                throw new IllegalArgumentException("Private object exceeds size limit");
            byte[] bytes = input.readNBytes((int)max + 1);
            if (bytes.length > max) throw new IllegalArgumentException("Private object exceeds size limit");
            return bytes;
        } catch (IOException e) { throw new IllegalStateException("Private object read failed", e); }
    }
    @Override public void delete(String key, String version) {
        try { Files.deleteIfExists(path(key)); }
        catch (IOException e) { throw new IllegalStateException("Private object delete failed", e); }
    }
}
