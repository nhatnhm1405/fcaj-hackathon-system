import { FormEvent, useState } from "react";
import { useNavigate } from "react-router";
import { C, GradientText, PixelButton, PixelInput, FloatingParticles } from "@/shared/components/PixelComponents";
import { SealFooter } from "@/shared/components/SealFooter";
import { apiErrorMessage, authApi } from "@/shared/apiClient";
import { useNotifications } from "@/app/providers/NotificationProvider";
import sealLogo from "@/imports/image.png";

type ResetStep = "email" | "otp" | "password";
const OTP_EXPIRATION_MINUTES = 10;

export function ForgotPasswordPage() {
  const navigate = useNavigate();
  const { addAuthToast } = useNotifications();
  const [step, setStep] = useState<ResetStep>("email");
  const [email, setEmail] = useState("");
  const [submittedEmail, setSubmittedEmail] = useState("");
  const [otp, setOtp] = useState("");
  const [resetToken, setResetToken] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function requestOtp(emailToUse = email.trim()) {
    const trimmedEmail = emailToUse.trim();
    if (!trimmedEmail) {
      setError("Email is required.");
      return;
    }

    setError(null);
    setMessage(null);
    setSubmitting(true);
    try {
      await authApi.requestPasswordReset({ email: trimmedEmail });
      setSubmittedEmail(trimmedEmail);
      setOtp("");
      setResetToken("");
      setStep("otp");
      setMessage("OTP sent to your email.");
      addAuthToast({ type: "success", title: "OTP SENT", message: "Check your email for the reset code." });
    } catch (err) {
      setError(apiErrorMessage(err, "Failed to send OTP."));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRequestOtp(e: FormEvent) {
    e.preventDefault();
    await requestOtp();
  }

  async function handleVerifyOtp(e: FormEvent) {
    e.preventDefault();
    if (otp.length !== 6) {
      setError("OTP must be 6 digits.");
      return;
    }

    setError(null);
    setMessage(null);
    setSubmitting(true);
    try {
      const res = await authApi.verifyResetOtp({ email: submittedEmail, otp });
      setResetToken(res.data.resetToken);
      setStep("password");
      setMessage("OTP verified successfully.");
      addAuthToast({ type: "success", title: "OTP VERIFIED", message: "You can set a new password now." });
    } catch (err) {
      setError(apiErrorMessage(err, "OTP invalid."));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleResetPassword(e: FormEvent) {
    e.preventDefault();
    if (newPassword.length < 8) {
      setError("New password must be at least 8 characters.");
      return;
    }
    if (newPassword !== confirmPassword) {
      setError("New password and confirmation do not match.");
      return;
    }

    setError(null);
    setMessage(null);
    setSubmitting(true);
    try {
      await authApi.resetPassword({ resetToken, newPassword });
      addAuthToast({ type: "success", title: "PASSWORD UPDATED", message: "Please log in with your new password." });
      navigate("/", { replace: true });
    } catch (err) {
      setError(apiErrorMessage(err, "Failed to reset password."));
    } finally {
      setSubmitting(false);
    }
  }

  function changeEmail() {
    setStep("email");
    setSubmittedEmail("");
    setOtp("");
    setResetToken("");
    setNewPassword("");
    setConfirmPassword("");
    setError(null);
    setMessage(null);
  }

  const cardTitle = step === "email"
    ? "Forgot Password"
    : step === "otp"
      ? "Enter OTP"
      : "New Password";

  const cardCopy = step === "email"
    ? "Enter your approved account email to receive an OTP."
    : step === "otp"
      ? `We sent a 6-digit OTP to ${submittedEmail}.`
      : "Create a new password for your account.";

  return (
    <div style={{ background: C.bg, display: "flex", flexDirection: "column", minHeight: "100vh" }}>
      <div style={{ flex: 1, position: "relative", overflow: "hidden" }} className="cyber-grid-bg">
        <FloatingParticles count={20} />
        <div style={{ position: "absolute", top: "15%", left: "10%", width: 360, height: 360, borderRadius: "50%", background: "radial-gradient(circle, rgba(34,197,94,0.07), transparent 70%)", pointerEvents: "none" }} />
        <div style={{ position: "absolute", bottom: "15%", right: "10%", width: 360, height: 360, borderRadius: "50%", background: "radial-gradient(circle, rgba(59,130,246,0.07), transparent 70%)", pointerEvents: "none" }} />

        <div style={{ position: "relative", zIndex: 2, minHeight: "100vh", display: "flex", flexDirection: "column", justifyContent: "center", alignItems: "center", padding: 24 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 40 }}>
            <div style={{ height: 72, overflow: "visible", flexShrink: 0, display: "flex", alignItems: "center" }}>
              <img src={sealLogo} alt="SEAL" style={{ height: 144, width: "auto", objectFit: "contain", filter: "drop-shadow(0 0 8px rgba(34,197,94,0.4))" }} />
            </div>
            <span style={{ fontFamily: "'JetBrains Mono', monospace", fontWeight: 700, fontSize: 13, letterSpacing: "0.08em" }}>
              <GradientText>HACKATHON Management System</GradientText>
            </span>
          </div>

          <div style={{
            width: "100%",
            maxWidth: 440,
            background: C.surface,
            border: `1px solid ${C.border}`,
            padding: 36,
            position: "relative",
            overflow: "hidden",
            boxShadow: "0 0 40px rgba(34,197,94,0.06), 0 8px 32px rgba(0,0,0,0.5)",
          }}>
            <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${C.green}, ${C.blue}, transparent)`, opacity: 0.6 }} />
            <div style={{ position: "absolute", top: 0, left: 0, width: 12, height: 12, borderTop: `2px solid ${C.green}`, borderLeft: `2px solid ${C.green}` }} />
            <div style={{ position: "absolute", bottom: 0, right: 0, width: 12, height: 12, borderBottom: `2px solid rgba(59,130,246,0.5)`, borderRight: `2px solid rgba(59,130,246,0.5)` }} />

            <div style={{ marginBottom: 24 }}>
              <h1 style={{ fontFamily: "'JetBrains Mono', monospace", fontWeight: 900, fontSize: 30, lineHeight: 1.1, marginBottom: 10 }}>
                <GradientText>{cardTitle}</GradientText>
              </h1>
              <p style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, lineHeight: 1.8 }}>
                {cardCopy}
              </p>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 8, marginBottom: 22 }}>
              {["EMAIL", "OTP", "PASSWORD"].map((label, index) => {
                const activeIndex = step === "email" ? 0 : step === "otp" ? 1 : 2;
                const isActive = index === activeIndex;
                const isDone = index < activeIndex;
                return (
                  <div
                    key={label}
                    style={{
                      height: 4,
                      background: isActive || isDone ? C.green : C.surface3,
                      border: `1px solid ${isActive || isDone ? "rgba(34,197,94,0.45)" : C.border}`,
                    }}
                    aria-label={label}
                  />
                );
              })}
            </div>

            {message && (
              <div style={{
                background: "rgba(34,197,94,0.06)",
                border: "1px solid rgba(34,197,94,0.35)",
                color: C.green,
                fontFamily: "'JetBrains Mono', monospace",
                fontSize: 11,
                padding: "10px 12px",
                letterSpacing: "0.04em",
                marginBottom: 16,
              }}>
                {message}
              </div>
            )}

            {error && (
              <div style={{
                background: "rgba(239,68,68,0.08)",
                border: "1px solid rgba(239,68,68,0.35)",
                color: C.red,
                fontFamily: "'JetBrains Mono', monospace",
                fontSize: 11,
                padding: "10px 12px",
                letterSpacing: "0.04em",
                marginBottom: 16,
              }}>
                ERROR: {error}
              </div>
            )}

            {step === "email" && (
              <form onSubmit={handleRequestOtp} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
                <PixelInput
                  label="Email"
                  placeholder="you@seal.edu"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  disabled={submitting}
                />
                <PixelButton type="submit" variant="cyber" size="lg" fullWidth disabled={submitting}>
                  {submitting ? "SENDING OTP..." : "SEND OTP"}
                </PixelButton>
              </form>
            )}

            {step === "otp" && (
              <form onSubmit={handleVerifyOtp} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
                <PixelInput
                  label="OTP"
                  placeholder="123456"
                  type="text"
                  value={otp}
                  onChange={(e) => setOtp(e.target.value.replace(/\D/g, "").slice(0, 6))}
                  disabled={submitting}
                />
                <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, lineHeight: 1.7 }}>
                  OTP expires in {OTP_EXPIRATION_MINUTES} minutes and is limited to 10 attempts.
                </div>
                <PixelButton type="submit" variant="cyber" size="lg" fullWidth disabled={submitting}>
                  {submitting ? "VERIFYING..." : "VERIFY OTP"}
                </PixelButton>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
                  <PixelButton type="button" variant="secondary" fullWidth disabled={submitting} onClick={() => requestOtp(submittedEmail)}>
                    RESEND OTP
                  </PixelButton>
                  <PixelButton type="button" variant="ghost" fullWidth disabled={submitting} onClick={changeEmail}>
                    CHANGE EMAIL
                  </PixelButton>
                </div>
              </form>
            )}

            {step === "password" && (
              <form onSubmit={handleResetPassword} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
                <PixelInput
                  label="New Password"
                  placeholder="minimum 8 characters"
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  showToggle
                  disabled={submitting}
                />
                <PixelInput
                  label="Confirm New Password"
                  placeholder="repeat password"
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  showToggle
                  disabled={submitting}
                />
                <PixelButton type="submit" variant="cyber" size="lg" fullWidth disabled={submitting}>
                  {submitting ? "SAVING..." : "SAVE NEW PASSWORD"}
                </PixelButton>
              </form>
            )}

            <div style={{ marginTop: 24, textAlign: "center" }}>
              <button
                onClick={() => navigate("/login")}
                style={{
                  background: "none", border: "none", cursor: "pointer",
                  color: C.textMuted, fontFamily: "'JetBrains Mono', monospace",
                  fontSize: 11, letterSpacing: "0.08em", padding: 0,
                  transition: "color 0.15s",
                }}
                onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.color = C.green; }}
                onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.color = C.textMuted; }}
              >
                BACK TO LOGIN
              </button>
            </div>
          </div>
        </div>
      </div>
      <SealFooter />
    </div>
  );
}
