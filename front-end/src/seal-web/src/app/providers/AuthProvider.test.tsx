import { describe, it, expect, vi, beforeEach, type Mock } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import {
  AuthProvider, useAuth, resolveRole, resolveAllRoles, mapBackendRole, mapApiUser,
  ACTIVE_ROLE_KEY, type ApiUserProfile,
} from "@/app/providers/AuthProvider";
import { apiFetch, ApiError, teamsApi } from "@/shared/apiClient";

vi.mock("@/shared/apiClient", () => {
  class MockApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  }
  return {
    apiFetch: vi.fn(),
    ApiError: MockApiError,
    teamsApi: { getMy: vi.fn() },
  };
});

describe("resolveRole (priority: ADMIN > COORDINATOR > JUDGE > MENTOR > PARTICIPANT)", () => {
  it("matches a plain string role by substring, case-insensitively", () => {
    expect(resolveRole({ role: "event_coordinator" } as ApiUserProfile)).toBe("COORDINATOR");
    expect(resolveRole({ role: "SYSTEM_ADMIN" } as ApiUserProfile)).toBe("ADMIN");
  });

  it("picks the highest-priority role when several are present", () => {
    expect(resolveRole({ roles: ["JUDGE", "MENTOR"] } as ApiUserProfile)).toBe("JUDGE");
    expect(resolveRole({ roles: ["MENTOR", "SYSTEM_ADMIN"] } as ApiUserProfile)).toBe("ADMIN");
  });

  it("reads roleName/role_name off nested role objects", () => {
    expect(resolveRole({ roles: [{ roleName: "EVENT_COORDINATOR" }] } as unknown as ApiUserProfile)).toBe("COORDINATOR");
    expect(resolveRole({ roles: [{ role_name: "JUDGE" }] } as unknown as ApiUserProfile)).toBe("JUDGE");
  });

  it("requires an exact match for MENTOR (not a substring)", () => {
    expect(resolveRole({ role: "MENTOR" } as ApiUserProfile)).toBe("MENTOR");
    expect(resolveRole({ role: "MENTOR_LEAD" } as ApiUserProfile)).toBe("PARTICIPANT");
  });

  it("defaults to PARTICIPANT when no staff role is present", () => {
    expect(resolveRole({} as ApiUserProfile)).toBe("PARTICIPANT");
    expect(resolveRole({ role: "PARTICIPANT" } as ApiUserProfile)).toBe("PARTICIPANT");
  });
});

describe("resolveAllRoles", () => {
  it("collects every staff role across roles/role/roleName/role_name, deduped", () => {
    const roles = resolveAllRoles({
      roles: ["JUDGE", "judge"],
      role: "MENTOR",
    } as ApiUserProfile);
    expect(roles.sort()).toEqual(["JUDGE", "MENTOR"]);
  });

  it("excludes non-staff roles like PARTICIPANT", () => {
    expect(resolveAllRoles({ roles: ["PARTICIPANT"] } as ApiUserProfile)).toEqual([]);
  });

  it("returns an empty list when no roles are present", () => {
    expect(resolveAllRoles({} as ApiUserProfile)).toEqual([]);
  });
});

describe("mapBackendRole", () => {
  it("maps by substring for ADMIN/COORDINATOR/JUDGE, exact match for MENTOR", () => {
    expect(mapBackendRole("SYSTEM_ADMIN")).toBe("ADMIN");
    expect(mapBackendRole("EVENT_COORDINATOR")).toBe("COORDINATOR");
    expect(mapBackendRole("JUDGE")).toBe("JUDGE");
    expect(mapBackendRole("MENTOR")).toBe("MENTOR");
  });

  it("falls back to PARTICIPANT for anything else", () => {
    expect(mapBackendRole("PARTICIPANT")).toBe("PARTICIPANT");
    expect(mapBackendRole("MENTOR_LEAD")).toBe("PARTICIPANT");
  });
});

describe("mapApiUser", () => {
  it("prefers camelCase fields, falling back to snake_case", () => {
    const user = mapApiUser({
      email: "a@b.com",
      userId: 1, user_id: 2,
      fullName: "Alice", full_name: "Alicia",
      studentId: "SE100", student_id: "SE200",
      avatarUrl: "a.png", avatar_url: "b.png",
      isLeader: true, is_leader: false,
      teamId: 10, team_id: 20,
    } as ApiUserProfile);
    expect(user.user_id).toBe(1);
    expect(user.full_name).toBe("Alice");
    expect(user.student_id).toBe("SE100");
    expect(user.avatar_url).toBe("a.png");
    expect(user.is_leader).toBe(true);
    expect(user.team_id).toBe(10);
  });

  it("derives student_type from userType, and null for staff", () => {
    expect(mapApiUser({ email: "a@b.com", userType: "FPT_STUDENT" } as ApiUserProfile).student_type).toBe("FPT");
    expect(mapApiUser({ email: "a@b.com", userType: "EXTERNAL_STUDENT" } as ApiUserProfile).student_type).toBe("EXTERNAL");
    expect(mapApiUser({ email: "a@b.com", userType: "STAFF" } as ApiUserProfile).student_type).toBeNull();
  });

  it("flags profile_incomplete only for a PENDING_PROFILE userType", () => {
    expect(mapApiUser({ email: "a@b.com", userType: "PENDING_PROFILE" } as ApiUserProfile).profile_incomplete).toBe(true);
    expect(mapApiUser({ email: "a@b.com", userType: "FPT_STUDENT" } as ApiUserProfile).profile_incomplete).toBe(false);
  });

  it("defaults approved/is_active to true when absent, team_dormant always false initially", () => {
    const user = mapApiUser({ email: "a@b.com" } as ApiUserProfile);
    expect(user.approved).toBe(true);
    expect(user.is_active).toBe(true);
    expect(user.team_dormant).toBe(false);
  });

  it("honors an explicit false for approved/is_active", () => {
    const user = mapApiUser({ email: "a@b.com", isApproved: false, isActive: false } as ApiUserProfile);
    expect(user.approved).toBe(false);
    expect(user.is_active).toBe(false);
  });
});

