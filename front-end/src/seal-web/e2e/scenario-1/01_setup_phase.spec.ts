import { expect, test, type Page } from '@playwright/test';
import { S1_DEMO } from './s1DemoAccounts';
import {
  apiGet,
  apiPost,
  escapeRegExp,
  eventIdOf,
  findS1Event,
  getS1Rounds,
  getS1Tracks,
  GroupingCommit,
  GroupingPreview,
  JudgeRosterRow,
  MentorRosterRow,
  openCoordinatorPage,
  pauseForDemo,
  prelimRound,
  roundIdOf,
  signIn,
  TeamRow,
  trackIdOf,
  UserRow,
  userByEmail,
  userIdOf,
} from './s1TestUtils';

const STANDARD_RUBRIC_CRITERIA = ['Idea', 'Technical', 'UI/UX', 'Completeness', 'Presentation'] as const;
const configuredMentorJudgePause = Number.parseInt(process.env.E2E_MENTOR_JUDGE_PAUSE_MS ?? '2000', 10);
const MENTOR_JUDGE_PAUSE_MS = Number.isFinite(configuredMentorJudgePause) ? configuredMentorJudgePause : 2000;

function problemFileConfig() {
  const filePath = process.env.E2E_PROBLEM_FILE?.trim();
  if (!filePath) throw new Error('E2E_PROBLEM_FILE must point to the problem file used by the setup demo.');

  const fileName = filePath.split(/[\\/]/).pop();
  if (!fileName) throw new Error(`Cannot determine the problem file name from: ${filePath}`);
  return { filePath, fileName };
}

async function applyLeftoverGrouping(page: Page, eventId: number) {
  const preview = await apiGet<GroupingPreview>(page, `/api/teams/event/${eventId}/leftover-grouping/preview`);
  expect(preview.leftoverPeople, 'leftover preview should be readable').toBeGreaterThanOrEqual(0);

  if (preview.leftoverPeople > 0) {
    const reason = encodeURIComponent('S1 setup demo automatic leftover grouping');
    const commit = await apiPost<GroupingCommit>(
      page,
      `/api/teams/event/${eventId}/leftover-grouping/commit?reason=${reason}`,
    );
    expect(commit.peoplePlaced, 'leftover grouping should place people').toBeGreaterThan(0);
  }

  const after = await apiGet<GroupingPreview>(page, `/api/teams/event/${eventId}/leftover-grouping/preview`);
  expect(after.leftoverPeople, 'all setup leftovers should be resolved').toBe(0);
}

async function drawTracks(page: Page, eventId: number) {
  const before = await apiGet<TeamRow[]>(page, `/api/teams/event/${eventId}`);
  const unassigned = before.filter((team) => team.status === 'APPROVED' && (team.trackId ?? team.track_id ?? null) == null);

  if (unassigned.length > 0) {
    await apiPost<TeamRow[]>(page, `/api/teams/event/${eventId}/draw-tracks?includeAssigned=false`);
  }

  const teams = await apiGet<TeamRow[]>(page, `/api/teams/event/${eventId}`);
  expect(teams.filter((team) => team.status === 'APPROVED' && (team.trackId ?? team.track_id ?? null) == null)).toHaveLength(0);
  for (const trackName of S1_DEMO.tracks) {
    const count = teams.filter((team) => team.status === 'APPROVED' && (team.trackName ?? team.track_name) === trackName).length;
    expect(count, `${trackName} should have at least 2 approved teams after draw`).toBeGreaterThanOrEqual(2);
  }
}

async function assignMentors(page: Page, eventId: number) {
  const tracks = await getS1Tracks(page, eventId);
  const staff = await apiGet<UserRow[]>(page, '/api/coordinator/staff');
  let roster = await apiGet<MentorRosterRow[]>(page, `/api/coordinator/assignments/mentors?eventId=${eventId}`);

  for (const mentor of S1_DEMO.mentors) {
    const track = tracks.find((item) => item.name === mentor.trackName);
    expect(track, `Track ${mentor.trackName} should exist`).toBeTruthy();
    const trackId = trackIdOf(track!);
    if (roster.some((item) => item.trackId === trackId)) continue;

    const account = userByEmail(staff, mentor.email);
    await apiPost(page, '/api/coordinator/assignments/mentors', {
      mentorUserId: userIdOf(account),
      trackId,
    });
    roster = await apiGet<MentorRosterRow[]>(page, `/api/coordinator/assignments/mentors?eventId=${eventId}`);
  }

  for (const trackName of S1_DEMO.tracks) {
    expect(roster.some((item) => item.trackName === trackName), `${trackName} should have a mentor`).toBeTruthy();
  }

  return roster;
}

