import { apiFetch, type ApiResponse } from './core';

// ── Notifications ─────────────────────────────────────────────────

export interface Notification {
  notificationId: number;
  title: string;
  content: string;
  type?: string;
  isRead: boolean;
  createdAt: string;
  // Set only for ANNOUNCEMENT notifications (resolved from the source Announcement).
  senderName?: string | null;
  senderRole?: string | null;   // MENTOR | COORDINATOR
  scopeLabel?: string | null;   // track name (mentor) or event name (coordinator)
  linkUrl?: string | null;      // optional attachment link
}

export interface AnnouncementItem {
  announcementId: number;
  title: string;
  content: string;
  senderName: string;
  senderRole: string;   // MENTOR | COORDINATOR
  scope: string;        // TRACK | EVENT
  audience?: string | null;   // PARTICIPANT | JUDGE | MENTOR | ALL
  eventId?: number | null;    // event this announcement belongs to
  scopeLabel: string;
  linkUrl?: string | null;
  recipientCount: number;
  createdAt: string;
}

export const notificationsApi = {
  getAll: () =>
    apiFetch<ApiResponse<Notification[]>>('/api/notifications'),

  getUnreadCount: () =>
    apiFetch<ApiResponse<{ count: number }>>('/api/notifications/unread-count'),

  markAsRead: (notificationId: number) =>
    apiFetch<ApiResponse<void>>(`/api/notifications/${notificationId}/read`, { method: 'PUT' }),

  markAllAsRead: () =>
    apiFetch<ApiResponse<void>>('/api/notifications/read-all', { method: 'PUT' }),
};

// ── Announcements (Mentor: track-scoped · Coordinator: event-scoped) ──
export const announcementsApi = {
  // Mentor → all participants of one of their tracks.
  createMentor: (payload: { trackId: number; title: string; content: string; linkUrl?: string }) =>
    apiFetch<ApiResponse<AnnouncementItem>>('/api/mentor/announcements', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  listMentor: () =>
    apiFetch<ApiResponse<AnnouncementItem[]>>('/api/mentor/announcements'),

  // Coordinator → an audience (PARTICIPANT | JUDGE | MENTOR | ALL) across an event.
  createCoordinator: (payload: { eventId: number; audience: string; title: string; content: string; linkUrl?: string }) =>
    apiFetch<ApiResponse<AnnouncementItem>>('/api/coordinator/announcements', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  listCoordinator: () =>
    apiFetch<ApiResponse<AnnouncementItem[]>>('/api/coordinator/announcements'),
};
