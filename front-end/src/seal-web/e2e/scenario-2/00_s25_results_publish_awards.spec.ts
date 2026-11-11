import { mkdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { expect, test, type Page } from '@playwright/test';
import { S1_DEMO } from '../scenario-1/s1DemoAccounts';
import {
  apiGet,
  escapeRegExp,
  eventIdOf,
  findS1Event,
  getS1Rounds,
  gotoAppPage,
  pauseForDemo,
  prelimRound,
  roundIdOf,
  signIn,
  type RoundResultRow,
  type RoundRow,
  type ScoringProgressRow,
  type SubmissionRow,
} from '../scenario-1/s1TestUtils';

const DOWNLOAD_DIR = path.resolve(
  process.env.E2E_DOWNLOAD_DIR ?? 'e2e-downloads/scenario-2',
);

type PrizeRow = {
  prizeId: number;
  name: string;
  rankPosition: number;
  teamId: number | null;
  teamName: string | null;
  finalScore: number | null;
  awardedAt: string | null;
  announced: boolean;
};

function finalRound(rounds: RoundRow[]) {
  const round = rounds.find((item) => item.isFinal ?? item.is_final);
  expect(round, 'S25 final round should exist').toBeTruthy();
  return round as RoundRow;
}

async function selectScoringRound(page: Page, round: RoundRow) {
  const button = page.getByRole('button', {
    name: new RegExp(`^${escapeRegExp(round.name ?? '')}`, 'i'),
  });
  await expect(button).toBeVisible();
  await button.click();
}

async function downloadCsv(
  page: Page,
  buttonName: string,
  expectedFilename: string,
  expectedHeader: string,
) {
  await mkdir(DOWNLOAD_DIR, { recursive: true });

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: buttonName, exact: true }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe(expectedFilename);
  const savedPath = path.join(DOWNLOAD_DIR, expectedFilename);
  await download.saveAs(savedPath);

  const csv = (await readFile(savedPath, 'utf8')).replace(/^\uFEFF/, '');
  expect(csv.split(/\r?\n/, 1)[0]).toBe(expectedHeader);
  return { savedPath, csv };
}

function expectPreliminaryRanking(results: RoundResultRow[], topN: number) {
  const byTrack = new Map<string, RoundResultRow[]>();
  for (const result of results) {
    const track = result.trackName ?? 'NO_TRACK';
    const rows = byTrack.get(track) ?? [];
    rows.push(result);
    byTrack.set(track, rows);
  }

  expect(byTrack.size).toBe(4);
  for (const rows of byTrack.values()) {
    const ranked = rows.slice().sort((a, b) => a.rankPosition - b.rankPosition);
    expect(ranked.map((row) => row.rankPosition)).toEqual(
      Array.from({ length: ranked.length }, (_, index) => index + 1),
    );
    expect(ranked.filter((row) => row.advanced).map((row) => row.rankPosition))
      .toEqual(Array.from({ length: Math.min(topN, ranked.length) }, (_, index) => index + 1));
  }
}

