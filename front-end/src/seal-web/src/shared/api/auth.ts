import { apiFetch, type ApiResponse } from './core';

// ── Auth ─────────────────────────────────────────────────────────────

export interface RegisterPayload {
  email: string;
  password: string;
  fullName: string;
  userType: 'FPT_STUDENT' | 'EXTERNAL_STUDENT' | 'STAFF';
  studentId?: string;
  university?: string;
}

export interface LoginPayload {
  email: string;
  password: string;
  rememberMe?: boolean;
}

export interface ForgotPasswordPayload {
  email: string;
}

export interface VerifyResetOtpPayload {
  email: string;
  otp: string;
}

export interface VerifyResetOtpData {
  resetToken: string;
}

export interface ResetPasswordPayload {
  resetToken: string;
  newPassword: string;
}

export interface AuthTokenData {
  userId?: number;
}

export interface UserProfile {
  userId: number;
  email: string;
  fullName: string;
  userType: string;
  studentId?: string | null;
  university?: string | null;
  isApproved: boolean;
  isActive: boolean;
  avatarUrl?: string | null;
  roles?: UserEventRole[];
}

export interface UserEventRole {
  id: number;
  roleName: string;
  eventId?: number;
  trackId?: number;
  roundId?: number;
  judgeType?: string;
}

export const authApi = {
  register: (payload: RegisterPayload) =>
    apiFetch<ApiResponse<{ userId: number }>>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  login: (payload: LoginPayload) =>
    apiFetch<ApiResponse<AuthTokenData>>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  me: () =>
    apiFetch<ApiResponse<UserProfile>>('/api/auth/me'),

  updateMe: (payload: UpdateProfilePayload) =>
    apiFetch<ApiResponse<UserProfile>>('/api/auth/me', {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  // First-time OAuth user picks their account type + student details.
  completeProfile: (payload: CompleteProfilePayload) =>
    apiFetch<ApiResponse<UserProfile>>('/api/auth/complete-profile', {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  logout: () =>
    apiFetch<void>('/api/auth/logout', { method: 'POST' }),

  requestPasswordReset: (payload: ForgotPasswordPayload) =>
    apiFetch<ApiResponse<null>>('/api/auth/forgot-password', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  verifyResetOtp: (payload: VerifyResetOtpPayload) =>
    apiFetch<ApiResponse<VerifyResetOtpData>>('/api/auth/verify-reset-otp', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  resetPassword: (payload: ResetPasswordPayload) =>
    apiFetch<ApiResponse<null>>('/api/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  // Upload a new profile picture (multipart). Returns the updated profile.
  uploadAvatar: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return apiFetch<ApiResponse<UserProfile>>('/api/auth/me/avatar', {
      method: 'POST',
      body: form,
    });
  },

  // Remove the current profile picture. Returns the updated profile.
  deleteAvatar: () =>
    apiFetch<ApiResponse<UserProfile>>('/api/auth/me/avatar', { method: 'DELETE' }),

  // Signed-in user changes their own password by proving the current one.
  changePassword: (payload: ChangePasswordPayload) =>
    apiFetch<ApiResponse<null>>('/api/auth/me/password', {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
};

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export interface UpdateProfilePayload {
  fullName?: string;
  studentId?: string;
  university?: string;
}

// OAuth self-signup is for students only; staff are provisioned by an admin.
export interface CompleteProfilePayload {
  userType: 'FPT_STUDENT' | 'EXTERNAL_STUDENT';
  studentId?: string;
  university?: string;
}

// Sentinel userType for a first-time OAuth account that hasn't completed signup.
export const PENDING_PROFILE = 'PENDING_PROFILE';
