package com.seal.hackathon.service.grouping;

/**
 * An <b>atom</b> is an indivisible unit for leftover-team grouping: a group of
 * people that must stay together and can never be split across teams.
 *
 * <ul>
 *   <li>A lone participant (in no team) is a {@link #freeAgent} of size 1.</li>
 *   <li>An existing under-sized team (1 or 2 members) is an {@link #existingTeam}
 *       atom of that size — its members are friends kept together and the team is
 *       grown in place rather than dissolved.</li>
 * </ul>
 *
 * {@code ref} is an opaque identifier (e.g. {@code "U5"} for user 5, {@code "T3"}
 * for team 3). The pure planner never interprets it; the commit layer maps it back
 * to real entities. {@code size} is the number of <em>people</em> the atom carries.
 */
public record Atom(String ref, int size, boolean existingTeam) {

    public Atom {
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("Atom ref is required");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Atom size must be >= 1 (got " + size + ")");
        }
    }

    /** A single participant not yet in any team. */
    public static Atom freeAgent(String ref) {
        return new Atom(ref, 1, false);
    }

    /** An existing under-sized team (1-2 members) to be grown in place. */
    public static Atom existingTeam(String ref, int size) {
        return new Atom(ref, size, true);
    }
}
