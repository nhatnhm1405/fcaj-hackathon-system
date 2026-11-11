package com.seal.hackathon.service.grouping;

import java.util.List;

/**
 * One team in a grouping proposal. Either a brand-new team ({@link TeamOrigin#NEW})
 * or an existing team that gained members ({@link TeamOrigin#EXISTING}).
 *
 * @param origin         whether the team is created fresh or grown from an existing one
 * @param existingTeamRef ref of the existing team when {@code origin == EXISTING}, else {@code null}
 * @param size           final head-count (people), always within [MIN, MAX]
 * @param memberRefs     every atom ref in the team (existing-team seed first, then added)
 * @param addedRefs      only the atom refs newly added by grouping — the audience to notify
 */
public record ProposedTeam(TeamOrigin origin,
                           String existingTeamRef,
                           int size,
                           List<String> memberRefs,
                           List<String> addedRefs) {

    public enum TeamOrigin { NEW, EXISTING }
}
