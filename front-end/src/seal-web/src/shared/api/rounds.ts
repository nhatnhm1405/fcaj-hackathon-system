import { apiFetch, type ApiResponse } from './core';

// ── Rounds ────────────────────────────────────────────────────────

export interface Round {
  roundId: number;
  eventId: number;
  eventName?: string;
  name: string;
  orderNumber: number;
  startTime: string;
  endTime: string;
  submissionDeadline: string;
  topNAdvance?: number;
  isFinal: boolean;
  status?: string;
}

export interface CreateRoundPayload {
  name: string;
  orderNumber: number;
  startTime: string;
  endTime: string;
  submissionDeadline: string;
  topNAdvance?: number;
  isFinal?: boolean;
}

export interface UpdateRoundPayload {
  name?: string;
  topNAdvance?: number;
  clearTopNAdvance?: boolean; // send true to REMOVE the cut-off (topNAdvance can't be unset via null)
  status?: string;
  startTime?: string;
  endTime?: string;
  submissionDeadline?: string;
}

export const roundsApi = {
  getAll: (eventId: number) =>
    apiFetch<ApiResponse<Round[]>>(`/api/events/${eventId}/rounds`),

  getById: (eventId: number, roundId: number) =>
    apiFetch<ApiResponse<Round>>(`/api/events/${eventId}/rounds/${roundId}`),

  create: (eventId: number, payload: CreateRoundPayload) =>
    apiFetch<ApiResponse<Round>>(`/api/events/${eventId}/rounds`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  update: (eventId: number, roundId: number, payload: UpdateRoundPayload) =>
    apiFetch<ApiResponse<Round>>(`/api/events/${eventId}/rounds/${roundId}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
};

// ── Round Timers (live countdown per round phase) ───────────────────
// CONTEST gates team submission; JUDGING gates judge scoring. Time is
// server-authoritative: compute remaining from endsAt vs serverNow (correct for
// client clock skew) — never trust the local clock. See useRoundTimer.

export type TimerPhase = 'CONTEST' | 'JUDGING';
export type TimerStatus = 'IDLE' | 'RUNNING' | 'PAUSED' | 'STOPPED' | 'EXPIRED';

export interface RoundTimerState {
  roundId: number;
  phase: TimerPhase;
  status: TimerStatus;
  durationSeconds?: number | null;
  startedAt?: string | null;
  endsAt?: string | null;
  remainingSeconds: number;
  serverNow: string;
  milestoneMinutes?: number[] | null;
  notifyAtHalf?: boolean | null;
}

export interface StartTimerPayload {
  durationSeconds: number;
  milestoneMinutes?: number[];
  notifyAtHalf?: boolean;
}

export const timersApi = {
  get: (eventId: number, roundId: number, phase: TimerPhase) =>
    apiFetch<ApiResponse<RoundTimerState>>(`/api/events/${eventId}/rounds/${roundId}/timer/${phase}`),

  start: (eventId: number, roundId: number, phase: TimerPhase, payload: StartTimerPayload) =>
    apiFetch<ApiResponse<RoundTimerState>>(`/api/events/${eventId}/rounds/${roundId}/timer/${phase}/start`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  pause: (eventId: number, roundId: number, phase: TimerPhase) =>
    apiFetch<ApiResponse<RoundTimerState>>(`/api/events/${eventId}/rounds/${roundId}/timer/${phase}/pause`, { method: 'POST' }),

  resume: (eventId: number, roundId: number, phase: TimerPhase) =>
    apiFetch<ApiResponse<RoundTimerState>>(`/api/events/${eventId}/rounds/${roundId}/timer/${phase}/resume`, { method: 'POST' }),

  stop: (eventId: number, roundId: number, phase: TimerPhase) =>
    apiFetch<ApiResponse<RoundTimerState>>(`/api/events/${eventId}/rounds/${roundId}/timer/${phase}/stop`, { method: 'POST' }),

  extend: (eventId: number, roundId: number, phase: TimerPhase, seconds: number) =>
    apiFetch<ApiResponse<RoundTimerState>>(`/api/events/${eventId}/rounds/${roundId}/timer/${phase}/extend`, {
      method: 'POST',
      body: JSON.stringify({ seconds }),
    }),
};