async function assignPrelimJudges(page: Page, eventId: number) {
  const tracks = await getS1Tracks(page, eventId);
  const rounds = await getS1Rounds(page, eventId);
  const prelim = prelimRound(rounds);
  const prelimId = roundIdOf(prelim);
  let staff = await apiGet<UserRow[]>(page, '/api/coordinator/staff');
  let roster = await apiGet<JudgeRosterRow[]>(page, `/api/coordinator/assignments/judges?eventId=${eventId}`);

  const judgePool = [
    ...S1_DEMO.judges.map((judge) => ({ ...judge, createIfMissing: false, fullName: judge.email })),
    ...S1_DEMO.setupGuestJudges.map((judge) => ({ ...judge, createIfMissing: true })),
  ];
  let judgeIndex = 0;

  for (const trackName of S1_DEMO.tracks) {
    const track = tracks.find((item) => item.name === trackName);
    expect(track, `Track ${trackName} should exist`).toBeTruthy();
    const trackId = trackIdOf(track!);

    while (roster.filter((item) => item.roundId === prelimId && item.trackId === trackId).length < 2) {
      const account = judgePool[judgeIndex++];
      expect(account, `Not enough judge accounts to assign 2 judges for ${trackName}`).toBeTruthy();

      const activeRoundJudgeIds = new Set(
        roster.filter((item) => item.roundId === prelimId).map((item) => item.judgeUserId),
      );
      const existing = staff.find((item) => item.email?.toLowerCase() === account.email.toLowerCase());
      if (existing && activeRoundJudgeIds.has(userIdOf(existing))) {
        continue;
      }

      if (existing) {
        await apiPost(page, '/api/coordinator/assignments/judges', {
          judgeUserId: userIdOf(existing),
          roundId: prelimId,
          trackId,
        });
      } else {
        expect(account.createIfMissing, `${account.email} must exist or be a planned guest judge`).toBeTruthy();
        await apiPost(page, '/api/coordinator/guest-judges', {
          fullName: account.fullName,
          email: account.email,
          password: S1_DEMO.password,
          roundId: prelimId,
          trackId,
        });
      }

      staff = await apiGet<UserRow[]>(page, '/api/coordinator/staff');
      roster = await apiGet<JudgeRosterRow[]>(page, `/api/coordinator/assignments/judges?eventId=${eventId}`);
    }
  }

  const prelimRoster = roster.filter((item) => item.roundId === prelimId);
  expect(prelimRoster).toHaveLength(S1_DEMO.tracks.length * 2);
  for (const trackName of S1_DEMO.tracks) {
    expect(prelimRoster.filter((item) => item.trackName === trackName)).toHaveLength(2);
  }
}

async function uploadProblemForEveryTrack(page: Page, eventId: number) {
  const { filePath, fileName } = problemFileConfig();
  const tracks = await getS1Tracks(page, eventId);

  await openCoordinatorPage(page, '/coordinator/events');
  await page.getByRole('button', { name: /^PROBLEMS$/i }).click();

  for (const trackName of S1_DEMO.tracks) {
    const track = tracks.find((item) => item.name === trackName);
    expect(track, `Track ${trackName} should exist before uploading its problem`).toBeTruthy();
    const trackId = trackIdOf(track!);
    const row = page.locator('.row-actionable').filter({
      has: page.locator('input[type="file"]'),
      hasText: trackName,
    });
    await expect(row, `Problem row for ${trackName} should be visible`).toHaveCount(1);
    await row.scrollIntoViewIfNeeded();
    const fileInput = row.locator('input[type="file"]');
    await expect(fileInput).toHaveCount(1);
    const uploadResponsePromise = page.waitForResponse((response) =>
      response.request().method() === 'POST'
        && new URL(response.url()).pathname === `/api/events/${eventId}/tracks/${trackId}/problem`,
    );
    await fileInput.setInputFiles(filePath);
    const uploadResponse = await uploadResponsePromise;
    expect(uploadResponse.ok(), `Problem upload should succeed for ${trackName}`).toBeTruthy();
    await expect(row.getByRole('button', { name: fileName, exact: true })).toBeVisible();
  }

  await expect(page.getByRole('button', { name: fileName, exact: true })).toHaveCount(S1_DEMO.tracks.length);
}

