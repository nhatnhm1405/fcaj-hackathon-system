import { describe, it, expect } from "vitest";
import {
  maxTeamsPerTrack,
  countAssigned,
  countUnassigned,
  teamsForTrack,
  isTrackValid,
  wouldExceedMax,
  canCompleteSetup,
  MIN_TEAMS_PER_TRACK,
  type TrackStatsTeam,
} from "@/features/events/trackStats";

describe("maxTeamsPerTrack", () => {
  it("rounds UP when teams do not divide evenly across tracks", () => {
    expect(maxTeamsPerTrack(12, 5)).toBe(3); // 12/5 = 2.4 → 3
    expect(maxTeamsPerTrack(10, 3)).toBe(4); // 10/3 = 3.33 → 4
    expect(maxTeamsPerTrack(5, 4)).toBe(2);  // 5/4  = 1.25 → 2 (matches screenshot)
  });

  it("returns the exact quotient when teams divide evenly", () => {
    expect(maxTeamsPerTrack(12, 4)).toBe(3);
    expect(maxTeamsPerTrack(9, 3)).toBe(3);
    expect(maxTeamsPerTrack(7, 7)).toBe(1);
  });

  it("returns 0 when there are no tracks (no division by zero)", () => {
    expect(maxTeamsPerTrack(10, 0)).toBe(0);
    expect(maxTeamsPerTrack(0, 0)).toBe(0);
    expect(maxTeamsPerTrack(10, -1)).toBe(0);
  });

  it("returns 0 when there are no teams", () => {
    expect(maxTeamsPerTrack(0, 4)).toBe(0);
  });
});

describe("countAssigned / countUnassigned", () => {
  const teams: TrackStatsTeam[] = [
    { trackId: 1 },
    { trackId: 2 },
    { trackId: null },
    { trackId: undefined },
    { trackId: 1 },
  ];

  it("splits teams by whether they have a track", () => {
    expect(countAssigned(teams)).toBe(3);
    expect(countUnassigned(teams)).toBe(2);
  });

  it("assigned + unassigned always equals the roster size", () => {
    expect(countAssigned(teams) + countUnassigned(teams)).toBe(teams.length);
  });

  it("handles an empty roster", () => {
    expect(countAssigned([])).toBe(0);
    expect(countUnassigned([])).toBe(0);
  });
});

describe("teamsForTrack", () => {
  it("returns only the teams placed into the given track", () => {
    const teams = [
      { trackId: 1, name: "Alpha" },
      { trackId: 2, name: "Beta" },
      { trackId: 1, name: "Gamma" },
      { trackId: null, name: "Delta" },
    ];
    expect(teamsForTrack(teams, 1).map(t => t.name)).toEqual(["Alpha", "Gamma"]);
    expect(teamsForTrack(teams, 2).map(t => t.name)).toEqual(["Beta"]);
    expect(teamsForTrack(teams, 99)).toEqual([]);
  });
});

describe("isTrackValid", () => {
  it("requires at least MIN_TEAMS_PER_TRACK teams", () => {
    expect(MIN_TEAMS_PER_TRACK).toBe(2);
    expect(isTrackValid(0)).toBe(false);
    expect(isTrackValid(1)).toBe(false);
    expect(isTrackValid(2)).toBe(true);
    expect(isTrackValid(5)).toBe(true);
  });
});

describe("wouldExceedMax", () => {
  it("flags a drop only when it pushes the track past max", () => {
    expect(wouldExceedMax(2, 3)).toBe(false); // 2 + 1 = 3, still within
    expect(wouldExceedMax(3, 3)).toBe(true);  // 3 + 1 = 4, over
    expect(wouldExceedMax(0, 1)).toBe(false);
    expect(wouldExceedMax(1, 1)).toBe(true);
  });

  it("never flags when max is unknown (0 or less)", () => {
    expect(wouldExceedMax(10, 0)).toBe(false);
    expect(wouldExceedMax(10, -1)).toBe(false);
  });
});

describe("canCompleteSetup", () => {
  it("allows when every track has >= 2 teams and nothing is unassigned", () => {
    const result = canCompleteSetup({
      tracks: [
        { trackId: 1, name: "AI", teamCount: 3 },
        { trackId: 2, name: "Web", teamCount: 2 },
      ],
      unassignedCount: 0,
    });
    expect(result.ok).toBe(true);
    expect(result.invalidTracks).toEqual([]);
    expect(result.reasons).toEqual([]);
  });

  it("blocks and lists tracks with fewer than 2 teams", () => {
    const result = canCompleteSetup({
      tracks: [
        { trackId: 1, name: "AI", teamCount: 3 },
        { trackId: 2, name: "Web", teamCount: 1 },
        { trackId: 3, name: "Green", teamCount: 0 },
      ],
      unassignedCount: 0,
    });
    expect(result.ok).toBe(false);
    expect(result.invalidTracks.map(t => t.name)).toEqual(["Web", "Green"]);
    expect(result.reasons).toHaveLength(2);
    expect(result.reasons[0]).toContain("Web");
  });

  it("blocks when teams are still unassigned", () => {
    const result = canCompleteSetup({
      tracks: [{ trackId: 1, name: "AI", teamCount: 4 }],
      unassignedCount: 3,
    });
    expect(result.ok).toBe(false);
    expect(result.reasons.some(r => r.includes("unassigned"))).toBe(true);
  });

  it("reports both invalid tracks and unassigned teams together", () => {
    const result = canCompleteSetup({
      tracks: [{ trackId: 1, name: "AI", teamCount: 1 }],
      unassignedCount: 2,
    });
    expect(result.ok).toBe(false);
    expect(result.reasons).toHaveLength(2);
  });

  it("blocks when there are no tracks at all", () => {
    const result = canCompleteSetup({ tracks: [], unassignedCount: 0 });
    expect(result.ok).toBe(false);
    expect(result.reasons[0]).toContain("at least one track");
  });
});
