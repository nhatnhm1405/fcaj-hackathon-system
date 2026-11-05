import { expect, type Page } from '@playwright/test';
import { S1_DEMO } from './s1DemoAccounts';

export const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8080';

const configuredStepPause = Number.parseInt(process.env.E2E_STEP_PAUSE_MS ?? '0', 10);
export const STEP_PAUSE_MS = Number.isFinite(configuredStepPause) ? configuredStepPause : 0;

type ApiEnvelope<T> = { data: T; message?: string };

export type EventRow = {
  id?: number;
  eventId?: number;
  event_id?: number;
  name?: string;
  status?: string;
  trackSelectionMode?: string;
  track_selection_mode?: string;
};

export type TrackRow = {
  id?: number;
  trackId?: number;
  track_id?: number;
  name?: string;
  capacity?: number | null;
};

export type RoundRow = {
  id?: number;
  roundId?: number;
  round_id?: number;
  name?: string;
  orderNumber?: number;
  order_number?: number;
  status?: string;
  topNAdvance?: number | null;
  top_n_advance?: number | null;
  isFinal?: boolean;
  is_final?: boolean;
};

export type TeamRow = {
  teamId?: number;
  team_id?: number;
  id?: number;
  name?: string;
  status?: string;
  trackId?: number | null;
  track_id?: number | null;
  trackName?: string | null;
  track_name?: string | null;
};

export type UserRow = {
  userId?: number;
  user_id?: number;
  id?: number;
  email?: string;
  fullName?: string;
  full_name?: string;
  judgeType?: string | null;
  judge_type?: string | null;
};

export type MentorRosterRow = {
  id: number;
  mentorUserId: number;
  mentorName: string;
  trackId: number;
  trackName: string;
};

export type JudgeRosterRow = {
  id: number;
  judgeUserId: number;
  judgeName: string;
  judgeType?: string | null;
  roundId: number;
  roundName: string;
  isFinal?: boolean;
  trackId?: number | null;
  trackName?: string | null;
};

export type GroupingPreview = {
  leftoverPeople: number;
  proposedTeams?: unknown[];
  warnings?: unknown[];
};

export type GroupingCommit = {
  teamsCreated: number;
  teamsGrown: number;
  peoplePlaced: number;
  unresolvedWarnings: number;
};

export type TimerState = {
  roundId: number;
  phase: 'CONTEST' | 'JUDGING';
  status: 'IDLE' | 'RUNNING' | 'PAUSED' | 'STOPPED' | 'EXPIRED';
  remainingSeconds: number;
};

export type CriteriaRow = {
  criteriaId: number;
  name: string;
  maxScore: number;
  weight: number;
  orderNumber?: number;
};

export type SubmissionRow = {
  submissionId: number;
  teamId: number;
  teamName: string;
  roundId: number;
};

export type ScoringProgressRow = {
  submissionId: number;
  teamId: number;
  teamName: string;
  trackId?: number | null;
  trackName?: string | null;
  assignedJudgeCount: number;
  completedJudgeCount: number;
  complete: boolean;
  judges: {
    judgeUserId: number;
    judgeName: string;
    status: 'NOT_STARTED' | 'DRAFT' | 'INCOMPLETE' | 'FINAL';
  }[];
};

export type RoundResultRow = {
  resultId: number;
  teamId: number;
  teamName: string;
  trackName?: string | null;
  totalScore: number;
  rankPosition: number;
  advanced: boolean;
  isPublished?: boolean;
};

export type SupportRequestRow = {
  requestId: number;
  teamName: string;
  trackId: number;
  trackName: string;
  category: string;
  description: string;
  status: 'OPEN' | 'RESOLVED' | 'CANCELLED';
};

export function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export function eventIdOf(event: EventRow): number {
  const id = event.eventId ?? event.event_id ?? event.id;
  if (typeof id !== 'number') throw new Error(`Event id missing for ${event.name ?? 'unknown event'}`);
  return id;
}

export function trackIdOf(track: TrackRow | TeamRow): number {
  const id = track.trackId ?? track.track_id ?? ('id' in track ? track.id : undefined);
  if (typeof id !== 'number') throw new Error(`Track id missing for ${track.name ?? 'unknown track'}`);
  return id;
}

export function roundIdOf(round: RoundRow): number {
  const id = round.roundId ?? round.round_id ?? round.id;
  if (typeof id !== 'number') throw new Error(`Round id missing for ${round.name ?? 'unknown round'}`);
  return id;
}

export function userIdOf(user: UserRow): number {
  const id = user.userId ?? user.user_id ?? user.id;
  if (typeof id !== 'number') throw new Error(`User id missing for ${user.email ?? 'unknown user'}`);
  return id;
}

export function trackNameOf(team: TeamRow): string | null {
  return team.trackName ?? team.track_name ?? null;
}

export async function gotoAppPage(page: Page, path: string) {
  await page.goto(path, { waitUntil: 'domcontentloaded' });
}