async function applyStandardRubric(page: Page, eventId: number) {
  const templateLabel = process.env.E2E_CRITERIA_TEMPLATE?.trim() || 'Standard Rubric (5)';
  const rounds = await getS1Rounds(page, eventId);
  const prelim = prelimRound(rounds);
  const prelimId = roundIdOf(prelim);
  const prelimName = prelim.name;
  expect(prelimName, 'Preliminary round name should exist').toBeTruthy();

  await expect(page).toHaveURL(/\/coordinator\/events/);
  await page.getByRole('button', { name: /^CRITERIA$/i }).click();
  await page.getByRole('button', {
    name: new RegExp(`${escapeRegExp(prelimName!)}$`, 'i'),
  }).click();

  const templateSelect = page.locator('select').filter({
    has: page.locator('option', { hasText: templateLabel }),
  });
  await expect(templateSelect, `${templateLabel} should exist in the template dropdown`).toHaveCount(1);
  await templateSelect.click();
  await pauseForDemo(page);
  await templateSelect.selectOption({ label: templateLabel });
  const selectedTemplateId = await templateSelect.inputValue();

  const applyButton = page.getByRole('button', { name: /^APPLY$/i });
  await expect(applyButton).toBeEnabled();
  const applyResponsePromise = page.waitForResponse((response) =>
    response.request().method() === 'POST'
      && new URL(response.url()).pathname
        === `/api/events/${eventId}/rounds/${prelimId}/criteria/apply-template/${selectedTemplateId}`,
  );
  await applyButton.click();
  const applyResponse = await applyResponsePromise;
  expect(applyResponse.ok(), `${templateLabel} should be applied successfully`).toBeTruthy();
  await expect(page.getByText('TEMPLATE APPLIED', { exact: true })).toBeVisible();
  for (const criteriaName of STANDARD_RUBRIC_CRITERIA) {
    await expect(page.getByText(criteriaName, { exact: true }).first()).toBeVisible();
  }
}

test.describe.configure({ mode: 'serial' });

test('S1 01 - setup phase grouping, track draw, mentors, and judges', async ({ page }) => {
  await signIn(page, S1_DEMO.coordinator.email, S1_DEMO.coordinator.roleLabel);
  const event = await findS1Event(page);
  expect(event.status).toBe('SETUP');
  const eventId = eventIdOf(event);

  await test.step('coordinator reviews S1 in SETUP', async () => {
    await openCoordinatorPage(page, '/coordinator/events');
    await expect(page.getByText(S1_DEMO.eventName).first()).toBeVisible();
    await expect(page.getByText('SETUP').first()).toBeVisible();
    await pauseForDemo(page);
  });

  await test.step('apply leftover grouping', async () => {
    await applyLeftoverGrouping(page, eventId);
    await pauseForDemo(page);
  });

  await test.step('draw tracks for all approved teams', async () => {
    await drawTracks(page, eventId);
    await pauseForDemo(page);
  });

  await test.step('assign one mentor per track', async () => {
    const mentorRoster = await assignMentors(page, eventId);
    await openCoordinatorPage(page, '/coordinator/judges');
    await expect(page.getByRole('heading', { name: /Assignments/i })).toBeVisible();
    for (const mentor of mentorRoster) {
      await expect(page.getByText(mentor.mentorName, { exact: true }).first()).toBeVisible();
    }
    await page.waitForTimeout(MENTOR_JUDGE_PAUSE_MS);
  });

  await test.step('assign two judges per preliminary track', async () => {
    await assignPrelimJudges(page, eventId);
    await openCoordinatorPage(page, '/coordinator/judges');
    await expect(page.getByRole('heading', { name: /Assignments/i })).toBeVisible();
    await expect(page.getByText('2 JUDGES')).toHaveCount(S1_DEMO.tracks.length);
    await pauseForDemo(page);
  });

  await test.step('upload the problem file for all four tracks', async () => {
    await uploadProblemForEveryTrack(page, eventId);
    await pauseForDemo(page);
  });

  await test.step('apply Standard Rubric to the preliminary round', async () => {
    await applyStandardRubric(page, eventId);
    await pauseForDemo(page);
  });

  await test.step('S1 remains in SETUP for the next bat file', async () => {
    const after = await findS1Event(page);
    expect(after.status).toBe('SETUP');
  });
});
