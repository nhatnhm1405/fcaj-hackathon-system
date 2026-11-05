import { describe, it, expect } from "vitest";
import {
  canCreateEvent,
  canReopenEvent,
  canRequestReopen,
  canCompleteEvent,
  canChangeEventStatus,
  canManageReopenRequests,
  type AppRole,
} from "@/shared/permissions";

const ALL_ROLES: AppRole[] = ['PARTICIPANT', 'MENTOR', 'JUDGE', 'COORDINATOR', 'ADMIN'];

// Helper: assert a predicate is true for exactly `expected` roles and false for
// every other role (plus null / undefined).
function expectAllowedFor(
  fn: (r: AppRole | null | undefined) => boolean,
  expected: AppRole[],
) {
  for (const role of ALL_ROLES) {
    expect(fn(role), `${role}`).toBe(expected.includes(role));
  }
  expect(fn(null)).toBe(false);
  expect(fn(undefined)).toBe(false);
}

describe("event permissions", () => {
  it("only ADMIN can create an event", () => {
    expectAllowedFor(canCreateEvent, ['ADMIN']);
  });

  it("only ADMIN can reopen a completed event", () => {
    expectAllowedFor(canReopenEvent, ['ADMIN']);
  });

  it("only ADMIN can complete a running event", () => {
    expectAllowedFor(canCompleteEvent, ['ADMIN']);
  });

  it("only ADMIN can manage (approve/reject) reopen requests", () => {
    expectAllowedFor(canManageReopenRequests, ['ADMIN']);
  });

  it("only COORDINATOR can request a reopen", () => {
    expectAllowedFor(canRequestReopen, ['COORDINATOR']);
  });

  it("ADMIN and COORDINATOR can change other event statuses", () => {
    expectAllowedFor(canChangeEventStatus, ['ADMIN', 'COORDINATOR']);
  });

  it("a coordinator cannot create or reopen, but can request a reopen", () => {
    expect(canCreateEvent('COORDINATOR')).toBe(false);
    expect(canReopenEvent('COORDINATOR')).toBe(false);
    expect(canCompleteEvent('COORDINATOR')).toBe(false);
    expect(canRequestReopen('COORDINATOR')).toBe(true);
  });
});
