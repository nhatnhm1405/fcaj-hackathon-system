package com.fcaj.hackathon.aijob;

/** Protocol byte limits shared by producer, completion consumer and worker. */
public final class JobLimits {
    private JobLimits() {}
    public static final int RUBRIC = 262_144, MANIFEST = 262_144, OUTPUT = 262_144;
    public static final int SOURCE = 8_000_000, MESSAGE = 32_768;
    static byte[] checked(byte[] bytes, int max, String code) {
        if (bytes.length > max) throw JobException.invalid(code);
        return bytes;
    }
}
