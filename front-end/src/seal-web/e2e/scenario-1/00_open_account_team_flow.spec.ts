import { expect, type Locator, type Page, test } from '@playwright/test';
import { S1_DEMO } from './s1DemoAccounts';

const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8080';
const configuredStepPause = Number.parseInt(process.env.E2E_STEP_PAUSE_MS ?? '0', 10);
const STEP_PAUSE_MS = Number.isFinite(configuredStepPause) ? configuredStepPause : 0;

type ApiResponse<T> = { data: T; message?: string };
type EventRow = {
  id?: number;
  eventId?: number;
  event_id?: number;
  name?: string;
  status?: string;
  trackSelectionMode?: string;
  track_selection_mode?: string;
};
type TrackRow = { name?: string };
type RoundRow = {
  id?: number;
  roundId?: number;
  round_id?: number;
  name?: string;
  topNAdvance?: number | null;
  top_n_advance?: number | null;
  isFinal?: boolean;
  is_final?: boolean;
};
type CriteriaRow = { name?: string };
type TeamRow = {
  name?: string;
  status?: string;
  members?: unknown[];
};
type NewStudent = {
  fullName: string;
  email: string;
  studentId: string;
};

function eventIdOf(event: EventRow): number {
  const id = event.eventId ?? event.event_id ?? event.id;
  if (typeof id !== 'number') {
    throw new Error(`Event id missing for ${event.name ?? 'unknown event'}`);
  }
  return id;
}

function roundIdOf(round: RoundRow): number {
  const id = round.roundId ?? round.round_id ?? round.id;
  if (typeof id !== 'number') {
    throw new Error(`Round id missing for ${round.name ?? 'unknown round'}`);
  }
  return id;
}

async function apiGet<T>(page: Page, path: string): Promise<T> {
  const res = await page.request.get(`${API_URL}${path}`);
  expect(res.ok(), `${path} returned ${res.status()}`).toBeTruthy();
  const body = await res.json() as ApiResponse<T>;
  return body.data;
}

async function gotoAppPage(page: Page, path: string) {
  await page.goto(path, { waitUntil: 'domcontentloaded' });
}

async function resetBrowserSession(page: Page) {
  await page.context().clearCookies();
  await gotoAppPage(page, '/');
  await page.evaluate(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
  });
}

async function pauseForDemo(page: Page) {
  if (STEP_PAUSE_MS > 0) {
    await page.waitForTimeout(STEP_PAUSE_MS);
  }
}

async function signIn(page: Page, email: string, roleLabel?: string) {
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
}

async function openCoordinatorPage(page: Page, path: string) {
  await gotoAppPage(page, path);
  if (page.url().includes('/select-role')) {
    await page.getByRole('button', { name: /Coordinator/i }).click();
    await page.waitForURL(/\/dashboard\/coordinator|\/coordinator/, { timeout: 30_000 });
    await gotoAppPage(page, path);
  }
}

async function findS1Event(page: Page): Promise<EventRow> {
  const events = await apiGet<EventRow[]>(page, '/api/events');
  const event = events.find((item) => item.name === S1_DEMO.eventName);
  expect(event, `${S1_DEMO.eventName} should be seeded`).toBeTruthy();
  return event as EventRow;
}

function rowByText(page: Page, text: string): Locator {
  return page.locator('tbody tr', { hasText: text }).first();
}

function makeNewStudent(): NewStudent {
  const stamp = Date.now().toString();
  return {
    fullName: `S1 Joiner ${stamp.slice(-6)}`,
    email: `s1.joiner.${stamp}@example.com`,
    studentId: `SE${stamp.slice(-8)}`,
  };
}

