import { useState } from "react";
import { useNavigate } from "react-router";
import {
  C, GradientText, PixelButton, PixelInput, PixelCard, FloatingParticles,
} from "@/shared/components/PixelComponents";
import { SealFooter } from "@/shared/components/SealFooter";
import { SocialAuthButtons } from "@/features/auth/SocialAuthButtons";
import { UniversitySelect } from "@/shared/components/UniversitySelect";
import sealLogo from "@/imports/image.png";
import { apiFetch, ApiError, apiErrorMessage } from "@/shared/apiClient";
import { useNotifications } from "@/app/providers/NotificationProvider";
import {
  FPT_STUDENT_ID_ERROR,
  isValidFptStudentId,
} from "@/shared/validation/fptStudentId";

type StudentType = 'FPT' | 'EXTERNAL';

export function RegisterPage() {
  const navigate = useNavigate();
  const { addAuthToast } = useNotifications();
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [studentType, setStudentType] = useState<StudentType>('FPT');
  const [studentId, setStudentId] = useState("");
  const [studentIdError, setStudentIdError] = useState<string | null>(null);
  const [university, setUniversity] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function checkStudentIdExists(id: string) {
    if (!id.trim()) return;
    try {
      const res = await apiFetch<{ data: boolean }>(`/api/auth/check-student-id?id=${encodeURIComponent(id.trim())}`);
      if (res.data) {
        addAuthToast({ type: 'warning', title: 'STUDENT ID TAKEN', message: `Student ID "${id.trim()}" is already registered. If this is you, use the login page.` });
      }
    } catch {
      // silently ignore — backend will enforce on submit
    }
  }

  async function handleStudentIdBlur(id: string) {
    const normalizedStudentId = id.trim();
    if (!normalizedStudentId) return;

    if (studentType === 'FPT' && !isValidFptStudentId(normalizedStudentId)) {
      setStudentIdError(FPT_STUDENT_ID_ERROR);
      return;
    }

    setStudentIdError(null);
    await checkStudentIdExists(normalizedStudentId);
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!fullName || !email || !password) {
      setError("Please fill in all required fields");
      addAuthToast({ type: 'warning', title: 'MISSING FIELDS', message: 'Please fill in all required fields.' });
      return;
    }
    if (password !== confirmPassword) {
      setError("Passwords do not match");
      addAuthToast({ type: 'warning', title: 'PASSWORD MISMATCH', message: 'Passwords do not match.' });
      return;
    }
    const normalizedStudentId = studentId.trim();
    if (!normalizedStudentId) {
      setError("Student ID is required.");
      addAuthToast({ type: 'warning', title: 'MISSING STUDENT ID', message: 'Student ID is required.' });
      return;
    }
    if (studentType === 'FPT' && !isValidFptStudentId(normalizedStudentId)) {
      setStudentIdError(FPT_STUDENT_ID_ERROR);
      setError(FPT_STUDENT_ID_ERROR);
      addAuthToast({ type: 'warning', title: 'INVALID FPT STUDENT ID', message: FPT_STUDENT_ID_ERROR });
      return;
    }
    setStudentIdError(null);
    setSubmitting(true);
    try {
      await apiFetch('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify({
          fullName,
          email,
          password,
          userType: studentType === 'FPT' ? 'FPT_STUDENT' : 'EXTERNAL_STUDENT',
          studentId: normalizedStudentId,
          university: studentType === 'EXTERNAL' ? (university.trim() || null) : null,
        }),
      });
      addAuthToast({ type: 'success', title: 'REGISTRATION SUBMITTED', message: 'Your account is pending coordinator approval.' });
      navigate('/pending-approval');
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          setError("An account with this email already exists.");
        } else {
          setError(err.message || "Registration failed. Please try again.");
        }
      } else {
        setError("Network error. Please check your connection and try again.");
      }
      addAuthToast({
        type: 'warning',
        title: 'REGISTRATION FAILED',
        message: err instanceof ApiError && err.status === 409
          ? 'An account with this email already exists.'
          : apiErrorMessage(err, 'Registration failed. Please try again.'),
      });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={{ background: C.bg, display: "flex", flexDirection: "column", minHeight: "100vh" }}>
    <div style={{ flex: 1, position: "relative", overflow: "hidden", padding: "40px 24px" }} className="cyber-grid-bg">
      <FloatingParticles count={26} />
      <div style={{ position: "absolute", top: "10%", right: "5%", width: 400, height: 400, borderRadius: "50%", background: "radial-gradient(circle, rgba(59,130,246,0.06), transparent 70%)", pointerEvents: "none" }} />

      <div style={{ position: "relative", zIndex: 2, maxWidth: 560, margin: "0 auto" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 24, justifyContent: "center" }}>
          <div style={{ height: 100, overflow: "visible", flexShrink: 0, display: "flex", alignItems: "center" }}>
            <img src={sealLogo} alt="HMS" style={{ height: 200, width: "auto", objectFit: "contain" }} />
          </div>
        </div>

        <div style={{ textAlign: "center", marginBottom: 28 }}>
          <h1 style={{ fontFamily: "'JetBrains Mono', monospace", fontWeight: 900, fontSize: 36, lineHeight: 1.1 }}>
            <GradientText>Create Account</GradientText>
          </h1>
          <p style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, marginTop: 10 }}>
            Submit your registration for coordinator approval.
          </p>
        </div>

        <PixelCard glow gradient style={{ padding: 28 }}>
          <form onSubmit={handleSubmit} autoComplete="off" style={{ display: "flex", flexDirection: "column", gap: 18 }}>
            <PixelInput label="Full Name" placeholder="Your full name" value={fullName} onChange={(e) => setFullName(e.target.value)} autoComplete="off" />
            <PixelInput label="Email" type="email" placeholder="you@seal.edu" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="off" />

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
              <PixelInput label="Password" type="password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} showToggle autoComplete="new-password" />
              <PixelInput label="Confirm Password" type="password" placeholder="••••••••" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} showToggle autoComplete="new-password" />
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              <label style={{ color: C.greenMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase" }}>
                Student Type
              </label>
              <div style={{ display: "flex", gap: 10 }}>
                {(['FPT', 'EXTERNAL'] as StudentType[]).map((t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => {
                      setStudentType(t);
                      setStudentIdError(null);
                    }}
                    style={{
                      flex: 1,
                      padding: "10px 14px",
                      background: studentType === t ? "rgba(34,197,94,0.12)" : C.surface2,
                      border: studentType === t ? `1px solid ${C.green}` : `1px solid ${C.border}`,
                      color: studentType === t ? C.green : C.textMuted,
                      fontFamily: "'JetBrains Mono', monospace",
                      fontSize: 12,
                      cursor: "pointer",
                      borderRadius: 0,
                      letterSpacing: "0.06em",
                      textTransform: "uppercase",
                      transition: "all 0.15s ease",
                    }}
                  >
                    {t === 'FPT' ? 'FPT Student' : 'External Student'}
                  </button>
                ))}
              </div>
            </div>

            {studentType === 'FPT' ? (
              <div>
                <PixelInput
                  label="FPT Student ID"
                  placeholder="SE000000"
                  value={studentId}
                  onChange={(e) => {
                    setStudentId(e.target.value);
                    setStudentIdError(null);
                  }}
                  onBlur={(e) => void handleStudentIdBlur(e.target.value)}
                  autoComplete="off"
                />
                <div style={{
                  color: studentIdError ? C.red : C.textMuted,
                  fontFamily: "'JetBrains Mono', monospace",
                  fontSize: 10,
                  lineHeight: 1.5,
                  marginTop: 6,
                }}>
                  {studentIdError ?? "Format: HE/SE/DE/QE/CE + 6 digits (000000–229999)."}
                </div>
              </div>
            ) : (
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16, alignItems: "start" }}>
                <PixelInput label="Student ID" placeholder="Your student ID" value={studentId} onChange={(e) => setStudentId(e.target.value)} onBlur={(e) => void handleStudentIdBlur(e.target.value)} autoComplete="off" />
                <UniversitySelect label="University" value={university} onChange={setUniversity} />
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
              }}>
                ERROR: {error}
              </div>
            )}

            <PixelButton type="submit" variant="cyber" size="lg" fullWidth disabled={submitting}>
              {submitting ? "SUBMITTING..." : "SUBMIT REGISTRATION"}
            </PixelButton>

            <SocialAuthButtons />
          </form>
        </PixelCard>

        <div style={{ marginTop: 24, textAlign: "center", display: "flex", flexDirection: "column", gap: 12 }}>
          <span style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11 }}>
            ALREADY HAVE AN ACCOUNT?{" "}
            <a onClick={() => navigate('/login')} style={{ color: C.green, cursor: "pointer", letterSpacing: "0.06em" }}>
              LOGIN
            </a>
          </span>
          <a
            onClick={() => navigate('/')}
            style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, cursor: "pointer", letterSpacing: "0.1em", transition: "color 0.15s" }}
            onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.color = C.green; }}
            onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.color = C.textMuted; }}
          >
            ← BACK TO HOME
          </a>
        </div>
      </div>
    </div>
    <SealFooter />
    </div>
  );
}
