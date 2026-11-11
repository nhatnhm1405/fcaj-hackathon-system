import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { authApi, PENDING_PROFILE } from "@/shared/apiClient";
import { ACTIVE_ROLE_KEY } from "@/app/providers/AuthProvider";
import { useNotifications } from "@/app/providers/NotificationProvider";
import { C, GradientText, FloatingParticles } from "@/shared/components/PixelComponents";

export function OAuth2RedirectPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { addAuthToast } = useNotifications();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const err = searchParams.get("error");

    if (err) {
      setError(decodeURIComponent(err));
      return;
    }

    // The backend already set the JWT as an HttpOnly cookie before redirecting
    // here — just clear stale UI state from any prior session and confirm.
    localStorage.removeItem(ACTIVE_ROLE_KEY);

    // Mirror the email/password login UX: greet the user with a toast. Fetch the
    // profile for their name and to tell a first-time sign-up from a returning user.
    authApi.me()
      .then(res => {
        const u = res.data;
        if (u.userType === PENDING_PROFILE) {
          addAuthToast({ type: 'success', title: 'WELCOME', message: 'Complete your profile to get started.' });
          window.location.replace("/complete-profile");
          return;
        }
        if (!u.isApproved) {
          addAuthToast({ type: 'success', title: 'PROFILE COMPLETE', message: 'Your account awaits coordinator approval.' });
          window.location.replace("/pending-approval");
          return;
        }
        addAuthToast({ type: 'success', title: 'WELCOME BACK', message: `Authenticated as ${u.fullName}` });
        window.location.replace("/dashboard");
      })
      .catch(() => {
        // No valid cookie (direct visit, or the backend failed to set one) —
        // don't assume success like the old token-based flow could.
        addAuthToast({ type: 'warning', title: 'AUTHENTICATION FAILED', message: 'Please sign in again.' });
        window.location.replace("/login");
      });
  }, [searchParams, navigate, addAuthToast]);

  return (
    <div
      style={{
        background: C.bg,
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        position: "relative",
        overflow: "hidden",
      }}
      className="cyber-grid-bg"
    >
      <FloatingParticles count={20} />
      <div style={{ position: "relative", zIndex: 2, textAlign: "center" }}>
        {error ? (
          <div style={{ maxWidth: 420 }}>
            <div style={{
              background: "rgba(239,68,68,0.08)",
              border: "1px solid rgba(239,68,68,0.35)",
              padding: "24px 32px",
              marginBottom: 24,
            }}>
              <p style={{
                color: C.red,
                fontFamily: "'JetBrains Mono', monospace",
                fontSize: 12,
                letterSpacing: "0.06em",
                marginBottom: 8,
              }}>
                AUTHENTICATION FAILED
              </p>
              <p style={{
                color: C.textMuted,
                fontFamily: "'JetBrains Mono', monospace",
                fontSize: 11,
              }}>
                {error}
              </p>
            </div>
            <button
              onClick={() => navigate("/login", { replace: true })}
              style={{
                background: "none",
                border: `1px solid ${C.border}`,
                color: C.text,
                fontFamily: "'JetBrains Mono', monospace",
                fontSize: 12,
                letterSpacing: "0.08em",
                padding: "10px 24px",
                cursor: "pointer",
              }}
            >
              BACK TO LOGIN
            </button>
          </div>
        ) : (
          <div>
            <GradientText>
              <span style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 18, fontWeight: 700 }}>
                AUTHENTICATING...
              </span>
            </GradientText>
            <div style={{ marginTop: 16, display: "flex", gap: 6, justifyContent: "center" }}>
              {[0, 1, 2].map(i => (
                <div
                  key={i}
                  style={{
                    width: 6, height: 6,
                    background: C.green,
                    animation: `pulse 1.2s ease-in-out ${i * 0.2}s infinite`,
                  }}
                />
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