async function approvePendingAccount(page: Page, student: NewStudent) {
  await signIn(page, S1_DEMO.coordinator.email, S1_DEMO.coordinator.roleLabel);
  await openCoordinatorPage(page, '/coordinator/accounts');
  await expect(page.getByRole('heading', { name: /Accounts/i })).toBeVisible();
  await page.getByPlaceholder('Search by name or email...').fill(student.email);

  const accountRow = rowByText(page, student.email);
  await expect(accountRow).toBeVisible();
  await accountRow.click();
  await page.getByRole('button', { name: /^APPROVE SELECTED \(1\)$/ }).click();
  const approveResponse = page.waitForResponse((response) =>
    response.request().method() === 'PUT'
      && /\/api\/account-approvals\/\d+\/approve$/.test(new URL(response.url()).pathname)
      && response.ok()
  );
  await page.getByRole('button', { name: /^APPROVE 1$/ }).click();
  await approveResponse;
  await expect(accountRow).toHaveCount(0);
  await expect(page.getByText('No pending accounts')).toBeVisible();
}

async function requestToJoinArsenal(page: Page, student: NewStudent) {
  await signIn(page, student.email);
  await gotoAppPage(page, '/dashboard');
  await page.getByRole('button', { name: /^WAIT FOR INVITE$/ }).click();
  await expect(page.getByText('Find a Team')).toBeVisible();
  await page.getByPlaceholder('Team name, leader, track...').fill(S1_DEMO.targetTeamName);
  await page.getByRole('button', { name: /^SEARCH$/ }).click();

  const teamCard = page.getByRole('button', { name: new RegExp(`^${S1_DEMO.targetTeamName}\\s+`) }).locator('xpath=..').first();
  const teamToggle = teamCard.getByRole('button', { name: new RegExp(`^${S1_DEMO.targetTeamName}\\s+`) });
  await expect(teamToggle).toBeVisible();
  await teamToggle.click();
  const requestResponse = page.waitForResponse((response) =>
    response.request().method() === 'POST'
      && /\/api\/join-requests\/teams\/\d+$/.test(new URL(response.url()).pathname)
      && response.ok()
  );
  await teamCard.getByRole('button', { name: /^REQUEST TO JOIN$/ }).click();
  await requestResponse;
  await expect(teamCard.getByRole('button', { name: /^REQUEST ALREADY SENT$/ })).toBeVisible();
}

async function acceptJoinRequestAsLeader(page: Page, student: NewStudent) {
  await signIn(page, S1_DEMO.arsenalLeader.email);
  await gotoAppPage(page, '/team/view');
  await expect(page.getByRole('heading', { name: S1_DEMO.targetTeamName })).toBeVisible();
  await page.getByRole('button', { name: /^JOIN REQUESTS/ }).click();

  const requestCard = page
    .locator(`xpath=//div[normalize-space(.)="${student.email}"]/ancestor::div[.//button[normalize-space(.)="ACCEPT"]][1]`);
  await expect(requestCard).toBeVisible();
  const acceptResponse = page.waitForResponse((response) =>
    response.request().method() === 'PUT'
      && /\/api\/join-requests\/\d+\/accept$/.test(new URL(response.url()).pathname)
      && response.ok()
  );
  await requestCard.getByRole('button', { name: /^ACCEPT$/ }).click();
  await acceptResponse;
  await expect(page.getByText(`${student.fullName} has joined the team.`)).toBeVisible();
  await expect(page.getByText('4/5')).toBeVisible();
}

async function inviteExistingMemberAsLeader(page: Page) {
  await page.getByRole('button', { name: /^INVITE MEMBER$/ }).click();
  await expect(page.getByPlaceholder('min 2 characters')).toBeVisible();
  await page.getByPlaceholder('min 2 characters').fill(S1_DEMO.inviteMember.email);
  await page.getByRole('button', { name: /^SEARCH$/ }).click();

  const result = page.locator('div', { hasText: S1_DEMO.inviteMember.email }).filter({ hasText: 'INVITE' }).first();
  await expect(result).toBeVisible();
  await result.getByRole('button', { name: /^INVITE$/ }).click();
  await expect(page.getByText('Send team invitation?')).toBeVisible();
  const inviteResponse = page.waitForResponse((response) =>
    response.request().method() === 'POST'
      && /\/api\/invites\/teams\/\d+$/.test(new URL(response.url()).pathname)
      && response.ok()
  );
  await page.getByRole('button', { name: /^SEND INVITE$/ }).click();
  await inviteResponse;
  await expect(page.getByText(/Invitation sent to/i).first()).toBeVisible();
}