test('S25 calculates and publishes final results, awards prizes, exports rankings, then completes the event', async ({ page }) => {
  test.setTimeout(900_000);

  await test.step('Verify the S25 hand-off state', async () => {
    await signIn(page, S1_DEMO.coordinator.email, S1_DEMO.coordinator.roleLabel);

    const event = await findS1Event(page);
    const eventId = eventIdOf(event);
    expect(event.status).toBe('IN_PROGRESS');

    const rounds = await getS1Rounds(page, eventId);
    const preliminary = prelimRound(rounds);
    const final = finalRound(rounds);
    const preliminaryId = roundIdOf(preliminary);
    const finalId = roundIdOf(final);

    expect(preliminary.status).toBe('FINALIZED');
    expect(final.status).toBe('ACTIVE');

    const preliminaryResults = await apiGet<RoundResultRow[]>(
      page,
      `/api/events/${eventId}/rounds/${preliminaryId}/results/all`,
    );
    expect(preliminaryResults).toHaveLength(15);
    expect(preliminaryResults.every((result) => result.isPublished === true)).toBeTruthy();
    expectPreliminaryRanking(
      preliminaryResults,
      preliminary.topNAdvance ?? preliminary.top_n_advance ?? 2,
    );
    expect(preliminaryResults.filter((result) => result.advanced)).toHaveLength(8);

    const finalSubmissions = await apiGet<SubmissionRow[]>(
      page,
      `/api/submissions/round/${finalId}`,
    );
    const finalProgress = await apiGet<ScoringProgressRow[]>(
      page,
      `/api/events/${eventId}/rounds/${finalId}/scoring-progress`,
    );
    const finalResults = await apiGet<RoundResultRow[]>(
      page,
      `/api/events/${eventId}/rounds/${finalId}/results/all`,
    );

    expect(finalSubmissions).toHaveLength(8);
    expect(finalProgress).toHaveLength(8);
    expect(finalProgress.every((item) =>
      item.complete
      && item.assignedJudgeCount === 3
      && item.completedJudgeCount === 3
      && item.judges.every((judge) => judge.status === 'FINAL'))).toBeTruthy();
    expect(finalResults).toEqual([]);
  });

  await test.step('Download the published preliminary ranking', async () => {
    const event = await findS1Event(page);
    const eventId = eventIdOf(event);
    const preliminary = prelimRound(await getS1Rounds(page, eventId));

    await gotoAppPage(page, '/coordinator/scoring');
    await expect(page.getByRole('heading', { name: 'Scoring & Results' })).toBeVisible();
    await selectScoringRound(page, preliminary);
    await expect(page.getByText(`${preliminary.name} — Rankings`, { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'EXPORT CSV', exact: true })).toBeEnabled();

    const exported = await downloadCsv(
      page,
      'EXPORT CSV',
      `rankings-round-${roundIdOf(preliminary)}.csv`,
      'rank_position,team_name,total_score,advanced',
    );
    expect(exported.csv.trim().split(/\r?\n/)).toHaveLength(16);
    await pauseForDemo(page);
  });

  let publishedFinalResults: RoundResultRow[] = [];

  await test.step('Calculate a draft global ranking for the final round', async () => {
    const event = await findS1Event(page);
    const eventId = eventIdOf(event);
    const final = finalRound(await getS1Rounds(page, eventId));
    const finalId = roundIdOf(final);

    await selectScoringRound(page, final);
    const calculateButton = page.getByRole('button', { name: 'CALCULATE RANKINGS', exact: true });
    await expect(calculateButton).toBeEnabled();
    await calculateButton.click();

    await expect(page.getByRole('heading', { name: 'Calculate rankings?' })).toBeVisible();
    const calculateResponse = page.waitForResponse((response) =>
      response.url().includes(`/api/events/${eventId}/rounds/${finalId}/results/finalize`)
      && response.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'CALCULATE', exact: true }).click();
    expect((await calculateResponse).ok()).toBeTruthy();

    await expect(page.getByText('Rankings calculated.', { exact: true })).toBeVisible();
    const draftResults = await apiGet<RoundResultRow[]>(
      page,
      `/api/events/${eventId}/rounds/${finalId}/results/all`,
    );

    expect(draftResults).toHaveLength(8);
    expect(draftResults.map((result) => result.rankPosition).sort((a, b) => a - b))
      .toEqual([1, 2, 3, 4, 5, 6, 7, 8]);
    expect(draftResults.every((result) =>
      result.totalScore >= 0
      && result.totalScore <= 100
      && result.isPublished === false)).toBeTruthy();

    const participantVisibleResults = await apiGet<RoundResultRow[]>(
      page,
      `/api/events/${eventId}/rounds/${finalId}/results`,
    );
    expect(participantVisibleResults).toEqual([]);
    await pauseForDemo(page);
  });

  await test.step('Publish the final ranking and download it', async () => {
    const event = await findS1Event(page);
    const eventId = eventIdOf(event);
    const final = finalRound(await getS1Rounds(page, eventId));
    const finalId = roundIdOf(final);

    const publishButton = page.getByRole('button', { name: 'PUBLISH RESULTS', exact: true });
    await expect(publishButton).toBeEnabled();
    await publishButton.click();

    await expect(page.getByRole('heading', { name: 'Publish these results?' })).toBeVisible();
    await page.getByLabel('Confirmation text').fill(final.name ?? '');
    const publishResponse = page.waitForResponse((response) =>
      response.url().includes(`/api/events/${eventId}/rounds/${finalId}/results/publish`)
      && response.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'PUBLISH RESULTS', exact: true }).last().click();
    expect((await publishResponse).ok()).toBeTruthy();

    await expect(page.getByText('Results published.', { exact: true })).toBeVisible();
    publishedFinalResults = await apiGet<RoundResultRow[]>(
      page,
      `/api/events/${eventId}/rounds/${finalId}/results`,
    );
    expect(publishedFinalResults).toHaveLength(8);
    expect(publishedFinalResults.every((result) => result.isPublished === true)).toBeTruthy();

    const exported = await downloadCsv(
      page,
      'EXPORT CSV',
      `rankings-round-${finalId}.csv`,
      'rank_position,team_name,total_score,advanced',
    );
    expect(exported.csv.trim().split(/\r?\n/)).toHaveLength(9);
    await pauseForDemo(page);
  });

  await test.step('Verify that a participant can see the published final leaderboard', async () => {
    await signIn(page, S1_DEMO.arsenalLeader.email);
    await gotoAppPage(page, '/leaderboard');

    await expect(page.getByRole('heading', { name: 'Leaderboard' })).toBeVisible();
    await expect(page.getByText('Full Standings', { exact: true })).toBeVisible();
    const champion = publishedFinalResults.find((result) => result.rankPosition === 1);
    expect(champion).toBeTruthy();
    await expect(page.getByText(champion?.teamName ?? '', { exact: true }).first()).toBeVisible();
    await pauseForDemo(page);
  });

  await test.step('Auto-generate the top three prizes and announce them', async () => {
    await signIn(page, S1_DEMO.coordinator.email, S1_DEMO.coordinator.roleLabel);
    const event = await findS1Event(page);
    const eventId = eventIdOf(event);

    await gotoAppPage(page, '/coordinator/prizes');
    await expect(page.getByRole('heading', { name: 'Awards' })).toBeVisible();
    const generateButton = page.getByRole('button', {
      name: 'AUTO-GENERATE FROM FINAL',
      exact: true,
    });
    await expect(generateButton).toBeEnabled();

    const generateResponse = page.waitForResponse((response) =>
      response.url().includes(`/api/events/${eventId}/prizes/auto-generate`)
      && response.request().method() === 'POST',
    );
    await generateButton.click();
    expect((await generateResponse).ok()).toBeTruthy();

    const draftPrizes = (await apiGet<PrizeRow[]>(page, `/api/events/${eventId}/prizes`))
      .sort((a, b) => a.rankPosition - b.rankPosition);
    const expectedWinners = publishedFinalResults
      .slice()
      .sort((a, b) => a.rankPosition - b.rankPosition)
      .slice(0, 3);

    expect(draftPrizes).toHaveLength(3);
    expect(draftPrizes.map((prize) => prize.rankPosition)).toEqual([1, 2, 3]);
    expect(draftPrizes.map((prize) => prize.teamId))
      .toEqual(expectedWinners.map((result) => result.teamId));
    expect(draftPrizes.every((prize) => !prize.announced && prize.awardedAt == null)).toBeTruthy();

    const announceButton = page.getByRole('button', { name: 'ANNOUNCE', exact: true });
    await expect(announceButton).toBeEnabled();
    await announceButton.click();
    await expect(page.getByRole('heading', { name: 'Announce these prizes?' })).toBeVisible();
    await page.getByLabel('Confirmation text').fill(event.name ?? '');

    const announceResponse = page.waitForResponse((response) =>
      response.url().includes(`/api/events/${eventId}/prizes/announce`)
      && response.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'ANNOUNCE PRIZES', exact: true }).click();
    expect((await announceResponse).ok()).toBeTruthy();

    const announcedPrizes = (await apiGet<PrizeRow[]>(page, `/api/events/${eventId}/prizes`))
      .sort((a, b) => a.rankPosition - b.rankPosition);
    expect(announcedPrizes).toHaveLength(3);
    expect(announcedPrizes.every((prize) => prize.announced && prize.awardedAt != null)).toBeTruthy();
    expect(announcedPrizes.map((prize) => prize.teamId))
      .toEqual(expectedWinners.map((result) => result.teamId));
    await pauseForDemo(page);
  });

  await test.step('Complete the event as an administrator', async () => {
    await signIn(page, 'admin@fpt.edu.vn', 'Admin');
    const event = await findS1Event(page);
    const eventId = eventIdOf(event);
    expect(event.status).toBe('IN_PROGRESS');

    await gotoAppPage(page, '/admin/events');
    await expect(page.getByRole('heading', { name: 'Events' })).toBeVisible();
    const completeButton = page.getByRole('button', { name: 'COMPLETE EVENT', exact: true });
    await expect(completeButton).toBeEnabled();
    await completeButton.click();

    await expect(page.getByRole('heading', { name: 'Complete this event?' })).toBeVisible();
    const completeResponse = page.waitForResponse((response) =>
      response.url().includes(`/api/events/${eventId}/complete`)
      && response.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'CONFIRM COMPLETE', exact: true }).click();
    expect((await completeResponse).ok()).toBeTruthy();

    const completed = await findS1Event(page);
    expect(completed.status).toBe('COMPLETED');
    await expect(page.getByRole('button', { name: 'COMPLETE EVENT', exact: true })).toHaveCount(0);
    await pauseForDemo(page);
  });
});
