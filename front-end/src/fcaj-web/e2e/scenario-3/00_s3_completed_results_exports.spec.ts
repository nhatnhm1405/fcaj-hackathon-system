import { mkdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { expect, test, type Page } from '@playwright/test';
import { S1_DEMO } from '../scenario-1/s1DemoAccounts';
import {
  apiGet,
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
} from '../scenario-1/s1TestUtils';

const DOWNLOAD_DIR = path.resolve(
  process.env.E2E_DOWNLOAD_DIR ?? 'e2e-downloads/scenario-3',
);

type PrizeRow = {
  prizeId: number;
  name: string;
  rankPosition: number;
  teamId: number;
  teamName: string;
  awardedAt: string;
  announced: boolean;
};

type TeamWithMembers = {
  teamId: number;
  name: string;
  trackName?: string | null;
  members?: {
    fullName: string;
    email: string;
  }[];
};

function finalRound(rounds: RoundRow[]) {
  const round = rounds.find((item) => item.isFinal ?? item.is_final);
  expect(round, 'S3 final round should exist').toBeTruthy();
  return round as RoundRow;
}

async function downloadCsv(
  page: Page,
  buttonName: string,
  expectedFilename: string,
  expectedHeader: string,
) {
  await mkdir(DOWNLOAD_DIR, { recursive: true });

  const button = page.getByRole('button', { name: buttonName, exact: true });
  await expect(button).toBeEnabled();
  const downloadPromise = page.waitForEvent('download');
  await button.click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe(expectedFilename);
  const savedPath = path.join(DOWNLOAD_DIR, expectedFilename);
  await download.saveAs(savedPath);

  const csv = (await readFile(savedPath, 'utf8')).replace(/^\uFEFF/, '');
  expect(csv.split(/\r?\n/, 1)[0]).toBe(expectedHeader);
  return { savedPath, csv };
}

test('S3 verifies completed public results and downloads winner and participant reports', async ({ page }) => {
  test.setTimeout(600_000);

  let preliminary: RoundRow;
  let final: RoundRow;
  let preliminaryResults: RoundResultRow[] = [];
  let finalResults: RoundResultRow[] = [];
  let prizes: PrizeRow[] = [];
  let eventId = 0;
  let participantCount = 0;

  await test.step('Verify the independent S3 completed snapshot', async () => {
    await signIn(page, S1_DEMO.coordinator.email, S1_DEMO.coordinator.roleLabel);

    const event = await findS1Event(page);
    eventId = eventIdOf(event);
    expect(event.status).toBe('COMPLETED');

    const rounds = await getS1Rounds(page, eventId);
    preliminary = prelimRound(rounds);
    final = finalRound(rounds);
    expect(preliminary.status).toBe('FINALIZED');
    expect(final.status).toBe('FINALIZED');

    preliminaryResults = await apiGet<RoundResultRow[]>(
      page,
      `/api/events/${eventId}/rounds/${roundIdOf(preliminary)}/results`,
    );
    finalResults = await apiGet<RoundResultRow[]>(
      page,
      `/api/events/${eventId}/rounds/${roundIdOf(final)}/results`,
    );
    prizes = (await apiGet<PrizeRow[]>(page, `/api/events/${eventId}/prizes`))
      .sort((a, b) => a.rankPosition - b.rankPosition);
    const teams = await apiGet<TeamWithMembers[]>(page, `/api/teams/event/${eventId}`);
    participantCount = teams.reduce((sum, team) => sum + (team.members?.length ?? 0), 0);

    expect(preliminaryResults).toHaveLength(15);
    expect(preliminaryResults.filter((result) => result.advanced)).toHaveLength(8);
    expect(preliminaryResults.every((result) => result.isPublished === true)).toBeTruthy();
    expect(finalResults).toHaveLength(8);
    expect(finalResults.every((result) => result.isPublished === true)).toBeTruthy();
    expect(finalResults.map((result) => result.rankPosition).sort((a, b) => a - b))
      .toEqual([1, 2, 3, 4, 5, 6, 7, 8]);

    expect(prizes).toHaveLength(3);
    expect(prizes.map((prize) => prize.rankPosition)).toEqual([1, 2, 3]);
    expect(prizes.every((prize) => prize.announced && prize.awardedAt != null)).toBeTruthy();
    expect(prizes.map((prize) => prize.teamId))
      .toEqual(finalResults
        .slice()
        .sort((a, b) => a.rankPosition - b.rankPosition)
        .slice(0, 3)
        .map((result) => result.teamId));
    expect(participantCount).toBeGreaterThan(0);
  });

  await test.step('Verify participant-facing final, preliminary, and history views', async () => {
    await signIn(page, S1_DEMO.arsenalLeader.email);
    await gotoAppPage(page, '/leaderboard');

    await expect(page.getByRole('heading', { name: 'Leaderboard' })).toBeVisible();
    await expect(page.getByText('Full Standings', { exact: true })).toBeVisible();
    const champion = finalResults.find((result) => result.rankPosition === 1);
    expect(champion).toBeTruthy();
    await expect(page.getByText(champion?.teamName ?? '', { exact: true }).first()).toBeVisible();

    const roundSelect = page.locator('label')
      .filter({ hasText: /^Round$/ })
      .locator('..')
      .locator('select');
    await roundSelect.selectOption(String(roundIdOf(preliminary)));
    await expect(page.getByText('Full Standings', { exact: true })).toHaveCount(0);

    const trackNames = [...new Set(
      preliminaryResults
        .map((result) => result.trackName)
        .filter((trackName): trackName is string => Boolean(trackName)),
    )];
    expect(trackNames).toHaveLength(4);
    for (const trackName of trackNames) {
      await expect(page.getByText(trackName, { exact: true }).first()).toBeVisible();
    }
    await expect(page.getByText('Advanced', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Eliminated', { exact: true }).first()).toBeVisible();

    await gotoAppPage(page, '/history');
    await expect(page.getByRole('heading', { name: 'History' })).toBeVisible();
    await expect(page.getByText(S1_DEMO.eventName, { exact: true }).first()).toBeVisible();
    await expect(page.getByText(S1_DEMO.targetTeamName, { exact: true }).first()).toBeVisible();
    await pauseForDemo(page);
  });

  await test.step('Verify completed events leave live scoring and move to coordinator history', async () => {
    await signIn(page, S1_DEMO.coordinator.email, S1_DEMO.coordinator.roleLabel);
    await gotoAppPage(page, '/coordinator/scoring');
    await expect(page.getByText(
      'No event is currently open for configuration. Past events have moved to History.',
      { exact: true },
    )).toBeVisible();

    await gotoAppPage(page, '/coordinator/judges/history');
    await expect(page.getByRole('heading', { name: 'History' })).toBeVisible();
    await expect(page.getByText(S1_DEMO.eventName, { exact: true }).first()).toBeVisible();
    await pauseForDemo(page);
  });

  await test.step('Download the winner and participant CSV reports', async () => {
    await gotoAppPage(page, '/coordinator/prizes');
    await expect(page.getByRole('heading', { name: 'Awards' })).toBeVisible();
    await expect(page.getByText('COMPLETED', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('ANNOUNCED', { exact: true }).first()).toBeVisible();

    const eventSlug = S1_DEMO.eventName.replace(/\s+/g, '_');
    const winners = await downloadCsv(
      page,
      'EXPORT WINNERS CSV',
      `winners-${eventSlug}.csv`,
      'rank,prize,team,track,final_score,awarded_at,event,certificate_statement',
    );
    expect(winners.csv.trim().split(/\r?\n/)).toHaveLength(prizes.length + 1);
    for (const prize of prizes) {
      expect(winners.csv).toContain(prize.teamName);
    }

    const participants = await downloadCsv(
      page,
      'EXPORT PARTICIPANTS CSV',
      `participants-${eventSlug}.csv`,
      'full_name,email,team,track,role,event,certificate_statement',
    );
    expect(participants.csv.trim().split(/\r?\n/)).toHaveLength(participantCount + 1);
    expect(participants.csv).toContain(S1_DEMO.targetTeamName);
    await pauseForDemo(page);
  });
});
