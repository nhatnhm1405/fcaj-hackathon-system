// Event lifecycle authorization — single source of truth for the FE.
//
// These are PURE functions of the (frontend) role string so they are trivial to
// unit-test and to reuse across pages. They mirror the backend rules, which are
// the REAL gate (see HackathonEventController / ReopenRequestController):
//   - Only SYSTEM_ADMIN may create or reopen an event.
//   - Coordinators run an event's forward lifecycle (OPEN→SETUP→IN_PROGRESS→
//     COMPLETED) but cannot create it, and cannot reopen a COMPLETED one —
//     they file a reopen request for the admin to approve.
//
// The FE role is the normalized value from AuthProvider
// ('ADMIN' | 'COORDINATOR' | 'JUDGE' | 'MENTOR' | 'PARTICIPANT'), NOT the raw
// backend role name (SYSTEM_ADMIN / EVENT_COORDINATOR).

import { useAuth } from "@/app/providers/AuthProvider";

export type AppRole = 'PARTICIPANT' | 'MENTOR' | 'JUDGE' | 'COORDINATOR' | 'ADMIN';

/** Creating a new event is a platform action — Admin only. */
export function canCreateEvent(role: AppRole | null | undefined): boolean {
  return role === 'ADMIN';
}

/** Directly reopening a COMPLETED event (COMPLETED → IN_PROGRESS) — Admin only. */
export function canReopenEvent(role: AppRole | null | undefined): boolean {
  return role === 'ADMIN';
}

/** Filing a "please reopen" request — Coordinators only (Admins just reopen). */
export function canRequestReopen(role: AppRole | null | undefined): boolean {
  return role === 'COORDINATOR';
}

/**
 * Completing a running event (IN_PROGRESS → COMPLETED) — Admin only. Enforced
 * on the backend via the dedicated POST /api/events/{id}/complete endpoint
 * (the generic PUT no longer allows IN_PROGRESS → COMPLETED).
 */
export function canCompleteEvent(role: AppRole | null | undefined): boolean {
  return role === 'ADMIN';
}

/**
 * Driving the Coordinator-run lifecycle transitions (OPEN, SETUP, START,
 * CANCEL...). Completing is excluded — see canCompleteEvent.
 */
export function canChangeEventStatus(role: AppRole | null | undefined): boolean {
  return role === 'ADMIN' || role === 'COORDINATOR';
}

/** Reviewing (approve/reject) Coordinator reopen requests — Admin only. */
export function canManageReopenRequests(role: AppRole | null | undefined): boolean {
  return role === 'ADMIN';
}

/**
 * Hook wrapper reading the current user's role from AuthProvider. Pages can call
 * `const perms = usePermissions()` and read `perms.canCreateEvent`, etc.
 */
export function usePermissions() {
  const { currentUser } = useAuth();
  const role = (currentUser?.role ?? null) as AppRole | null;
  return {
    role,
    canCreateEvent: canCreateEvent(role),
    canReopenEvent: canReopenEvent(role),
    canRequestReopen: canRequestReopen(role),
    canCompleteEvent: canCompleteEvent(role),
    canChangeEventStatus: canChangeEventStatus(role),
    canManageReopenRequests: canManageReopenRequests(role),
  };
}
