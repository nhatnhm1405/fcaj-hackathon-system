package com.seal.hackathon.service.grouping;

import java.util.List;

/**
 * A situation the planner could not resolve automatically and hands back to the
 * coordinator to settle manually in the review step. Warnings never block the
 * proposal — they are surfaced alongside it.
 *
 * @param type        the kind of unresolved situation
 * @param atomRefs    the atoms involved
 * @param peopleCount total people represented by {@code atomRefs}
 * @param message     human-readable explanation for the coordinator
 */
public record GroupingWarning(WarningType type,
                              List<String> atomRefs,
                              int peopleCount,
                              String message) {

    public enum WarningType {
        /**
         * Leftover participant(s) that could neither form a valid team nor be
         * dropped into one with room (every team is full). Coordinator options:
         * approve a solo team, hand-place, or knowingly exceed capacity.
         */
        UNPLACEABLE_LEFTOVER,

        /**
         * An existing under-sized team (1-2 members) that ran out of free agents to
         * reach the recommended minimum. There is no hard min-member gate, so it may
         * simply proceed as-is, or the coordinator may merge it manually.
         */
        DEFICIENT_TEAM_UNRESCUED
    }
}
