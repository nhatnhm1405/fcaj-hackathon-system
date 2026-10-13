import { describe, it, expect, vi, beforeEach, type Mock } from "vitest";
import { render, screen, waitFor, act } from "@testing-library/react";
import {
  NotificationProvider, useNotifications,
  toKind, mapNotification, summariseSenders, announceSplashFor,
  type UINotification,
} from "@/app/providers/NotificationProvider";
import { notificationsApi, type Notification as ApiNotification } from "@/shared/apiClient";

vi.mock("@/shared/apiClient", () => ({
  notificationsApi: {
    getAll: vi.fn(),
    getUnreadCount: vi.fn(),
    markAsRead: vi.fn(),
    markAllAsRead: vi.fn(),
  },
}));

const authMock = vi.hoisted(() => ({
  currentUser: { user_id: 1, full_name: "Alice", email: "a@b.com" } as { user_id: number } | null,
  isLoading: false,
  patchCurrentUser: vi.fn(),
  refreshTeamContext: vi.fn(),
}));
vi.mock("@/app/providers/AuthProvider", () => ({
  useAuth: () => authMock,
}));

describe("toKind", () => {
  it("maps success-ish backend types", () => {
    for (const t of ["TEAM_APPROVED", "JOIN_REQUEST_ACCEPTED", "RESULT_PUBLISHED", "ROUND_WIN", "TEAM_ADVANCE"]) {
      expect(toKind(t)).toBe("success");
    }
  });

  it("maps warning-ish backend types", () => {
    for (const t of ["ACCOUNT_REJECTED", "TEAM_DISQUALIFIED", "UPLOAD_FAIL", "MEMBER_REMOVED", "INVITE_DECLINE"]) {
      expect(toKind(t)).toBe("warning");
    }
  });

  it("is case-insensitive", () => {
    expect(toKind("team_approved")).toBe("success");
  });

  it("defaults to info for anything else, including undefined", () => {
    expect(toKind("TIMER")).toBe("info");
    expect(toKind(undefined)).toBe("info");
    expect(toKind("")).toBe("info");
  });
});

describe("mapNotification", () => {
  it("maps every field, coercing isRead to a boolean", () => {
    const ui = mapNotification({
      notificationId: 5, title: "Hi", content: "Body", type: "TEAM_APPROVED",
      isRead: true, createdAt: "2026-01-01T00:00:00",
      senderName: "Bob", senderRole: "MENTOR", scopeLabel: "AI Track", linkUrl: "https://x",
    } as ApiNotification);
    expect(ui).toEqual({
      notification_id: 5, title: "Hi", message: "Body", is_read: true,
      type: "success", rawType: "TEAM_APPROVED", created_at: "2026-01-01T00:00:00",
      from: "Bob", sender_role: "MENTOR", scope_label: "AI Track", link_url: "https://x",
    });
  });

  it("falls back to null for optional announcement fields", () => {
    const ui = mapNotification({
      notificationId: 1, title: "Hi", content: "Body", type: "INFO",
      isRead: false, createdAt: "2026-01-01T00:00:00",
    } as ApiNotification);
    expect(ui.from).toBeNull();
    expect(ui.sender_role).toBeNull();
    expect(ui.scope_label).toBeNull();
    expect(ui.link_url).toBeNull();
  });
});

describe("summariseSenders", () => {
  it("falls back to 'a coordinator' when there are no senders", () => {
    expect(summariseSenders([])).toBe("a coordinator");
  });

  it("returns the single sender's name", () => {
    expect(summariseSenders(["Alice"])).toBe("Alice");
  });

  it("joins exactly two senders with 'and'", () => {
    expect(summariseSenders(["Alice", "Bob"])).toBe("Alice and Bob");
  });

  it("summarises 3+ senders as 'first and N others'", () => {
    expect(summariseSenders(["Alice", "Bob", "Carol"])).toBe("Alice and 2 others");
  });
});

describe("announceSplashFor", () => {
  it("returns null for an empty list", () => {
    expect(announceSplashFor([])).toBeNull();
  });

  it("dedupes senders across items", () => {
    const items = [
      { from: "Alice" } as UINotification,
      { from: "Alice" } as UINotification,
      { from: "Bob" } as UINotification,
    ];
    const splash = announceSplashFor(items);
    expect(splash?.items).toHaveLength(3);
    expect(splash?.from).toBe("Alice and Bob");
  });
});

// ── Provider integration ──────────────────────────────────────────────

function apiNotif(overrides: Partial<ApiNotification> = {}): ApiNotification {
  return {
    notificationId: 1,
    title: "Title",
    content: "Message",
    type: "INFO",
    isRead: false,
    createdAt: new Date().toISOString(),
    ...overrides,
  } as ApiNotification;
}

let ctx!: ReturnType<typeof useNotifications>;
function Harness() {
  ctx = useNotifications();
  return (
    <div>
      <div data-testid="unread">{ctx.unreadCount}</div>
      <div data-testid="count">{ctx.userNotifications.length}</div>
    </div>
  );
}

