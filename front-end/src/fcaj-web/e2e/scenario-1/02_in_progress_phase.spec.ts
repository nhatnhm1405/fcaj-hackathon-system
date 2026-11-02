import { expect, test, type Page } from '@playwright/test';
import { S1_DEMO } from './s1DemoAccounts';
import {
  apiGet,
  apiPost,
  apiPut,
  CriteriaRow,
  eventIdOf,
  escapeRegExp,
  findS1Event,
  getS1Rounds,
  JudgeRosterRow,
  leaderEmailForTeam,
  openCoordinatorPage,
  pauseForDemo,
  prelimRound,
  RoundResultRow,
  roundIdOf,
  ScoringProgressRow,
  signIn,
  SubmissionRow,
  SupportRequestRow,
  TeamRow,
  TimerState,
  trackNameOf,
  UserRow,
  userIdOf,
} from './s1TestUtils';

const contestSeconds = Number.parseInt(process.env.S1_CONTEST_SECONDS ?? '600', 10);
const judgingSeconds = Number.parseInt(process.env.S1_JUDGING_SECONDS ?? '3600', 10);

type SubmissionTarget = TeamRow & { leaderEmail: string; trackName: string };

function selectSubmissionTargets(teams: TeamRow[]): SubmissionTarget[] {
  const approved = teams
    .filter((team) => team.status === 'APPROVED' && trackNameOf(team) != null && leaderEmailForTeam(team.name ?? '') != null)
    .map((team) => ({ ...team, leaderEmail: leaderEmailForTeam(team.name ?? '')!, trackName: trackNameOf(team)! }));

  const selected: SubmissionTarget[] = [];
  const arsenal = approved.find((team) => team.name === S1_DEMO.targetTeamName);
  if (arsenal) selected.push(arsenal);

  const coveredTracks = new Set(selected.map((team) => team.trackName));
  for (const trackName of S1_DEMO.tracks) {
    if (coveredTracks.has(trackName)) continue;
    const candidate = approved.find((team) => team.trackName === trackName && !selected.some((item) => item.name === team.name));
    if (candidate) {
      selected.push(candidate);
      coveredTracks.add(trackName);
    }
  }

  expect(selected.length, 'at least one known seeded team should be available for submission').toBeGreaterThan(0);
  return selected;
}

function submissionPayload(teamName: string, roundId: number) {
  const slug = teamName.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'team';
  return {
    roundId,
    repoUrl: `https://github.com/seal-demo/e2e-${slug}`,
    demoUrl: `https://demo.seal.dev/e2e-${slug}`,
    slideUrl: `https://slides.seal.dev/e2e-${slug}`,
    description: `S1 E2E submission for ${teamName}.`,
  };
}

function scorePayload(submissionId: number, criteria: CriteriaRow[], seed: number) {
  return {
    submissionId,
    draft: false,
    scores: criteria.map((criterion, index) => {
      const max = Number(criterion.maxScore);
      const value = Math.min(max, 7.2 + ((seed + index) % 5) * 0.45);
      return {
        criteriaId: criterion.criteriaId,
        value: Number(value.toFixed(2)),
        comment: `S1 E2E score for ${criterion.name}.`,
      };
    }),
  };
}

async function startEventIfNeeded(page: Page, eventId: number) {
  const event = await findS1Event(page);
  if (event.status === 'IN_PROGRESS') return;
  expect(event.status).toBe('SETUP');

  await openCoordinatorPage(page, '/coordinator/events');
  await expect(page.getByText('SETUP').first()).toBeVisible();
  await apiPut(page, `/api/events/${eventId}`, { status: 'IN_PROGRESS' });
  await openCoordinatorPage(page, '/coordinator/events');
  await expect(page.getByText('IN_PROGRESS').first()).toBeVisible();
}

async function startContestTimer(page: Page, eventId: number, roundId: number) {
  const timer = await apiPost<TimerState>(page, `/api/events/${eventId}/rounds/${roundId}/timer/CONTEST/start`, {
    durationSeconds: contestSeconds,
    milestoneMinutes: [5, 1],
    notifyAtHalf: true,
  });
  expect(timer.status).toBe('RUNNING');
  expect(timer.remainingSeconds).toBeGreaterThan(0);
}

