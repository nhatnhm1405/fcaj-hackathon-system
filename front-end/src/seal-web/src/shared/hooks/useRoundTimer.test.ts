import { describe, it, expect, vi, beforeEach, afterEach, type Mock } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { useRoundTimer } from "@/shared/hooks/useRoundTimer";
import { timersApi, type RoundTimerState } from "@/shared/apiClient";

vi.mock("@/shared/apiClient", () => ({
  timersApi: { get: vi.fn() },
}));

const addToast = vi.fn();
vi.mock("@/app/providers/NotificationProvider", () => ({
  useNotifications: () => ({ addToast }),
}));

function makeState(overrides: Partial<RoundTimerState> = {}): RoundTimerState {
  return {
    roundId: 5,
    phase: "CONTEST",
    status: "IDLE",
    durationSeconds: null,
    startedAt: null,
    endsAt: null,
    remainingSeconds: 0,
    serverNow: new Date().toISOString(),
    milestoneMinutes: null,
    notifyAtHalf: null,
    ...overrides,
  };
}

beforeEach(() => {
  (timersApi.get as Mock).mockReset();
  addToast.mockReset();
});

afterEach(() => {
  vi.useRealTimers();
});

describe("useRoundTimer — disabled", () => {
  it("does not call the API and reports IDLE when eventId is missing", () => {
    const { result } = renderHook(() => useRoundTimer(null, 5, "CONTEST"));
    expect(timersApi.get).not.toHaveBeenCalled();
    expect(result.current.status).toBe("IDLE");
    expect(result.current.loading).toBe(false);
    expect(result.current.isConfigured).toBe(false);
  });

  it("does not call the API when roundId is missing", () => {
    renderHook(() => useRoundTimer(1, undefined, "CONTEST"));
    expect(timersApi.get).not.toHaveBeenCalled();
  });

  it("does not call the API when explicitly disabled via options", () => {
    renderHook(() => useRoundTimer(1, 5, "CONTEST", { enabled: false }));
    expect(timersApi.get).not.toHaveBeenCalled();
  });
});

describe("useRoundTimer — fetched state", () => {
  it("computes remainingSeconds from endsAt while RUNNING", async () => {
    const now = new Date("2026-01-01T00:00:00.000Z");
    const endsAt = new Date(now.getTime() + 90_000).toISOString();
    (timersApi.get as Mock).mockResolvedValue({
      data: makeState({ status: "RUNNING", durationSeconds: 120, startedAt: now.toISOString(), endsAt, serverNow: now.toISOString() }),
    });

    const { result } = renderHook(() => useRoundTimer(1, 5, "CONTEST"));

    await waitFor(() => expect(result.current.status).toBe("RUNNING"));
    expect(result.current.isRunning).toBe(true);
    expect(result.current.isConfigured).toBe(true);
    expect(result.current.isPaused).toBe(false);
    expect(result.current.remainingSeconds).toBeGreaterThan(85);
    expect(result.current.remainingSeconds).toBeLessThanOrEqual(90);
    expect(result.current.loading).toBe(false);
    expect(timersApi.get).toHaveBeenCalledWith(1, 5, "CONTEST");
  });

  it("reads remainingSeconds directly (not from endsAt) while PAUSED", async () => {
    (timersApi.get as Mock).mockResolvedValue({
      data: makeState({ status: "PAUSED", durationSeconds: 120, remainingSeconds: 42, endsAt: null }),
    });

    const { result } = renderHook(() => useRoundTimer(1, 5, "CONTEST"));

    await waitFor(() => expect(result.current.status).toBe("PAUSED"));
    expect(result.current.isPaused).toBe(true);
    expect(result.current.isRunning).toBe(false);
    expect(result.current.remainingSeconds).toBe(42);
  });

  it("reports isExpired for an EXPIRED state regardless of remaining", async () => {
    (timersApi.get as Mock).mockResolvedValue({
      data: makeState({ status: "EXPIRED", durationSeconds: 120, remainingSeconds: 0 }),
    });

    const { result } = renderHook(() => useRoundTimer(1, 5, "CONTEST"));

    await waitFor(() => expect(result.current.status).toBe("EXPIRED"));
    expect(result.current.isExpired).toBe(true);
    expect(result.current.isRunning).toBe(false);
  });

  it("clears state and sets loadFailed when the fetch rejects", async () => {
    (timersApi.get as Mock).mockRejectedValue(new Error("network down"));

    const { result } = renderHook(() => useRoundTimer(1, 5, "CONTEST"));

    await waitFor(() => expect(result.current.loadFailed).toBe(true));
    expect(result.current.status).toBe("IDLE");
    expect(result.current.loading).toBe(false);
  });

  it("refetch() re-queries the API and applies the new state", async () => {
    (timersApi.get as Mock).mockResolvedValueOnce({ data: makeState({ status: "PAUSED", remainingSeconds: 10 }) });
    const { result } = renderHook(() => useRoundTimer(1, 5, "CONTEST"));
    await waitFor(() => expect(result.current.status).toBe("PAUSED"));

    (timersApi.get as Mock).mockResolvedValueOnce({ data: makeState({ status: "RUNNING", remainingSeconds: 99, endsAt: new Date(Date.now() + 99_000).toISOString() }) });
    act(() => { result.current.refetch(); });

    await waitFor(() => expect(result.current.status).toBe("RUNNING"));
    expect(timersApi.get).toHaveBeenCalledTimes(2);
  });
});

