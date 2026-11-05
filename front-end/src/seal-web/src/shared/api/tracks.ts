import { apiFetch, ApiError, API_BASE_URL, type ApiResponse } from './core';

// ── Tracks ────────────────────────────────────────────────────────

export interface Track {
  trackId: number;
  eventId: number;
  name: string;
  description?: string;
  capacity?: number | null;   // total slots (max teams)
  teamCount?: number | null;  // approved teams already in this track (slots used)
}

export interface CreateTrackPayload {
  name: string;
  description?: string;
}

export const tracksApi = {
  getAll: (eventId: number) =>
    apiFetch<ApiResponse<Track[]>>(`/api/events/${eventId}/tracks`),

  getById: (eventId: number, trackId: number) =>
    apiFetch<ApiResponse<Track>>(`/api/events/${eventId}/tracks/${trackId}`),

  create: (eventId: number, payload: CreateTrackPayload) =>
    apiFetch<ApiResponse<Track>>(`/api/events/${eventId}/tracks`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  update: (eventId: number, trackId: number, payload: Partial<CreateTrackPayload>) =>
    apiFetch<ApiResponse<Track>>(`/api/events/${eventId}/tracks/${trackId}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  delete: (eventId: number, trackId: number) =>
    apiFetch<ApiResponse<void>>(`/api/events/${eventId}/tracks/${trackId}`, { method: 'DELETE' }),
};

// ── Track "đề thi" (problem statement) ─────────────────────────────
// One file per track. Coordinator uploads (event SETUP/IN_PROGRESS) then releases
// it; members of an approved team in the track download a released problem. The
// file is access-controlled — never served from the public /uploads path.

export interface TrackProblem {
  trackId: number;
  trackName: string;
  hasProblem: boolean;
  fileName?: string | null;
  fileSize?: number | null;
  contentType?: string | null;
  released: boolean;
  uploadedAt?: string | null;
  releasedAt?: string | null;
}

export const problemsApi = {
  // Coordinator: status of every track's problem in the event.
  listForEvent: (eventId: number) =>
    apiFetch<ApiResponse<TrackProblem[]>>(`/api/events/${eventId}/problems`),

  // Coordinator or a participant in the track: problem metadata (participants only
  // see it once released).
  get: (eventId: number, trackId: number) =>
    apiFetch<ApiResponse<TrackProblem>>(`/api/events/${eventId}/tracks/${trackId}/problem`),

  // Coordinator: upload / replace the problem file (multipart).
  upload: (eventId: number, trackId: number, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return apiFetch<ApiResponse<TrackProblem>>(
      `/api/events/${eventId}/tracks/${trackId}/problem`,
      { method: 'POST', body: form },
    );
  },

  // Coordinator: publish / hide the problem.
  release: (eventId: number, trackId: number) =>
    apiFetch<ApiResponse<TrackProblem>>(
      `/api/events/${eventId}/tracks/${trackId}/problem/release`, { method: 'PUT' }),
  retract: (eventId: number, trackId: number) =>
    apiFetch<ApiResponse<TrackProblem>>(
      `/api/events/${eventId}/tracks/${trackId}/problem/retract`, { method: 'PUT' }),

  // Coordinator: remove the problem entirely.
  remove: (eventId: number, trackId: number) =>
    apiFetch<ApiResponse<void>>(
      `/api/events/${eventId}/tracks/${trackId}/problem`, { method: 'DELETE' }),

  // Open the file (with auth) in a new tab to VIEW it. PDFs/images preview inline;
  // other types (docx/zip) the browser can't preview, so they download instead.
  // Opens the tab synchronously first so popup blockers don't kill it after await.
  view: async (eventId: number, trackId: number) => {
    const win = window.open('', '_blank');
    try {
      const res = await fetch(
        `${API_BASE_URL}/api/events/${eventId}/tracks/${trackId}/problem/download`,
        { credentials: 'include' },
      );
      if (!res.ok) {
        win?.close();
        const body = await res.json().catch(() => ({}));
        throw new ApiError(res.status, body?.message ?? `HTTP ${res.status}`);
      }
      const url = URL.createObjectURL(await res.blob());
      if (win) win.location.href = url;
      else window.open(url, '_blank', 'noopener'); // popup blocked — best-effort retry
      setTimeout(() => URL.revokeObjectURL(url), 60_000); // give the tab time to load
    } catch (err) {
      win?.close();
      throw err;
    }
  },

  // Download the file (with auth) and trigger a browser "Save as". Returns nothing;
  // throws ApiError on failure so callers can surface a message.
  download: async (eventId: number, trackId: number, fallbackName = 'problem') => {
    const res = await fetch(
      `${API_BASE_URL}/api/events/${eventId}/tracks/${trackId}/problem/download`,
      { credentials: 'include' },
    );
    if (!res.ok) {
      const body = await res.json().catch(() => ({}));
      throw new ApiError(res.status, body?.message ?? `HTTP ${res.status}`);
    }
    const blob = await res.blob();
    const name = filenameFromContentDisposition(res.headers.get('Content-Disposition')) ?? fallbackName;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  },
};

// Pull the original file name out of a Content-Disposition header
// (prefers RFC 5987 `filename*=UTF-8''…`, falls back to `filename="…"`).
function filenameFromContentDisposition(header: string | null): string | null {
  if (!header) return null;
  const star = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (star?.[1]) {
    try { return decodeURIComponent(star[1]); } catch { /* fall through */ }
  }
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain?.[1] ?? null;
}