async function requestMentorSupport(page: Page) {
  const description = 'S1 demo: Arsenal needs mentor support before final submission.';
  await signIn(page, S1_DEMO.arsenalLeader.email);
  await page.goto('/team/view', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: S1_DEMO.targetTeamName })).toBeVisible();
  await page.getByRole('button', { name: /^REQUEST HELP$/ }).click();
  await page.locator('textarea').fill(description);
  const requestResponse = page.waitForResponse((response) =>
    response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/support-requests'
      && response.ok()
  );
  await page.getByRole('button', { name: /^REQUEST SUPPORT$/ }).click();
  await requestResponse;
  await expect(page.getByText('SUPPORT OPEN')).toBeVisible();
}

async function resolveMentorSupport(page: Page, mentorEmail: string, trackName: string) {
  await signIn(page, mentorEmail, 'Mentor');
  await page.goto('/mentor/tracks', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: /My Tracks/i })).toBeVisible();
  const supportToggle = page.locator('button', { hasText: 'SUPPORT REQUESTS' }).filter({ hasText: trackName }).first();
  await expect(supportToggle).toBeVisible();
  await supportToggle.click();
  await expect(page.getByText(S1_DEMO.targetTeamName).first()).toBeVisible();
  const resolveResponse = page.waitForResponse((response) =>
    response.request().method() === 'PUT'
      && /\/api\/mentor\/support-requests\/\d+\/resolve$/.test(new URL(response.url()).pathname)
      && response.ok()
  );
  await page.getByRole('button', { name: /^MARK RESOLVED$/ }).click();
  await resolveResponse;
  await expect(page.getByText('RESOLVED').first()).toBeVisible();
}

async function verifySupportResolvedForLeader(page: Page) {
  await signIn(page, S1_DEMO.arsenalLeader.email);
  await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText(/Support resolved/i).first()).toBeVisible();
  const requests = await apiGet<SupportRequestRow[]>(page, '/api/support-requests/mine');
  expect(requests.some((item) => item.teamName === S1_DEMO.targetTeamName && item.status === 'RESOLVED')).toBeTruthy();
}

async function submitArsenalViaUi(page: Page, roundId: number) {
  await signIn(page, S1_DEMO.arsenalLeader.email);
  await page.goto('/team/submit', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: /Submit Project/i })).toBeVisible();
  const payload = submissionPayload(S1_DEMO.targetTeamName, roundId);
  await page.getByPlaceholder('https://github.com/your-team/project').fill(payload.repoUrl);
  await page.getByPlaceholder('https://demo.example.com').fill(payload.demoUrl);
  await page.getByPlaceholder('https://slides.google.com/...').fill(payload.slideUrl);
  await page.locator('textarea[placeholder="Brief description of your submission"]').fill(payload.description);
  const submitButton = page.getByRole('button', { name: /^(SUBMIT|UPDATE SUBMISSION)$/ });
  await expect(submitButton).toBeEnabled();
  const submitResponse = page.waitForResponse((response) =>
    response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/submissions'
      && response.ok()
  );
  await submitButton.click();
  await submitResponse;
  await expect(page.getByText(/SUBMISSION (SAVED|UPDATED)|Submission (saved|updated)/).first()).toBeVisible();
}

async function submitOtherTargetsViaApi(page: Page, roundId: number, targets: SubmissionTarget[]) {
  for (const target of targets) {
    if (target.name === S1_DEMO.targetTeamName) continue;
    await signIn(page, target.leaderEmail);
    await apiPost(page, '/api/submissions', submissionPayload(target.name ?? 'team', roundId));
  }
}

async function stopContestTimerFromUi(page: Page, eventId: number, roundId: number) {
  await signIn(page, S1_DEMO.coordinator.email, S1_DEMO.coordinator.roleLabel);
  await openCoordinatorPage(page, '/coordinator/events');
  await page.getByText('Timers').click();
  await expect(page.getByText(/Contest/).first()).toBeVisible();
  await page.getByRole('button', { name: /^STOP$/ }).first().click();
  await expect(page.getByText('Stop this timer?')).toBeVisible();
  const stopResponse = page.waitForResponse((response) =>
    response.request().method() === 'POST'
      && new URL(response.url()).pathname === `/api/events/${eventId}/rounds/${roundId}/timer/CONTEST/stop`
      && response.ok()
  );
  await page.getByRole('button', { name: /^STOP TIMER$/ }).click();
  await stopResponse;
  const timer = await apiGet<TimerState>(page, `/api/events/${eventId}/rounds/${roundId}/timer/CONTEST`);
  expect(['STOPPED', 'EXPIRED']).toContain(timer.status);
}

