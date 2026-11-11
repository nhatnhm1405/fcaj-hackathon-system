// Barrel re-export — every API call in the app used to live in this one file.
// It's now split by domain under ./api/*, one module per backend domain
// (mirrors the same split done on the backend's service layer). This file
// stays so all existing `from "@/shared/apiClient"` imports keep working
// unchanged; add new domains here as they're extracted.

export * from './api/core';
export * from './api/auth';
export * from './api/admin';
export * from './api/participationRequests';
export * from './api/teamRejoinRequests';
export * from './api/events';
export * from './api/tracks';
export * from './api/rounds';
export * from './api/teams';
export * from './api/submissions';
export * from './api/scoring';
export * from './api/ai';
export * from './api/results';
export * from './api/prizes';
export * from './api/notifications';
export * from './api/assignments';
export * from './api/support';
export * from './api/coordinator';