async function acceptInviteAsExistingMember(page: Page) {
  await signIn(page, S1_DEMO.inviteMember.email);
  await gotoAppPage(page, '/dashboard');
  await page.getByRole('button', { name: /^WAIT FOR INVITE$/ }).click();

  const inviteCard = page.locator(
    `xpath=//button[normalize-space(.)="ACCEPT INVITE"]/ancestor::div[.//div[normalize-space(.)="${S1_DEMO.targetTeamName}"] and .//*[normalize-space(.)="TEAM APPROVED"]][1]`,
  );
  await expect(inviteCard).toBeVisible();
  await inviteCard.getByRole('button', { name: /^ACCEPT INVITE$/ }).click();
  await expect(page.getByText('Accept team invitation?')).toBeVisible();
  const acceptDialog = page.locator('xpath=//h2[normalize-space(.)="Accept team invitation?"]/parent::div').last();
  const acceptInviteResponse = page.waitForResponse((response) =>
    response.request().method() === 'PUT'
      && /\/api\/invites\/\d+\/accept$/.test(new URL(response.url()).pathname)
      && response.ok()
  );
  await acceptDialog.getByRole('button', { name: /^ACCEPT INVITE$/ }).click();
  await acceptInviteResponse;

  await gotoAppPage(page, '/team/view');
  await expect(page.getByRole('heading', { name: S1_DEMO.targetTeamName })).toBeVisible();
  await expect(page.getByText('5/5')).toBeVisible();
}

async function verifyOpenSeedData(page: Page) {
  const event = await findS1Event(page);
  expect(event.status).toBe('OPEN');
  expect(event.trackSelectionMode ?? event.track_selection_mode).toBe('RANDOM');
  const eventId = eventIdOf(event);

  const tracks = await apiGet<TrackRow[]>(page, `/api/events/${eventId}/tracks`);
  expect(tracks.map((track) => track.name)).toEqual(expect.arrayContaining([...S1_DEMO.tracks]));

  const rounds = await apiGet<RoundRow[]>(page, `/api/events/${eventId}/rounds`);
  expect(rounds).toHaveLength(S1_DEMO.expectedRoundCount);
  expect(rounds.some((round) => (round.topNAdvance ?? round.top_n_advance) === 2)).toBeTruthy();
  expect(rounds.some((round) => Boolean(round.isFinal ?? round.is_final))).toBeTruthy();

  for (const round of rounds) {
    const criteria = await apiGet<CriteriaRow[]>(page, `/api/events/${eventId}/rounds/${roundIdOf(round)}/criteria`);
    expect(criteria).toHaveLength(S1_DEMO.expectedCriteriaCountPerRound);
  }

  const teams = await apiGet<TeamRow[]>(page, `/api/teams/event/${eventId}`);
  expect(teams.length).toBeGreaterThanOrEqual(15);
  expect(teams.some((team) => team.name === S1_DEMO.targetTeamName)).toBeTruthy();
  expect(teams.some((team) => team.name === S1_DEMO.pendingTeamName && team.status === 'PENDING')).toBeTruthy();
}

async function approveRealMadrid(page: Page) {
  await openCoordinatorPage(page, '/coordinator/teams');
  await expect(page.getByRole('heading', { name: /Teams/i })).toBeVisible();

  const teamRow = rowByText(page, S1_DEMO.pendingTeamName);
  await expect(teamRow).toBeVisible();
  await teamRow.click();
  await expect(page.getByText('Approve this team?')).not.toBeVisible();
  await page.getByRole('button', { name: /^APPROVE TEAM$/ }).click();
  await expect(page.getByText('Approve this team?')).toBeVisible();
  const approveDialog = page.locator('xpath=//h2[normalize-space(.)="Approve this team?"]/parent::div').last();
  const approveTeamResponse = page.waitForResponse((response) =>
    response.request().method() === 'PUT'
      && /\/api\/teams\/\d+\/approve$/.test(new URL(response.url()).pathname)
      && response.ok()
  );
  await approveDialog.getByRole('button', { name: /^APPROVE TEAM$/ }).click();
  await approveTeamResponse;
}