async function startJudgingTimer(page: Page, eventId: number, roundId: number) {
  const timer = await apiPost<TimerState>(page, `/api/events/${eventId}/rounds/${roundId}/timer/JUDGING/start`, {
    durationSeconds: judgingSeconds,
    milestoneMinutes: [30, 15, 5, 1],
    notifyAtHalf: true,
  });
  expect(timer.status).toBe('RUNNING');
  expect(timer.remainingSeconds).toBeGreaterThan(0);
}

async function scoreOneSubmissionViaUi(page: Page, judgeEmail: string, submission: SubmissionRow, criteria: CriteriaRow[]) {
  await signIn(page, judgeEmail, 'Judge');
  await page.goto('/judge/score', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: /Score Submissions/i })).toBeVisible();
  const teamButton = page.getByRole('button', { name: new RegExp(escapeRegExp(submission.teamName)) }).first();
  await expect(teamButton).toBeVisible({ timeout: 30_000 });
  await teamButton.click();

  const inputs = page.locator('input[type="number"]');
  await expect(inputs).toHaveCount(criteria.length);
  for (let i = 0; i < criteria.length; i++) {
    const max = Number(criteria[i].maxScore);
    await inputs.nth(i).fill(String(Math.min(max, 8 + i * 0.2).toFixed(1)));
  }

  const comments = page.locator('textarea[placeholder="Comment..."]');
  for (let i = 0; i < criteria.length; i++) {
    await comments.nth(i).fill(`UI score for ${criteria[i].name}`);
  }

  const scoreResponse = page.waitForResponse((response) =>
    response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/scores'
      && response.ok()
  );
  await page.getByRole('button', { name: /^SUBMIT FINAL$/ }).click();
  await scoreResponse;
  await expect(page.getByText(/SCORES SUBMITTED|Scores submitted as final/i).first()).toBeVisible();
}

async function scoreRemainingViaApi(page: Page, eventId: number, roundId: number, criteria: CriteriaRow[]) {
  await signIn(page, S1_DEMO.coordinator.email, S1_DEMO.coordinator.roleLabel);
  const staff = await apiGet<UserRow[]>(page, '/api/coordinator/staff');
  const emailByUserId = new Map(staff.map((user) => [userIdOf(user), user.email]));
  let progress = await apiGet<ScoringProgressRow[]>(page, `/api/events/${eventId}/rounds/${roundId}/scoring-progress`);

  for (const submission of progress) {
    for (const judge of submission.judges) {
      if (judge.status === 'FINAL') continue;
      const email = emailByUserId.get(judge.judgeUserId);
      expect(email, `Email should exist for judge ${judge.judgeName}`).toBeTruthy();
      await signIn(page, email!, 'Judge');
      await apiPost(page, '/api/scores', scorePayload(submission.submissionId, criteria, submission.teamId + judge.judgeUserId));
    }
  }

  await signIn(page, S1_DEMO.coordinator.email, S1_DEMO.coordinator.roleLabel);
  progress = await apiGet<ScoringProgressRow[]>(page, `/api/events/${eventId}/rounds/${roundId}/scoring-progress`);
  expect(progress.length, 'there should be submissions to score').toBeGreaterThan(0);
  expect(progress.every((item) => item.complete)).toBeTruthy();
}

async function calculateRankingAndExport(page: Page, eventId: number, roundId: number) {
  await signIn(page, S1_DEMO.coordinator.email, S1_DEMO.coordinator.roleLabel);
  await page.goto('/coordinator/scoring', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: /Scoring & Results/i })).toBeVisible();
  await expect(page.getByRole('button', { name: /^CALCULATE RANKINGS$/ })).toBeEnabled();
  await page.getByRole('button', { name: /^CALCULATE RANKINGS$/ }).click();
  await expect(page.getByText('Calculate rankings?')).toBeVisible();
  const finalizeResponse = page.waitForResponse((response) =>
    response.request().method() === 'POST'
      && new URL(response.url()).pathname === `/api/events/${eventId}/rounds/${roundId}/results/finalize`
      && response.ok()
  );
  await page.getByRole('button', { name: /^CALCULATE$/ }).click();
  await finalizeResponse;
  await expect(page.getByText(/Rankings calculated|RANKINGS CALCULATED/i).first()).toBeVisible();

  const results = await apiGet<RoundResultRow[]>(page, `/api/events/${eventId}/rounds/${roundId}/results/all`);
  expect(results.length, 'rankings should be calculated').toBeGreaterThan(0);

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: /^EXPORT CSV$/ }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toContain(`rankings-round-${roundId}`);
}

test.describe.configure({ mode: 'serial' });

