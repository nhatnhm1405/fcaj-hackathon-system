package com.fcaj.hackathon.storage;

import java.security.MessageDigest;
import java.util.HexFormat;

public final class ObjectKeys {
    private ObjectKeys() {}
    public static String valid(String key) {
        if (key == null || key.isBlank() || key.length() > 500 || key.startsWith("/")
                || key.contains("\\") || key.contains("..") || !key.matches("[a-zA-Z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid private object key");
        return key;
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