describe("useRoundTimer — milestone banners (fireBanners)", () => {
  it("fires a milestone banner exactly once when remaining crosses the threshold", async () => {
    vi.useFakeTimers();
    const start = new Date("2026-01-01T00:00:00.000Z");
    vi.setSystemTime(start);
    // 62s remaining at mount; a 1-minute (60s) milestone crosses ~2s in — well
    // before the 5s resync poll, so the resync's own refetch never interferes.
    const endsAt = new Date(start.getTime() + 62_000).toISOString();
    (timersApi.get as Mock).mockResolvedValue({
      data: makeState({
        status: "RUNNING", durationSeconds: 120, startedAt: start.toISOString(),
        endsAt, serverNow: start.toISOString(), milestoneMinutes: [1], notifyAtHalf: false,
      }),
    });

    renderHook(() => useRoundTimer(1, 5, "CONTEST", { fireBanners: true }));

    await act(async () => { await vi.advanceTimersByTimeAsync(0); });
    expect(addToast).not.toHaveBeenCalled();

    await act(async () => { await vi.advanceTimersByTimeAsync(3_000); });
    expect(addToast).toHaveBeenCalledTimes(1);
    expect(addToast).toHaveBeenCalledWith(expect.objectContaining({ title: "1 min left", type: "warning" }));

    // Crossing the same threshold again on later ticks must not refire it.
    await act(async () => { await vi.advanceTimersByTimeAsync(2_000); });
    expect(addToast).toHaveBeenCalledTimes(1);
  });

  it("fires an EXPIRED banner once the countdown reaches zero", async () => {
    vi.useFakeTimers();
    const start = new Date("2026-01-01T00:00:00.000Z");
    vi.setSystemTime(start);
    const endsAt = new Date(start.getTime() + 3_000).toISOString();
    (timersApi.get as Mock).mockResolvedValue({
      data: makeState({
        status: "RUNNING", durationSeconds: 10, startedAt: start.toISOString(),
        endsAt, serverNow: start.toISOString(), milestoneMinutes: [], notifyAtHalf: false,
      }),
    });

    renderHook(() => useRoundTimer(1, 5, "CONTEST", { fireBanners: true }));
    await act(async () => { await vi.advanceTimersByTimeAsync(0); });

    await act(async () => { await vi.advanceTimersByTimeAsync(4_000); });
    expect(addToast).toHaveBeenCalledWith(expect.objectContaining({ title: "Time's up" }));
  });

  it("never calls addToast when fireBanners is not set (default)", async () => {
    vi.useFakeTimers();
    const start = new Date("2026-01-01T00:00:00.000Z");
    vi.setSystemTime(start);
    const endsAt = new Date(start.getTime() + 3_000).toISOString();
    (timersApi.get as Mock).mockResolvedValue({
      data: makeState({ status: "RUNNING", durationSeconds: 10, startedAt: start.toISOString(), endsAt, serverNow: start.toISOString() }),
    });

    renderHook(() => useRoundTimer(1, 5, "CONTEST"));
    await act(async () => { await vi.advanceTimersByTimeAsync(0); });
    await act(async () => { await vi.advanceTimersByTimeAsync(4_000); });

    expect(addToast).not.toHaveBeenCalled();
  });
});
