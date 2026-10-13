package com.seal.hackathon.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Dry-run of leftover-team grouping the coordinator reviews before committing.
 * Nothing is persisted to produce this — it mirrors exactly what {@code commit} will do.
 */
@Getter
@Builder
public class GroupingPreviewResponse {

    /** Total leftover people considered (members of every under-sized team). */
    private int leftoverPeople;
    /** Teams that are a single lone participant (treated as movable free agents). */
    private int soloCount;
    /** Teams of two (kept together as one unit). */
    private int pairCount;

    private List<ProposedTeamView> proposedTeams;
    private List<WarningView> warnings;

    @Getter
    @Builder
    public static class ProposedTeamView {
        /** "NEW" (created on commit) or "EXISTING" (an existing team grown in place). */
        private String origin;
        /** Existing team id when {@code origin == EXISTING}, else null. */
        private Integer existingTeamId;
        /** Existing team name, or a placeholder for a to-be-created team. */
        private String teamName;
        private int size;
        private List<MemberView> members;
        /** Only the members newly added by grouping — the audience notified on commit. */
        private List<MemberView> addedMembers;
    }

    @Getter
    @Builder
    public static class WarningView {
        /** UNPLACEABLE_LEFTOVER or DEFICIENT_TEAM_UNRESCUED. */
        private String type;
        private int peopleCount;
        private String message;
        private List<MemberView> people;
    }

    @Getter
    @Builder
    public static class MemberView {
        private Integer userId;
        private String fullName;
    }
}