export async function pauseForDemo(page: Page) {
  if (STEP_PAUSE_MS > 0) await page.waitForTimeout(STEP_PAUSE_MS);
}

export async function resetBrowserSession(page: Page) {
  await page.context().clearCookies();
  await gotoAppPage(page, '/');
  await page.evaluate(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
  });
}

export async function signIn(page: Page, email: string, roleLabel?: string) {
  await resetBrowserSession(page);
  await gotoAppPage(page, '/login');
  await page.getByPlaceholder('you@seal.edu').fill(email);
  await page.locator('input[type="password"]').first().fill(S1_DEMO.password);
  await page.getByRole('button', { name: /^LOGIN$/ }).click();
  await page.waitForURL(/\/(dashboard|select-role|pending-approval)/, { timeout: 30_000 });

  if (page.url().includes('/select-role') && roleLabel) {
    await page.getByRole('button', { name: new RegExp(roleLabel, 'i') }).click();
    await page.waitForURL(/\/dashboard/, { timeout: 30_000 });
  }

  await expect(page).not.toHaveURL(/\/login/);
  await dismissLoginModals(page);
}

async function dismissLoginModals(page: Page) {
  for (let i = 0; i < 4; i++) {
    const notificationDismiss = page.getByRole('button', { name: /^DISMISS$/ }).last();
    if (await notificationDismiss.isVisible({ timeout: 500 }).catch(() => false)) {
      await notificationDismiss.click({ force: true });
      await page.waitForTimeout(150);
      continue;
    }

    const guideClose = page.getByRole('button', { name: /^GOT IT$/ }).last();
    if (await guideClose.isVisible({ timeout: 500 }).catch(() => false)) {
      await guideClose.click({ force: true });
      await page.waitForTimeout(150);
      continue;
    }

    break;
  }
}

export async function openCoordinatorPage(page: Page, path: string) {
  await gotoAppPage(page, path);
  if (page.url().includes('/select-role')) {
    await page.getByRole('button', { name: /Coordinator/i }).click();
    await page.waitForURL(/\/dashboard\/coordinator|\/coordinator/, { timeout: 30_000 });
    await gotoAppPage(page, path);
  }
}

export async function appApi<T>(page: Page, method: string, path: string, payload?: unknown): Promise<T> {
  if (page.url() === 'about:blank') {
    await gotoAppPage(page, '/');
  }

  const cookies = await page.context().cookies(API_URL);
  const authCookie = cookies.find((cookie) => cookie.name === 'seal_auth_token');
  const headers: Record<string, string> = {};
  if (authCookie?.value) headers.Authorization = `Bearer ${authCookie.value}`;

  const response = await page.context().request.fetch(`${API_URL}${path}`, {
    method: method.toUpperCase(),
    headers,
    ...(payload !== undefined ? { data: payload } : {}),
  });
  const text = await response.text();
  let body: unknown = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
  }

  expect(response.ok(), `${method.toUpperCase()} ${path} returned ${response.status()}: ${JSON.stringify(body)}`).toBeTruthy();
  return (body as ApiEnvelope<T>).data;
}

export const apiGet = <T>(page: Page, path: string) => appApi<T>(page, 'GET', path);
export const apiPost = <T>(page: Page, path: string, payload?: unknown) => appApi<T>(page, 'POST', path, payload);
export const apiPut = <T>(page: Page, path: string, payload?: unknown) => appApi<T>(page, 'PUT', path, payload);

export async function findS1Event(page: Page): Promise<EventRow> {
  const events = await apiGet<EventRow[]>(page, '/api/events');
  const event = events.find((item) => item.name === S1_DEMO.eventName);
  expect(event, `${S1_DEMO.eventName} should exist`).toBeTruthy();
  return event as EventRow;
}

export async function getS1Tracks(page: Page, eventId: number) {
  return apiGet<TrackRow[]>(page, `/api/events/${eventId}/tracks`);
}

export async function getS1Rounds(page: Page, eventId: number) {
  const rounds = await apiGet<RoundRow[]>(page, `/api/events/${eventId}/rounds`);
  return rounds.sort((a, b) => (a.orderNumber ?? a.order_number ?? roundIdOf(a)) - (b.orderNumber ?? b.order_number ?? roundIdOf(b)));
}

export function prelimRound(rounds: RoundRow[]) {
  const round = rounds.find((item) => !(item.isFinal ?? item.is_final));
  expect(round, 'S1 preliminary round should exist').toBeTruthy();
  return round as RoundRow;
}

export function userByEmail(staff: UserRow[], email: string): UserRow {
  const user = staff.find((item) => item.email?.toLowerCase() === email.toLowerCase());
  expect(user, `Staff account ${email} should exist`).toBeTruthy();
  return user as UserRow;
}

export function leaderEmailForTeam(teamName: string): string | undefined {
  return S1_DEMO.submissionLeaders.find((item) => item.teamName === teamName)?.email;
}