describe("AuthProvider / useAuth — login()", () => {
  beforeEach(() => {
    localStorage.clear();
    (apiFetch as Mock).mockReset();
    (teamsApi.getMy as Mock).mockReset();
  });

  it("resolves 'ok' and persists the active role for a single-role staff account", async () => {
    (apiFetch as Mock)
      .mockImplementationOnce(() => Promise.reject(new ApiError(401, "no session"))) // mount restore
      .mockImplementationOnce(() => Promise.resolve({})) // /api/auth/login
      .mockImplementationOnce(() => Promise.resolve({ // /api/auth/me
        data: { email: "coord@fpt.edu.vn", fullName: "Coord", userType: "STAFF", roles: ["EVENT_COORDINATOR"] },
      }));

    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    let outcome: string = "";
    await act(async () => { outcome = await result.current.login("coord@fpt.edu.vn", "pw"); });

    expect(outcome).toBe("ok");
    expect(result.current.currentUser?.role).toBe("COORDINATOR");
    expect(localStorage.getItem(ACTIVE_ROLE_KEY)).toBe("EVENT_COORDINATOR");
  });

  it("resolves 'ok:select-role' when multiple roles exist with no saved preference", async () => {
    (apiFetch as Mock)
      .mockImplementationOnce(() => Promise.reject(new ApiError(401, "no session")))
      .mockImplementationOnce(() => Promise.resolve({}))
      .mockImplementationOnce(() => Promise.resolve({
        data: { email: "j@fpt.edu.vn", fullName: "Judge Mentor", userType: "STAFF", roles: ["JUDGE", "MENTOR"] },
      }));

    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    let outcome: string = "";
    await act(async () => { outcome = await result.current.login("j@fpt.edu.vn", "pw"); });

    expect(outcome).toBe("ok:select-role");
    expect(result.current.availableRoles.sort()).toEqual(["JUDGE", "MENTOR"]);
    expect(result.current.activeRole).toBeNull();
  });

  it("resolves 'pending_approval' only when a 403 carries the backend approval message", async () => {
    (apiFetch as Mock)
      .mockImplementationOnce(() => Promise.reject(new ApiError(401, "no session")))
      .mockImplementationOnce(() => Promise.reject(new ApiError(
        403,
        "Your account is pending approval. Please wait for an Event Coordinator to review your registration.",
      )));

    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    let outcome: string = "";
    await act(async () => { outcome = await result.current.login("x@fpt.edu.vn", "pw"); });

    expect(outcome).toBe("pending_approval");
    expect(result.current.isAuthenticated).toBe(false);
  });

  it("does not misreport an unrelated 403 as pending approval", async () => {
    (apiFetch as Mock)
      .mockImplementationOnce(() => Promise.reject(new ApiError(401, "no session")))
      .mockImplementationOnce(() => Promise.reject(new ApiError(403, "Forbidden")));

    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    let outcome: string = "";
    await act(async () => { outcome = await result.current.login("coord@fpt.edu.vn", "pw"); });

    expect(outcome).toBe("access_denied");
    expect(result.current.isAuthenticated).toBe(false);
  });

  it("resolves 'invalid_credentials' on a 401 from the login call", async () => {
    (apiFetch as Mock)
      .mockImplementationOnce(() => Promise.reject(new ApiError(401, "no session")))
      .mockImplementationOnce(() => Promise.reject(new ApiError(401, "bad password")));

    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    let outcome: string = "";
    await act(async () => { outcome = await result.current.login("x@fpt.edu.vn", "wrong"); });

    expect(outcome).toBe("invalid_credentials");
  });

  it("resolves the participant's team context (team_id, is_leader) on login", async () => {
    (apiFetch as Mock)
      .mockImplementationOnce(() => Promise.reject(new ApiError(401, "no session")))
      .mockImplementationOnce(() => Promise.resolve({}))
      .mockImplementationOnce(() => Promise.resolve({
        data: { userId: 7, email: "p@fpt.edu.vn", fullName: "Leader", userType: "FPT_STUDENT" },
      }));
    (teamsApi.getMy as Mock).mockResolvedValue({
      data: { teamId: 42, status: "APPROVED", eventStatus: "IN_PROGRESS", members: [{ userId: 7, role: "LEADER" }] },
    });

    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => { await result.current.login("p@fpt.edu.vn", "pw"); });

    expect(result.current.currentUser?.team_id).toBe(42);
    expect(result.current.currentUser?.is_leader).toBe(true);
    expect(result.current.currentUser?.team_dormant).toBe(false);
  });

  it("logout() clears the session and the stored active role", async () => {
    (apiFetch as Mock)
      .mockImplementationOnce(() => Promise.reject(new ApiError(401, "no session")))
      .mockImplementationOnce(() => Promise.resolve({}))
      .mockImplementationOnce(() => Promise.resolve({
        data: { email: "coord@fpt.edu.vn", fullName: "Coord", userType: "STAFF", roles: ["EVENT_COORDINATOR"] },
      }))
      .mockImplementationOnce(() => Promise.resolve({})); // /api/auth/logout (fire-and-forget)

    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    await act(async () => { await result.current.login("coord@fpt.edu.vn", "pw"); });
    expect(result.current.isAuthenticated).toBe(true);

    act(() => { result.current.logout(); });

    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.currentUser).toBeNull();
    expect(localStorage.getItem(ACTIVE_ROLE_KEY)).toBeNull();
  });
});
