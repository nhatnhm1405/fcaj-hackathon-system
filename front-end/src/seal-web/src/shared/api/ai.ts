import { ApiError, apiFetch, type ApiResponse } from './core';

// ── AI Judge Assistant ────────────────────────────────────────────
// Advisory, anonymity-safe reading of a submission to help a judge orient
// before scoring. Never writes scores — suggestions are reference-only.

export interface AiCriteriaInsight {
  criteriaName: string;
  assessment: string;
  suggestedScoreRange: string;
}

// Anonymized analysis of the submission's GitHub repository. Facts here are read
// straight from GitHub (not the model), so techStack/signals/redFlags are reliable.
export interface AiRepoAnalysis {
  analyzed: boolean;
  note: string | null;
  techStack: string[];
  signals: string[];
  redFlags: string[];
}

export interface AiInsight {
  summary: string;
  strengths: string[];
  concerns: string[];
  criteriaInsights: AiCriteriaInsight[];
  repo?: AiRepoAnalysis | null;
  disclaimer: string;
  model: string;
}

export type AiJobState = 'SUBMITTED' | 'SUCCEEDED' | 'FAILED';

export interface AiJobStatus {
  jobId: string;
  status: AiJobState | string;
  createdAt: string;
  finishedAt: string | null;
  resultAvailable: boolean;
  errorCode: string | null;
}

// JUDGE / EVENT_COORDINATOR only. The backend grades asynchronously: create a job,
// poll its status, then read the result once it has SUCCEEDED.
export const aiApi = {
  createJob: (submissionId: number) =>
    apiFetch<ApiResponse<AiJobStatus>>(`/api/ai/submissions/${submissionId}/jobs`, { method: 'POST' }),
  getJob: (jobId: string) =>
    apiFetch<ApiResponse<AiJobStatus>>(`/api/ai/jobs/${encodeURIComponent(jobId)}`),
  getJobResult: (jobId: string) =>
    apiFetch<ApiResponse<AiInsight>>(`/api/ai/jobs/${encodeURIComponent(jobId)}/result`),
};

// A job that ended as FAILED. `code` is the backend/worker error code.
export class AiJobFailedError extends Error {
  constructor(public code: string | null) {
    super(code ?? 'AI_JOB_FAILED');
  }
}

// The job did not finish within the waiting window. It may still finish later.
export class AiJobTimeoutError extends Error {
  constructor() {
    super('AI_JOB_TIMEOUT');
  }
}

export interface AiInsightRequestOptions {
  signal?: AbortSignal;
  pollIntervalMs?: number;
  timeoutMs?: number;
  // Consecutive network/5xx failures tolerated while polling.
  maxTransientErrors?: number;
  api?: Pick<typeof aiApi, 'createJob' | 'getJob' | 'getJobResult'>;
  now?: () => number;
  sleep?: (ms: number, signal?: AbortSignal) => Promise<void>;
}

function abortError(): Error {
  const err = new Error('Aborted');
  err.name = 'AbortError';
  return err;
}

function defaultSleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(abortError());
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort);
      resolve();
    }, ms);
    function onAbort() {
      clearTimeout(timer);
      reject(abortError());
    }
    signal?.addEventListener('abort', onAbort, { once: true });
  });
}

function isTransient(err: unknown): boolean {
  return !(err instanceof ApiError) || err.status >= 500;
}

// Creates one AI job for the submission and waits for its result.
export async function requestSubmissionInsight(
  submissionId: number,
  options: AiInsightRequestOptions = {},
): Promise<AiInsight> {
  const {
    signal,
    pollIntervalMs = 3000,
    timeoutMs = 240_000,
    maxTransientErrors = 3,
    api = aiApi,
    now = Date.now,
    sleep = defaultSleep,
  } = options;
  const checkAborted = () => {
    if (signal?.aborted) throw abortError();
  };

  checkAborted();
  const created = await api.createJob(submissionId);
  const jobId = created.data.jobId;
  let status = created.data;
  const deadline = now() + timeoutMs;
  let transientErrors = 0;

  for (;;) {
    checkAborted();
    if (status.status === 'FAILED') throw new AiJobFailedError(status.errorCode);
    if (status.status === 'SUCCEEDED') {
      try {
        return (await api.getJobResult(jobId)).data;
      } catch (err) {
        // The result can trail the status by a moment; keep waiting.
        if (!(err instanceof ApiError && err.status === 409 && err.message === 'RESULT_NOT_READY')) throw err;
      }
    }
    if (now() >= deadline) throw new AiJobTimeoutError();
    await sleep(pollIntervalMs, signal);
    try {
      status = (await api.getJob(jobId)).data;
      transientErrors = 0;
    } catch (err) {
      checkAborted();
      if (!isTransient(err) || ++transientErrors > maxTransientErrors) throw err;
    }
  }
}

const AI_ERROR_MESSAGES: Record<string, string> = {
  JUDGING_CLOSED: 'Judging is closed for this round, so AI assist is unavailable.',
  SUBMISSION_INPUT_CHANGED: 'The submission changed while preparing the AI request. Please try again.',
  REPOSITORY_FETCH_TIMEOUT: 'The repository took too long to read. Please try again later.',
  JOB_CREATE_FORBIDDEN: 'You are not allowed to request AI assist for this submission.',
  JOB_ACCESS_FORBIDDEN: 'You are not allowed to view this AI result.',
  JOB_NOT_FOUND: 'This AI request could not be found.',
  JOB_SERVICE_UNAVAILABLE: 'The AI service is unavailable right now. Please try again later.',
  CONCURRENT_UPDATE: 'Another update happened at the same time. Please try again.',
  AI_JOB_TIMEOUT: 'The AI is taking longer than expected. Please try again in a few minutes.',
};

// User-facing message for any error thrown by requestSubmissionInsight.
export function aiErrorMessage(err: unknown): string {
  if (err instanceof AiJobFailedError) {
    return `The AI could not finish this review${err.code ? ` (${err.code})` : ''}. Please try again later.`;
  }
  if (err instanceof AiJobTimeoutError) return AI_ERROR_MESSAGES.AI_JOB_TIMEOUT;
  if (err instanceof ApiError) {
    if (err.status === 404 && !AI_ERROR_MESSAGES[err.message]) return 'AI assist is not enabled on this server.';
    return AI_ERROR_MESSAGES[err.message] ?? (err.message || 'Failed to generate AI insights.');
  }
  return 'Failed to generate AI insights.';
}
