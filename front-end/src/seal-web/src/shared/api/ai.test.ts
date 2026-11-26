import { describe, it, expect, vi } from "vitest";
import { ApiError } from "./core";
import {
  AiJobFailedError,
  AiJobTimeoutError,
  aiErrorMessage,
  requestSubmissionInsight,
  type AiInsight,
  type AiJobStatus,
} from "./ai";

const insight: AiInsight = {
  summary: "A web app.",
  strengths: ["Tests"],
  concerns: [],
  criteriaInsights: [],
  repo: null,
  disclaimer: "Advisory only.",
  model: "deepseek-flash",
};

function job(status: string, errorCode: string | null = null): { data: AiJobStatus } {
  return {
    data: {
      jobId: "job-1",
      status,
      createdAt: "2026-11-26T00:00:00Z",
      finishedAt: status === "SUBMITTED" ? null : "2026-11-26T00:00:15Z",
      resultAvailable: status === "SUCCEEDED",
      errorCode,
    },
  };
}

function fakeApi(statuses: Array<{ data: AiJobStatus } | Error>, result: Array<{ data: AiInsight } | Error> = [{ data: insight }]) {
  return {
    createJob: vi.fn().mockResolvedValue(job("SUBMITTED")),
    getJob: vi.fn().mockImplementation(async () => {
      const next = statuses.shift();
      if (!next) throw new Error("no more statuses");
      if (next instanceof Error) throw next;
      return next;
    }),
    getJobResult: vi.fn().mockImplementation(async () => {
      const next = result.shift();
      if (!next) throw new Error("no more results");
      if (next instanceof Error) throw next;
      return next;
    }),
  };
}

const sleep = vi.fn(async () => {});

describe("requestSubmissionInsight", () => {
  it("creates a job, polls until it succeeds, then returns the result", async () => {
    const api = fakeApi([job("SUBMITTED"), job("SUCCEEDED")]);

    await expect(requestSubmissionInsight(7, { api, sleep })).resolves.toEqual(insight);

    expect(api.createJob).toHaveBeenCalledWith(7);
    expect(api.getJob).toHaveBeenCalledTimes(2);
    expect(api.getJob).toHaveBeenCalledWith("job-1");
    expect(api.getJobResult).toHaveBeenCalledWith("job-1");
  });

  it("throws the worker error code when the job fails", async () => {
    const api = fakeApi([job("FAILED", "MOCK_PROCESSING_FAILED")]);

    const err = await requestSubmissionInsight(7, { api, sleep }).catch((e) => e);

    expect(err).toBeInstanceOf(AiJobFailedError);
    expect(err.code).toBe("MOCK_PROCESSING_FAILED");
    expect(api.getJobResult).not.toHaveBeenCalled();
  });

  it("keeps waiting when the result is not ready yet", async () => {
    const api = fakeApi([job("SUCCEEDED"), job("SUCCEEDED")], [new ApiError(409, "RESULT_NOT_READY"), { data: insight }]);

    await expect(requestSubmissionInsight(7, { api, sleep })).resolves.toEqual(insight);
    expect(api.getJobResult).toHaveBeenCalledTimes(2);
  });

  it("tolerates a few transient polling errors", async () => {
    const api = fakeApi([new TypeError("network"), new ApiError(503, "JOB_SERVICE_UNAVAILABLE"), job("SUCCEEDED")]);

    await expect(requestSubmissionInsight(7, { api, sleep })).resolves.toEqual(insight);
  });

  it("stops on a client error while polling", async () => {
    const api = fakeApi([new ApiError(403, "JOB_ACCESS_FORBIDDEN")]);

    await expect(requestSubmissionInsight(7, { api, sleep })).rejects.toMatchObject({ status: 403 });
    expect(api.getJob).toHaveBeenCalledTimes(1);
  });

  it("gives up after too many transient errors in a row", async () => {
    const api = fakeApi([new TypeError("a"), new TypeError("b"), new TypeError("c")]);

    await expect(requestSubmissionInsight(7, { api, sleep, maxTransientErrors: 2 })).rejects.toThrow("c");
  });

  it("times out when the job never finishes", async () => {
    let t = 0;
    const api = fakeApi(Array.from({ length: 10 }, () => job("SUBMITTED")));
    const ticking = vi.fn(async () => {
      t += 1000;
    });

    await expect(
      requestSubmissionInsight(7, { api, sleep: ticking, now: () => t, timeoutMs: 3000 }),
    ).rejects.toBeInstanceOf(AiJobTimeoutError);
    expect(api.getJob.mock.calls.length).toBeLessThanOrEqual(4);
  });

  it("stops polling once aborted", async () => {
    const controller = new AbortController();
    const api = fakeApi([job("SUBMITTED"), job("SUCCEEDED")]);
    const abortingSleep = vi.fn(async () => {
      controller.abort();
    });

    await expect(
      requestSubmissionInsight(7, { api, sleep: abortingSleep, signal: controller.signal }),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(api.getJobResult).not.toHaveBeenCalled();
  });

  it("does not create a job when already aborted", async () => {
    const controller = new AbortController();
    controller.abort();
    const api = fakeApi([]);

    await expect(requestSubmissionInsight(7, { api, sleep, signal: controller.signal })).rejects.toMatchObject({ name: "AbortError" });
    expect(api.createJob).not.toHaveBeenCalled();
  });
});

describe("aiErrorMessage", () => {
  it("explains known backend codes", () => {
    expect(aiErrorMessage(new ApiError(409, "JUDGING_CLOSED"))).toMatch(/Judging is closed/);
  });

  it("includes the worker code for failed jobs", () => {
    expect(aiErrorMessage(new AiJobFailedError("MODEL_TIMEOUT"))).toContain("MODEL_TIMEOUT");
  });

  it("explains a missing AI endpoint", () => {
    expect(aiErrorMessage(new ApiError(404, "HTTP 404"))).toMatch(/not enabled/);
  });

  it("explains a timeout", () => {
    expect(aiErrorMessage(new AiJobTimeoutError())).toMatch(/longer than expected/);
  });
});