async function closeRegistration(page: Page) {
  await openCoordinatorPage(page, '/coordinator/events');
  await expect(page.getByRole('heading', { name: /Events/i })).toBeVisible();
  await expect(page.getByText(S1_DEMO.eventName).first()).toBeVisible();
  await expect(page.getByText('OPEN').first()).toBeVisible();
  await page.getByRole('button', { name: /^CLOSE REGISTRATION$/ }).click();
  await expect(page.getByText('Change status: CLOSE REGISTRATION?')).toBeVisible();
  const statusResponse = page.waitForResponse((response) =>
    response.request().method() === 'PUT'
      && /\/api\/events\/\d+$/.test(new URL(response.url()).pathname)
      && response.ok()
  );
  await page.getByRole('button', { name: /^CONFIRM$/ }).click();
  await statusResponse;
  await expect(page.getByText('SETUP').first()).toBeVisible();
}

test.describe.configure({ mode: 'serial' });

test('S1 00 - open account and team flow, then close registration', async ({ page }) => {
  const student = makeNewStudent();

  await test.step('register a brand new FPT student account', async () => {
    await gotoAppPage(page, '/register');
    await page.getByPlaceholder('Your full name').fill(student.fullName);
    await page.getByPlaceholder('you@seal.edu').fill(student.email);
    await page.locator('input[type="password"]').nth(0).fill(S1_DEMO.password);
    await page.locator('input[type="password"]').nth(1).fill(S1_DEMO.password);
    await page.getByPlaceholder('SE000000').fill(student.studentId);
    await page.getByRole('button', { name: /^SUBMIT REGISTRATION$/ }).click();
    await expect(page.getByText('Application Submitted')).toBeVisible();
    await pauseForDemo(page);
  });

  await test.step('coordinator approves the new account', async () => {
    await approvePendingAccount(page, student);
    await pauseForDemo(page);
  });

  await test.step('new student requests to join Arsenal', async () => {
    await requestToJoinArsenal(page, student);
    await pauseForDemo(page);
  });

  await test.step('Arsenal leader accepts the join request', async () => {
    await acceptJoinRequestAsLeader(page, student);
    await pauseForDemo(page);
  });

  await test.step('Arsenal leader invites p42', async () => {
    await inviteExistingMemberAsLeader(page);
    await pauseForDemo(page);
  });

  await test.step('p42 accepts the Arsenal invite', async () => {
    await acceptInviteAsExistingMember(page);
    await pauseForDemo(page);
  });

  await test.step('coordinator verifies S1 open data', async () => {
    await signIn(page, S1_DEMO.coordinator.email, S1_DEMO.coordinator.roleLabel);
    await verifyOpenSeedData(page);
    await pauseForDemo(page);
  });

  await test.step('coordinator approves Real Madrid pending team', async () => {
    await approveRealMadrid(page);
    await pauseForDemo(page);
  });

  await test.step('coordinator closes registration and event becomes SETUP', async () => {
    await closeRegistration(page);
    const event = await findS1Event(page);
    expect(event.status).toBe('SETUP');

    const teams = await apiGet<TeamRow[]>(page, `/api/teams/event/${eventIdOf(event)}`);
    const arsenal = teams.find((team) => team.name === S1_DEMO.targetTeamName);
    const realMadrid = teams.find((team) => team.name === S1_DEMO.pendingTeamName);
    expect(arsenal?.members ?? []).toHaveLength(5);
    expect(realMadrid?.status).toBe('APPROVED');
    expect(teams.filter((team) => team.status === 'PENDING')).toHaveLength(0);
  });
});
