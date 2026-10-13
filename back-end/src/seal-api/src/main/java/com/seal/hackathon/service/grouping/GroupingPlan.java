package com.seal.hackathon.service.grouping;

import java.util.List;

/**
 * The read-only output of {@link LeftoverGroupingPlanner}: the teams it proposes to
 * create/grow, plus any warnings the coordinator must resolve. Pure data — nothing
 * is persisted until a separate commit step acts on an (optionally edited) plan.
 */
public record GroupingPlan(List<ProposedTeam> teams, List<GroupingWarning> warnings) {

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public int totalProposedTeams() {
        return teams.size();
    }
}
