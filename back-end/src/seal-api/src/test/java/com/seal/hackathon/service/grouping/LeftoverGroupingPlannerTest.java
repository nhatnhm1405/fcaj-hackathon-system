package com.seal.hackathon.service.grouping;

import com.seal.hackathon.service.grouping.GroupingWarning.WarningType;
import com.seal.hackathon.service.grouping.ProposedTeam.TeamOrigin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeftoverGroupingPlannerTest {

    private static final int MIN = 3;
    private static final int MAX = 5;

    private final LeftoverGroupingPlanner planner = new LeftoverGroupingPlanner(MIN, MAX);

    // ── helpers ──────────────────────────────────────────────────────

    private static List<Atom> freeAgents(int n) {
        return IntStream.rangeClosed(1, n)
                .mapToObj(i -> Atom.freeAgent("U" + i))
                .collect(java.util.stream.Collectors.toList());
    }

    /** Every proposed team must respect the [MIN, MAX] size bounds. */
    private void assertAllTeamsWithinBounds(GroupingPlan plan) {
        for (ProposedTeam team : plan.teams()) {
            assertTrue(team.size() >= MIN && team.size() <= MAX,
                    "team " + team + " out of [" + MIN + "," + MAX + "]");
        }
    }

    /** Nobody is lost or duplicated: people in teams + people in warnings == input people. */
    private void assertPeopleConserved(GroupingPlan plan, int expectedPeople) {
        int inTeams = plan.teams().stream().mapToInt(ProposedTeam::size).sum()
                // subtract the seed head-count already counted inside settled/existing teams:
                - plan.teams().stream()
                        .filter(t -> t.origin() == TeamOrigin.EXISTING)
                        .mapToInt(t -> t.size() - t.addedRefs().size())
                        .sum();
        int inWarnings = plan.warnings().stream()
                .filter(w -> w.type() == WarningType.UNPLACEABLE_LEFTOVER)
                .mapToInt(GroupingWarning::peopleCount).sum();
        assertEquals(expectedPeople, inTeams + inWarnings,
                "people added to teams + unplaceable people must equal the leftover pool");
    }

    private List<Integer> teamSizes(GroupingPlan plan) {
        return plan.teams().stream().map(ProposedTeam::size).sorted().toList();
    }

    // ── empty ────────────────────────────────────────────────────────

    @Test
    void plan_shouldReturnEmpty_whenNoAtoms() {
        GroupingPlan plan = planner.plan(List.of(), List.of());
        assertTrue(plan.teams().isEmpty());
        assertFalse(plan.hasWarnings());
    }

    // ── free agents only: forming new teams ──────────────────────────

    @ParameterizedTest
    @CsvSource({"3", "4", "5"})
    void plan_shouldFormExactlyOneTeam_whenAgentsFitOneTeam(int n) {
        GroupingPlan plan = planner.plan(freeAgents(n), List.of());
        assertEquals(1, plan.totalProposedTeams());
        assertEquals(n, plan.teams().get(0).size());
        assertEquals(TeamOrigin.NEW, plan.teams().get(0).origin());
        assertFalse(plan.hasWarnings());
    }

    @Test
    void plan_shouldSplitEvenly_whenSevenAgents() {
        // 7 must become 4 + 3, never 5 + 2 (2 would be an invalid team).
        GroupingPlan plan = planner.plan(freeAgents(7), List.of());
        assertEquals(List.of(3, 4), teamSizes(plan));
        assertFalse(plan.hasWarnings());
        assertAllTeamsWithinBounds(plan);
    }

    @ParameterizedTest
    @CsvSource({"6, 3, 3", "8, 4, 4", "10, 5, 5", "11, 3, 4"})
    void plan_shouldBalanceNewTeams_whenManyAgents(int n, int smallest, int largest) {
        GroupingPlan plan = planner.plan(freeAgents(n), List.of());
        assertFalse(plan.hasWarnings());
        assertAllTeamsWithinBounds(plan);
        List<Integer> sizes = teamSizes(plan);
        assertEquals(smallest, sizes.get(0));
        assertEquals(largest, sizes.get(sizes.size() - 1));
        assertEquals(n, sizes.stream().mapToInt(Integer::intValue).sum());
    }

    // ── rescuing under-sized existing teams ──────────────────────────

    @Test
    void plan_shouldRescueDeficientTeamInPlace_whenAgentsAvailable() {
        // Team of 2 (a pair) + 1 free agent → grown in place to 3, no new team.
        GroupingPlan plan = planner.plan(
                List.of(Atom.existingTeam("T1", 2), Atom.freeAgent("U1")), List.of());

        assertEquals(1, plan.totalProposedTeams());
        ProposedTeam team = plan.teams().get(0);
        assertEquals(TeamOrigin.EXISTING, team.origin());
        assertEquals("T1", team.existingTeamRef());
        assertEquals(3, team.size());
        assertEquals(List.of("U1"), team.addedRefs());
        assertFalse(plan.hasWarnings());
    }

    @Test
    void plan_shouldRescueTeamsNeedingFewestFirst_whenAgentsScarce() {
        // 1 agent, two deficient teams (need 1 and need 2). The one needing 1 wins.
        GroupingPlan plan = planner.plan(
                List.of(Atom.existingTeam("T1", 1), Atom.existingTeam("T2", 2), Atom.freeAgent("U1")),
                List.of());

        assertEquals(1, plan.totalProposedTeams());
        assertEquals("T2", plan.teams().get(0).existingTeamRef());
        assertEquals(3, plan.teams().get(0).size());
        // T1 (still 1) surfaces as a soft warning, not a hard failure.
        assertEquals(1, plan.warnings().size());
        assertEquals(WarningType.DEFICIENT_TEAM_UNRESCUED, plan.warnings().get(0).type());
    }

    // ── spilling the final stragglers ────────────────────────────────

    @Test
    void plan_shouldSpillStragglersIntoSettledTeam_whenNewTeamsCannotFormThem() {
        // 2 free agents, no way to form a team of 3, but a settled team has 2 spare slots.
        GroupingPlan plan = planner.plan(freeAgents(2),
                List.of(new SettledTeam("T9", 2)));

        assertEquals(1, plan.totalProposedTeams());
        ProposedTeam team = plan.teams().get(0);
        assertEquals(TeamOrigin.EXISTING, team.origin());
        assertEquals("T9", team.existingTeamRef());
        assertEquals(2, team.addedRefs().size());
        assertFalse(plan.hasWarnings());
        assertPeopleConserved(plan, 2);
    }

    // ── dead-ends → warnings (correct behaviour) ─────────────────────

    @Test
    void plan_shouldWarn_whenLoneAgentAndAllTeamsFull() {
        // The "26 players, 5 full teams, 1 left over" dead-end.
        GroupingPlan plan = planner.plan(freeAgents(1),
                List.of(new SettledTeam("T1", 0), new SettledTeam("T2", 0)));

        assertTrue(plan.teams().isEmpty());
        assertEquals(1, plan.warnings().size());
        assertEquals(WarningType.UNPLACEABLE_LEFTOVER, plan.warnings().get(0).type());
        assertEquals(1, plan.warnings().get(0).peopleCount());
        assertPeopleConserved(plan, 1);
    }

    @Test
    void plan_shouldWarn_whenOnlyLonePairsWithNoFreeAgents() {
        // Three friend-pairs (2+2+2). Cannot split friends, cannot make a valid team
        // without exceeding MAX → three deficient-team warnings, zero teams.
        GroupingPlan plan = planner.plan(
                List.of(Atom.existingTeam("T1", 2), Atom.existingTeam("T2", 2), Atom.existingTeam("T3", 2)),
                List.of());

        assertTrue(plan.teams().isEmpty());
        assertEquals(3, plan.warnings().size());
        assertTrue(plan.warnings().stream()
                .allMatch(w -> w.type() == WarningType.DEFICIENT_TEAM_UNRESCUED));
    }

    // ── mixed, realistic ─────────────────────────────────────────────

    @Test
    void plan_shouldHandleMixOfDeficientTeamsAndAgents() {
        // 1 solo-team (1), 1 pair (2), 5 free agents = 8 leftover people.
        List<Atom> atoms = new ArrayList<>(List.of(
                Atom.existingTeam("T1", 1), Atom.existingTeam("T2", 2)));
        atoms.addAll(freeAgents(5));

        GroupingPlan plan = planner.plan(atoms, List.of());

        assertFalse(plan.hasWarnings());
        assertAllTeamsWithinBounds(plan);
        // Both existing teams are grown (kept), none dissolved.
        assertTrue(plan.teams().stream().anyMatch(t -> "T1".equals(t.existingTeamRef())));
        assertTrue(plan.teams().stream().anyMatch(t -> "T2".equals(t.existingTeamRef())));
        assertPeopleConserved(plan, 5); // 5 free agents distributed onto the 1+2 seeds
    }
}