describe("NotificationProvider integration", () => {
  beforeEach(() => {
    (notificationsApi.getAll as Mock).mockReset();
    (notificationsApi.markAsRead as Mock).mockReset().mockResolvedValue({});
    (notificationsApi.markAllAsRead as Mock).mockReset().mockResolvedValue({});
    authMock.currentUser = { user_id: 1, full_name: "Alice", email: "a@b.com" };
    authMock.isLoading = false;
    authMock.patchCurrentUser.mockReset();
    authMock.refreshTeamContext.mockReset();
    sessionStorage.clear();
  });

  it("loads the baseline list without bannering pre-existing (non-announcement) notifications", async () => {
    (notificationsApi.getAll as Mock).mockResolvedValue({
      data: [apiNotif({ notificationId: 1, title: "Old news", isRead: false })],
    });

    render(<NotificationProvider><Harness /></NotificationProvider>);

    await waitFor(() => expect(screen.getByTestId("count").textContent).toBe("1"));
    expect(screen.getByTestId("unread").textContent).toBe("1");
    expect(screen.queryByText("Old news")).toBeNull();
  });

  it("banners a genuinely new, non-announcement, non-timer notification on the next refresh", async () => {
    (notificationsApi.getAll as Mock).mockResolvedValueOnce({ data: [] });
    render(<NotificationProvider><Harness /></NotificationProvider>);
    await waitFor(() => expect(screen.getByTestId("count").textContent).toBe("0"));

    (notificationsApi.getAll as Mock).mockResolvedValueOnce({
      data: [apiNotif({ notificationId: 2, title: "Team approved", type: "TEAM_APPROVED", isRead: false })],
    });
    await act(async () => { ctx.refresh(); await Promise.resolve(); });

    await waitFor(() => expect(screen.getByText("Team approved")).toBeInTheDocument());
  });

  it("does not banner a fresh TIMER notification (useRoundTimer already banners it client-side)", async () => {
    (notificationsApi.getAll as Mock).mockResolvedValueOnce({ data: [] });
    render(<NotificationProvider><Harness /></NotificationProvider>);
    await waitFor(() => expect(screen.getByTestId("count").textContent).toBe("0"));

    (notificationsApi.getAll as Mock).mockResolvedValueOnce({
      data: [apiNotif({ notificationId: 3, title: "5 min left", type: "TIMER", isRead: false })],
    });
    await act(async () => { ctx.refresh(); await Promise.resolve(); });

    await waitFor(() => expect(screen.getByTestId("count").textContent).toBe("1"));
    expect(screen.queryByText("5 min left")).toBeNull();
  });

  it("aggregates fresh announcement notifications into one splash instead of individual banners", async () => {
    (notificationsApi.getAll as Mock).mockResolvedValueOnce({ data: [] });
    render(<NotificationProvider><Harness /></NotificationProvider>);
    await waitFor(() => expect(screen.getByTestId("count").textContent).toBe("0"));

    (notificationsApi.getAll as Mock).mockResolvedValueOnce({
      data: [apiNotif({ notificationId: 4, title: "Heads up", type: "INFO", isRead: false, senderName: "Coach" })],
    });
    await act(async () => { ctx.refresh(); await Promise.resolve(); });

    await waitFor(() => expect(screen.getByText("VIEW MESSAGES")).toBeInTheDocument());
    expect(screen.getByText(/from/)).toHaveTextContent("Coach");
    expect(screen.queryByText("Heads up")).toBeNull(); // no separate banner for it
  });

  it("shows a one-time splash for pre-existing unread announcements on the very first load", async () => {
    (notificationsApi.getAll as Mock).mockResolvedValue({
      data: [apiNotif({ notificationId: 5, title: "Welcome", isRead: false, senderName: "Coach" })],
    });

    render(<NotificationProvider><Harness /></NotificationProvider>);

    await waitFor(() => expect(screen.getByText("VIEW MESSAGES")).toBeInTheDocument());
  });

  it("reacts to PARTICIPATION_ACCESS_APPROVED by reactivating the user and refreshing team context", async () => {
    (notificationsApi.getAll as Mock).mockResolvedValueOnce({ data: [] });
    render(<NotificationProvider><Harness /></NotificationProvider>);
    await waitFor(() => expect(screen.getByTestId("count").textContent).toBe("0"));

    (notificationsApi.getAll as Mock).mockResolvedValueOnce({
      data: [apiNotif({ notificationId: 6, title: "Reactivated", type: "PARTICIPATION_ACCESS_APPROVED", isRead: false })],
    });
    await act(async () => { ctx.refresh(); await Promise.resolve(); });

    await waitFor(() => expect(authMock.patchCurrentUser).toHaveBeenCalledWith({ is_active: true }));
    expect(authMock.refreshTeamContext).toHaveBeenCalled();
  });

  it("markRead marks locally and calls the API only when the item wasn't already read", async () => {
    (notificationsApi.getAll as Mock).mockResolvedValue({
      data: [apiNotif({ notificationId: 7, isRead: false })],
    });
    render(<NotificationProvider><Harness /></NotificationProvider>);
    await waitFor(() => expect(screen.getByTestId("unread").textContent).toBe("1"));

    act(() => { ctx.markRead(7); });
    expect(screen.getByTestId("unread").textContent).toBe("0");
    expect(notificationsApi.markAsRead).toHaveBeenCalledWith(7);

    (notificationsApi.markAsRead as Mock).mockClear();
    act(() => { ctx.markRead(7); }); // already read — no extra API call
    expect(notificationsApi.markAsRead).not.toHaveBeenCalled();
  });

  it("markAllRead marks every notification read and calls the API", async () => {
    (notificationsApi.getAll as Mock).mockResolvedValue({
      data: [apiNotif({ notificationId: 8, isRead: false }), apiNotif({ notificationId: 9, isRead: false })],
    });
    render(<NotificationProvider><Harness /></NotificationProvider>);
    await waitFor(() => expect(screen.getByTestId("unread").textContent).toBe("2"));

    act(() => { ctx.markAllRead(); });
    expect(screen.getByTestId("unread").textContent).toBe("0");
    expect(notificationsApi.markAllAsRead).toHaveBeenCalledTimes(1);
  });

  it("resets to an empty list when there is no signed-in user", async () => {
    authMock.currentUser = null;
    render(<NotificationProvider><Harness /></NotificationProvider>);
    expect(screen.getByTestId("count").textContent).toBe("0");
    expect(notificationsApi.getAll).not.toHaveBeenCalled();
  });
});