test('S1 02 - in-progress phase, mentor support, submission, judging, ranking export', async ({ page }) => {
  await signIn(page, S1_DEMO.coordinator.email, S1_DEMO.coordinator.roleLabel);
  const event = await findS1Event(page);
  expect(['SETUP', 'IN_PROGRESS']).toContain(event.status);
  const eventId = eventIdOf(event);
  const rounds = await getS1Rounds(page, eventId);
  const prelim = prelimRound(rounds);
  const prelimId = roundIdOf(prelim);
  const criteria = await apiGet<CriteriaRow[]>(page, `/api/events/${eventId}/rounds/${prelimId}/criteria`);
  expect(criteria).toHaveLength(S1_DEMO.expectedCriteriaCountPerRound);

  const teamsBeforeStart = await apiGet<TeamRow[]>(page, `/api/teams/event/${eventId}`);
  const arsenal = teamsBeforeStart.find((team) => team.name === S1_DEMO.targetTeamName);
  expect(arsenal?.trackName ?? arsenal?.track_name, 'Arsenal should have a track from setup').toBeTruthy();
  const arsenalTrack = (arsenal!.trackName ?? arsenal!.track_name)!;
  const mentor = S1_DEMO.mentors.find((item) => item.trackName === arsenalTrack);
  expect(mentor, `A stored mentor account should exist for ${arsenalTrack}`).toBeTruthy();
  const submissionTargets = selectSubmissionTargets(teamsBeforeStart);

  await test.step('coordinator starts the event', async () => {
    await startEventIfNeeded(page, eventId);
    await pauseForDemo(page);
  });

  await test.step('coordinator starts contest timer', async () => {
    await startContestTimer(page, eventId, prelimId);
    await pauseForDemo(page);
  });

  await test.step('Arsenal leader requests mentor help', async () => {
    await requestMentorSupport(page);
    await pauseForDemo(page);
  });

  await test.step('assigned mentor resolves support request', async () => {
    await resolveMentorSupport(page, mentor!.email, arsenalTrack);
    await pauseForDemo(page);
  });

  await test.step('Arsenal leader sees support resolved', async () => {
    await verifySupportResolvedForLeader(page);
    await pauseForDemo(page);
  });

  await test.step('students submit project URLs while contest timer is running', async () => {
    await submitArsenalViaUi(page, prelimId);
    await submitOtherTargetsViaApi(page, prelimId, submissionTargets);
    await pauseForDemo(page);
  });

  await test.step('coordinator ends contest timer before judging', async () => {
    await stopContestTimerFromUi(page, eventId, prelimId);
    await pauseForDemo(page);
  });

  await test.step('coordinator starts judging timer', async () => {
    await startJudgingTimer(page, eventId, prelimId);
    await pauseForDemo(page);
  });

  await test.step('assigned judges score submissions', async () => {
    await signIn(page, S1_DEMO.coordinator.email, S1_DEMO.coordinator.roleLabel);
    const submissions = await apiGet<SubmissionRow[]>(page, `/api/submissions/round/${prelimId}`);
    const progress = await apiGet<ScoringProgressRow[]>(page, `/api/events/${eventId}/rounds/${prelimId}/scoring-progress`);
    const roster = await apiGet<JudgeRosterRow[]>(page, `/api/coordinator/assignments/judges?eventId=${eventId}`);
    const firstProgress = progress.find((item) => item.judges.length > 0);
    expect(firstProgress, 'at least one submission should have assigned judges').toBeTruthy();
    const firstSubmission = submissions.find((item) => item.submissionId === firstProgress!.submissionId);
    expect(firstSubmission, 'first scoreable submission should exist').toBeTruthy();
    const firstJudgeId = firstProgress!.judges[0].judgeUserId;
    const firstJudgeRoster = roster.find((item) => item.judgeUserId === firstJudgeId);
    expect(firstJudgeRoster, 'first judge should be in coordinator roster').toBeTruthy();
    const staff = await apiGet<UserRow[]>(page, '/api/coordinator/staff');
    const firstJudgeEmail = staff.find((item) => userIdOf(item) === firstJudgeId)?.email;
    expect(firstJudgeEmail, 'first judge email should be found').toBeTruthy();

    await scoreOneSubmissionViaUi(page, firstJudgeEmail!, firstSubmission!, criteria);
    await scoreRemainingViaApi(page, eventId, prelimId, criteria);
    await pauseForDemo(page);
  });

  await test.step('coordinator calculates ranking and exports CSV', async () => {
    await calculateRankingAndExport(page, eventId, prelimId);
    const after = await findS1Event(page);
    expect(after.status).toBe('IN_PROGRESS');
  });
});
