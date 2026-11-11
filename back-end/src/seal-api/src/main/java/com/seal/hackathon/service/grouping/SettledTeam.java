package com.seal.hackathon.service.grouping;

/**
 * An existing team that is already valid (size &gt;= MIN) and therefore is not
 * regrouped. It only ever <em>absorbs</em> a leftover participant when nothing else
 * has room — the last-resort spill target (the instructor confirmed dropping a
 * stray person into an existing team, with a notification, is acceptable).
 *
 * @param ref  opaque identifier of the existing team
 * @param room free slots left before hitting MAX ({@code MAX - currentSize})
 */
public record SettledTeam(String ref, int room) {

    public SettledTeam {
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("SettledTeam ref is required");
        }
        if (room < 0) {
            throw new IllegalArgumentException("SettledTeam room must be >= 0 (got " + room + ")");
        }
    }
}
