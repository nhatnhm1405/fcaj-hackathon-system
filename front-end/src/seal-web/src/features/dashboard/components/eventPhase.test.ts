import { describe, expect, it } from "vitest";
import { contestTimerText, eventDateRange, phaseSummary, progressIndex, PROGRESS_STEPS, stepDates } from "./eventPhase";

const now = new Date("2026-11-24T10:00:00");
const ev = { registrationEnd: "2026-11-30T10:00:00", startDate: "2026-12-10T08:00:00", endDate: "2026-12-11T18:00:00" };

describe("phaseSummary", () => {
  it("counts down to the registration deadline", () => {
    expect(phaseSummary("OPEN", ev, now)).toBe("Registration open, 6 days left");
    expect(phaseSummary("OPEN", { ...ev, registrationEnd: "2026-11-25T09:00:00" }, now)).toBe("Registration open, 1 day left");
  });

  it("never claims registration is closed while the event is still OPEN", () => {
    expect(phaseSummary("OPEN", { ...ev, registrationEnd: "2026-11-20T10:00:00" }, now)).toBe("Registration open, accepting teams");
    expect(phaseSummary("OPEN", { ...ev, registrationEnd: "2026-11-24T20:00:00" }, now)).toBe("Registration open, closes today");
  });

  it("handles missing or invalid dates", () => {
    expect(phaseSummary("OPEN", {}, now)).toBe("Registration open, deadline TBA");
    expect(phaseSummary("IN_PROGRESS", { endDate: "nope" }, now)).toBe("Competition live");
  });

  it("describes the later phases", () => {
    expect(phaseSummary("SETUP", ev, now)).toBe("Setup phase, planned start Dec 10");
    expect(phaseSummary("IN_PROGRESS", ev, now)).toBe("Competition live, planned end Dec 11");
    expect(phaseSummary("COMPLETED", ev, now)).toBe("Event completed, ended Dec 11");
    expect(phaseSummary("CANCELLED", ev, now)).toBe("Event cancelled");
  });
});

describe("stepDates", () => {
  it("dates each step for the current phase", () => {
    expect(stepDates("OPEN", ev, now)).toEqual(["until Nov 30", "before Dec 10", "Dec 10 - 11", "after Dec 11"]);
    expect(stepDates("IN_PROGRESS", ev, now)).toEqual(["closed", "done", "Dec 10 - 11", "after Dec 11"]);
  });

  it("drops dates that already passed or are missing", () => {
    expect(stepDates("OPEN", { ...ev, registrationEnd: "2026-11-01T00:00:00", startDate: "2026-11-10T00:00:00" }, now))
      .toEqual(["", "", "Nov 10 - Dec 11", "after Dec 11"]);
    expect(stepDates("OPEN", {}, now)).toEqual(["", "", "", ""]);
  });
});

describe("contestTimerText", () => {
  it("formats each timer state", () => {
    expect(contestTimerText("RUNNING", 8076)).toBe("2:14:36 left to submit");
    expect(contestTimerText("RUNNING", 65)).toBe("01:05 left to submit");
    expect(contestTimerText("RUNNING", 0)).toBe("time is up");
    expect(contestTimerText("PAUSED", 65)).toBe("paused with 01:05 left");
    expect(contestTimerText("EXPIRED", 0)).toBe("time is up");
    expect(contestTimerText("IDLE", 0)).toBe("timer not started");
  });
});

describe("eventDateRange", () => {
  it("prints the year once unless the range crosses years", () => {
    expect(eventDateRange(ev)).toBe("Dec 10 - Dec 11, 2026");
    expect(eventDateRange({ startDate: "2026-12-30T00:00:00", endDate: "2027-01-02T00:00:00" })).toBe("Dec 30, 2026 - Jan 2, 2027");
    expect(eventDateRange({})).toBe("");
  });
});

describe("progressIndex", () => {
  it("maps each status to its step", () => {
    expect(["OPEN", "SETUP", "IN_PROGRESS"].map(progressIndex)).toEqual([0, 1, 2]);
    expect(progressIndex("COMPLETED")).toBe(PROGRESS_STEPS.length);
    expect(["DRAFT", "CANCELLED", "", null, undefined].map(progressIndex)).toEqual([-1, -1, -1, -1, -1]);
  });
});
