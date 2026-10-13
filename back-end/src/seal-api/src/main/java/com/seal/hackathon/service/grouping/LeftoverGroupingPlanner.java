package com.seal.hackathon.service.grouping;

import com.seal.hackathon.service.grouping.GroupingWarning.WarningType;
import com.seal.hackathon.service.grouping.ProposedTeam.TeamOrigin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pure, DB-free algorithm that groups leftover people into valid teams before an
 * event leaves SETUP. Models the task as bin-packing of unsplittable {@link Atom}s
 * (size 1-2) into teams sized within [MIN, MAX], keeping friend groups intact.
 *
 * <p>The result is a {@link GroupingPlan} the coordinator reviews and (optionally
 * edits before) committing — the planner itself mutates nothing.
 *
 * <h3>Phases</h3>
 * <ol>
 *   <li><b>Rescue</b> — top up existing under-sized teams to MIN with free agents,
 *       prioritising the teams that need the fewest (maximises how many are saved).</li>
 *   <li><b>Form</b> — split the remaining free agents into brand-new teams, spread
 *       <em>evenly</em> so every new team lands within [MIN, MAX]
 *       ({@code k = ceil(F / MAX)} teams).</li>
 *   <li><b>Spill</b> — drop the final 1-2 stragglers into any team that still has
 *       room (a rescued/new team first, then a settled team).</li>
 *   <li><b>Warn</b> — whatever still cannot reach MIN or find room is handed to the
 *       coordinator as a {@link GroupingWarning} (e.g. the "26 players, all teams
 *       full" or "three lone pairs" dead-ends). This is correct, not a bug: those
 *       states are genuinely infeasible without splitting friends or exceeding MAX.</li>
 * </ol>
 *
 * The algorithm is deterministic (atoms processed in ref order) so a proposal is
 * reproducible and explainable to participants.
 */
public class LeftoverGroupingPlanner {

    /** Recommended minimum team size. There is no hard DB gate on this; it is the grouping goal. */
    public static final int DEFAULT_MIN = 3;
    /** Hard maximum team size — mirrors {@code MAX_TEAM_MEMBERS} enforced elsewhere. */
    public static final int DEFAULT_MAX = 5;

    private final int min;
    private final int max;

    public LeftoverGroupingPlanner() {
        this(DEFAULT_MIN, DEFAULT_MAX);
    }

    public LeftoverGroupingPlanner(int min, int max) {
        if (min < 1 || max < min) {
            throw new IllegalArgumentException("Require 1 <= min <= max (got min=" + min + ", max=" + max + ")");
        }
        this.min = min;
        this.max = max;
    }

    /**
     * @param atoms        leftover units to place (free agents + under-sized teams). Under-sized
     *                     teams must have {@code size < min}; a valid team belongs in {@code settledTeams}.
     * @param settledTeams already-valid existing teams, used only as last-resort spill targets.
     * @return the proposed grouping plus any unresolved warnings.
     */
    public GroupingPlan plan(List<Atom> atoms, List<SettledTeam> settledTeams) {
        if (atoms == null || atoms.isEmpty()) {
            return new GroupingPlan(List.of(), List.of());
        }

        // Under-sized existing teams become pre-seeded bins; free agents are the fillers.
        List<Bin> bins = new ArrayList<>();
        for (Atom seed : atoms.stream().filter(Atom::existingTeam)
                .sorted(Comparator.comparing(Atom::ref)).toList()) {
            bins.add(Bin.seeded(seed));
        }
        Deque<Atom> fillers = atoms.stream().filter(a -> !a.existingTeam())
                .sorted(Comparator.comparing(Atom::ref))
                .collect(Collectors.toCollection(ArrayDeque::new));

        // Valid existing teams are absorber-only bins (never rescued, never split).
        List<Bin> settledBins = new ArrayList<>();
        if (settledTeams != null) {
            for (SettledTeam st : settledTeams.stream()
                    .sorted(Comparator.comparing(SettledTeam::ref)).toList()) {
                settledBins.add(Bin.settled(st, max));
            }
        }

        rescueDeficientTeams(bins, fillers);
        formNewTeams(bins, fillers);
        List<String> unplaceable = spillStragglers(bins, settledBins, fillers);

        return build(bins, settledBins, unplaceable);
    }

    /** Phase 1: bring under-sized existing teams up to MIN, those needing the fewest first. */
    private void rescueDeficientTeams(List<Bin> bins, Deque<Atom> fillers) {
        bins.stream()
                .filter(b -> b.load < min)
                .sorted(Comparator.comparingInt(b -> min - b.load))
                .forEach(b -> {
                    while (b.load < min && !fillers.isEmpty()) {
                        b.add(fillers.poll());
                    }
                });
    }

    /**
     * Phase 2: split the remaining free agents into new teams, evenly so each lands
     * in [MIN, MAX]. Only runs when at least MIN agents remain; fewer are left for
     * the spill phase. With {@code k = ceil(F/MAX)} bins filled round-robin, every
     * bin is provably within [MIN, MAX].
     */
    private void formNewTeams(List<Bin> bins, Deque<Atom> fillers) {
        int f = fillers.size();
        if (f < min) {
            return;
        }
        int k = (int) Math.ceil((double) f / max);
        List<Bin> fresh = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            fresh.add(Bin.fresh());
        }
        int i = 0;
        while (!fillers.isEmpty()) {
            fresh.get(i % k).add(fillers.poll());
            i++;
        }
        bins.addAll(fresh);
    }

    /**
     * Phase 3: place the final 1-2 stragglers into any team with room (rescued/new
     * first, then a settled team). Returns the refs of any that still had nowhere to go.
     */
    private List<String> spillStragglers(List<Bin> bins, List<Bin> settledBins, Deque<Atom> fillers) {
        List<String> unplaceable = new ArrayList<>();
        while (!fillers.isEmpty()) {
            Atom straggler = fillers.poll();
            Bin target = firstWithRoom(bins);
            if (target == null) {
                target = firstWithRoom(settledBins);
            }
            if (target != null) {
                target.add(straggler);
            } else {
                unplaceable.add(straggler.ref());
            }
        }
        return unplaceable;
    }

    private Bin firstWithRoom(List<Bin> bins) {
        for (Bin b : bins) {
            if (b.load < max) {
                return b;
            }
        }
        return null;
    }

    /** Phase 4: turn the final bin states into proposed teams and warnings. */
    private GroupingPlan build(List<Bin> bins, List<Bin> settledBins, List<String> unplaceable) {
        List<ProposedTeam> teams = new ArrayList<>();
        List<GroupingWarning> warnings = new ArrayList<>();

        for (Bin b : bins) {
            if (b.load >= min) {
                if (!b.added.isEmpty()) {
                    teams.add(b.toProposedTeam());
                }
            } else if (b.seedRef != null) {
                warnings.add(new GroupingWarning(WarningType.DEFICIENT_TEAM_UNRESCUED,
                        b.memberRefs(), b.load,
                        "Team " + b.seedRef + " still has " + b.load
                                + " member(s), below the recommended " + min
                                + ". It may proceed as-is or be merged manually."));
            } else {
                warnings.add(new GroupingWarning(WarningType.UNPLACEABLE_LEFTOVER,
                        b.memberRefs(), b.load,
                        b.load + " leftover participant(s) could not form a full team."));
            }
        }

        for (Bin b : settledBins) {
            if (!b.added.isEmpty()) {
                teams.add(b.toProposedTeam());
            }
        }

        if (!unplaceable.isEmpty()) {
            warnings.add(new GroupingWarning(WarningType.UNPLACEABLE_LEFTOVER, unplaceable,
                    unplaceable.size(),
                    unplaceable.size() + " leftover participant(s) had no team with room; "
                            + "place them manually or approve a solo team."));
        }

        return new GroupingPlan(List.copyOf(teams), List.copyOf(warnings));
    }

    /** Mutable working bin. A team is its optional existing-team seed plus added atoms. */
    private static final class Bin {
        private final String seedRef;   // existing team ref, or null for a brand-new team
        private final int seedSize;     // people carried by the seed (0 for a new team)
        private final List<Atom> added = new ArrayList<>();
        private int load;               // total people currently in the bin

        private Bin(String seedRef, int seedSize) {
            this.seedRef = seedRef;
            this.seedSize = seedSize;
            this.load = seedSize;
        }

        static Bin seeded(Atom existingTeam) {
            return new Bin(existingTeam.ref(), existingTeam.size());
        }

        static Bin fresh() {
            return new Bin(null, 0);
        }

        static Bin settled(SettledTeam team, int max) {
            return new Bin(team.ref(), max - team.room());
        }

        void add(Atom atom) {
            added.add(atom);
            load += atom.size();
        }

        List<String> memberRefs() {
            List<String> refs = new ArrayList<>();
            if (seedRef != null) {
                refs.add(seedRef);
            }
            added.forEach(a -> refs.add(a.ref()));
            return refs;
        }

        ProposedTeam toProposedTeam() {
            TeamOrigin origin = seedRef != null ? TeamOrigin.EXISTING : TeamOrigin.NEW;
            List<String> addedRefs = added.stream().map(Atom::ref).toList();
            return new ProposedTeam(origin, seedRef, load, memberRefs(), addedRefs);
        }
    }
}
