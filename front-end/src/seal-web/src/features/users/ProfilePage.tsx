import { useState, useEffect, useRef } from "react";
import { useAuth } from "@/app/providers/AuthProvider";
import {
  C, GradientText, PixelCard, PixelButton, PixelBadge, PixelInput, PixelTabs,
} from "@/shared/components/PixelComponents";
import { authApi, teamsApi, ApiError, apiErrorMessage, MyTeam, API_BASE_URL } from "@/shared/apiClient";
import { useNotifications } from "@/app/providers/NotificationProvider";
import { universityLabel } from "@/shared/userDisplay";

const mono = "'JetBrains Mono', monospace";

// OAuth avatars are absolute URLs; uploaded ones are /uploads/... served by the API.
function avatarSrc(url: string | null | undefined): string | null {
  if (!url) return null;
  return url.startsWith("http") ? url : `${API_BASE_URL}${url}`;
}

export function ProfilePage() {
  const { currentUser, patchCurrentUser } = useAuth();
  const { addToast } = useNotifications();
  const [tab, setTab] = useState("overview");
  const [team, setTeam] = useState<MyTeam | null>(null);

  const [fullName, setFullName] = useState(currentUser?.full_name ?? "");
  const [studentId, setStudentId] = useState(currentUser?.student_id ?? "");
  const [university, setUniversity] = useState(currentUser?.university ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  // Avatar upload
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [removing, setRemoving] = useState(false);
  const [avatarError, setAvatarError] = useState<string | null>(null);
  const [avatarSaved, setAvatarSaved] = useState(false);

  // Change password
  const [curPwd, setCurPwd] = useState("");
  const [newPwd, setNewPwd] = useState("");
  const [confirmPwd, setConfirmPwd] = useState("");
  const [pwdMsg, setPwdMsg] = useState<{ type: "error" | "success"; text: string } | null>(null);
  const [pwdSaving, setPwdSaving] = useState(false);

  useEffect(() => {
    if (currentUser?.role === 'PARTICIPANT') {
      teamsApi.getMy().then(res => setTeam(res.data)).catch(() => setTeam(null));
    }
  }, [currentUser?.role]);

  if (!currentUser) return null;
  const isStudent = currentUser.student_type !== null;
  const isExternal = currentUser.student_type === 'EXTERNAL';

  const shownAvatar = preview ?? avatarSrc(currentUser.avatar_url);

  async function save() {
    setError(null); setSaved(false);
    if (!fullName.trim()) {
      setError("Full name is required.");
      addToast({ type: "warning", title: "Missing name", message: "Full name is required." });
      return;
    }
    setSaving(true);
    try {
      const res = await authApi.updateMe({
        fullName: fullName.trim(),
        university: isExternal ? university : undefined,
      });
      const p = res.data;
      patchCurrentUser({
        full_name: p.fullName,
        student_id: p.studentId ?? null,
        university: p.university ?? null,
      });
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
      addToast({ type: "success", title: "Profile saved", message: "Your profile has been updated." });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save profile.");
      addToast({ type: "warning", title: "Save failed", message: apiErrorMessage(err, "Failed to save profile.") });
    } finally {
      setSaving(false);
    }
  }

  function onPickFile(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0];
    e.target.value = ""; // allow re-picking the same file
    if (!f) return;
    if (!f.type.startsWith("image/")) { setAvatarError("Please choose an image file."); addToast({ type: "warning", title: "Invalid file", message: "Please choose an image file." }); return; }
    if (f.size > 5 * 1024 * 1024) { setAvatarError("Image must be 5MB or smaller."); addToast({ type: "warning", title: "Image too large", message: "Image must be 5MB or smaller." }); return; }
    setAvatarError(null);
    setAvatarSaved(false);
    setPendingFile(f);
    setPreview(URL.createObjectURL(f));
  }

  async function uploadAvatar() {
    if (!pendingFile) return;
    setUploading(true); setAvatarError(null);
    try {
      const res = await authApi.uploadAvatar(pendingFile);
      patchCurrentUser({ avatar_url: res.data.avatarUrl ?? null });
      setPendingFile(null);
      setPreview(null);
      setAvatarSaved(true);
      setTimeout(() => setAvatarSaved(false), 2500);
      addToast({ type: "success", title: "Photo updated", message: "Your profile photo has been updated." });
    } catch (err) {
      setAvatarError(err instanceof ApiError ? err.message : "Failed to upload image.");
      addToast({ type: "warning", title: "Upload failed", message: apiErrorMessage(err, "Failed to upload image.") });
    } finally {
      setUploading(false);
    }
  }

  function cancelAvatar() {
    setPendingFile(null);
    setPreview(null);
    setAvatarError(null);
  }

  async function removeAvatar() {
    setRemoving(true); setAvatarError(null);
    try {
      await authApi.deleteAvatar();
      patchCurrentUser({ avatar_url: null });
      setPendingFile(null);
      setPreview(null);
      addToast({ type: "info", title: "Photo removed", message: "Your profile photo has been removed." });
    } catch (err) {
      setAvatarError(err instanceof ApiError ? err.message : "Failed to remove image.");
      addToast({ type: "warning", title: "Remove failed", message: apiErrorMessage(err, "Failed to remove image.") });
    } finally {
      setRemoving(false);
    }
  }

  async function submitPassword() {
    setPwdMsg(null);
    if (!curPwd || !newPwd || !confirmPwd) {
      setPwdMsg({ type: "error", text: "Please fill in all three password fields." });
      return;
    }
    if (newPwd.length < 8) {
      setPwdMsg({ type: "error", text: "New password must be at least 8 characters." });
      return;
    }
    if (newPwd !== confirmPwd) {
      setPwdMsg({ type: "error", text: "New password and confirmation do not match." });
      return;
    }
    if (newPwd === curPwd) {
      setPwdMsg({ type: "error", text: "New password must be different from the current password." });
      return;
    }
    setPwdSaving(true);
    try {
      await authApi.changePassword({ currentPassword: curPwd, newPassword: newPwd });
      setCurPwd(""); setNewPwd(""); setConfirmPwd("");
      setPwdMsg({ type: "success", text: "Password changed successfully." });
      addToast({ type: "success", title: "Password changed", message: "Your password has been updated." });
    } catch (err) {
      const text = apiErrorMessage(err, "Failed to change password.");
      setPwdMsg({ type: "error", text });
      addToast({ type: "warning", title: "Change failed", message: text });
    } finally {
      setPwdSaving(false);
    }
  }

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 20 }}>
      <div>
        <h1 style={{ fontFamily: mono, fontSize: 28, fontWeight: 800 }}>
          <GradientText>Profile</GradientText>
        </h1>
      </div>

      <PixelTabs
        tabs={[{ id: "overview", label: "Overview" }, { id: "settings", label: "Settings" }]}
        active={tab} onChange={setTab}
      />

      {tab === "overview" && (
        <PixelCard glow gradient style={{ padding: 24 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 18, marginBottom: 24, flexWrap: "wrap" }}>
            <Avatar src={shownAvatar} name={currentUser.full_name} size={88} />
            <div>
              <div style={{ color: C.text, fontFamily: mono, fontSize: 20, fontWeight: 800 }}>{currentUser.full_name}</div>
              <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 12, marginTop: 4 }}>{currentUser.email}</div>
            </div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 18 }}>
            <Field label="Full Name" value={currentUser.full_name} />
            <Field label="Email" value={currentUser.email} />
            <Field label="Role" badge={<PixelBadge color="blue">{currentUser.role}</PixelBadge>} />
            {/* Student-only fields — hidden for staff (mentor/judge/coordinator/admin). */}
            {isStudent && (
              <>
                <Field label="Student Type" badge={
                  <PixelBadge color={currentUser.student_type === 'FPT' ? 'green' : 'cyan'}>{currentUser.student_type}</PixelBadge>} />
                <Field label="Student ID" value={currentUser.student_id ?? "—"} />
                <Field label="University" value={universityLabel(currentUser.student_type, currentUser.university) ?? "—"} />
              </>
            )}
            {team && (
              <>
                <Field label="Team" value={team.name} />
                <Field label="Track" value={team.trackName ?? "—"} />
              </>
            )}
          </div>
        </PixelCard>
      )}

      {tab === "settings" && (
        <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
          {/* Profile photo */}
          <PixelCard style={{ padding: 24 }}>
            <SectionHeading>Profile Photo</SectionHeading>
            <div style={{ display: "flex", alignItems: "center", gap: 20, flexWrap: "wrap" }}>
              <Avatar src={shownAvatar} name={currentUser.full_name} size={96} />
              <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                <input ref={fileInputRef} type="file" accept="image/*" onChange={onPickFile} style={{ display: "none" }} />
                <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
                  <PixelButton variant="secondary" onClick={() => fileInputRef.current?.click()}>CHOOSE IMAGE</PixelButton>
                  {pendingFile && (
                    <>
                      <PixelButton variant="cyber" onClick={uploadAvatar} disabled={uploading}>{uploading ? "UPLOADING…" : "UPLOAD"}</PixelButton>
                      <PixelButton variant="ghost" onClick={cancelAvatar} disabled={uploading}>CANCEL</PixelButton>
                    </>
                  )}
                  {!pendingFile && currentUser.avatar_url && (
                    <PixelButton variant="danger" onClick={removeAvatar} disabled={removing}>{removing ? "REMOVING…" : "REMOVE PHOTO"}</PixelButton>
                  )}
                </div>
              </div>
            </div>
            {avatarError && (
              <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: mono, fontSize: 11, padding: "10px 12px", marginTop: 14 }}>ERROR: {avatarError}</div>
            )}
            {avatarSaved && (
              <div style={{ background: "rgba(34,197,94,0.08)", border: "1px solid rgba(34,197,94,0.35)", color: C.green, fontFamily: mono, fontSize: 12, padding: "10px 14px", marginTop: 14 }}>✓ Photo updated.</div>
            )}
          </PixelCard>

          {/* Profile info */}
          <PixelCard style={{ padding: 24 }}>
            <SectionHeading>Profile Info</SectionHeading>
            <div style={{ display: "flex", flexDirection: "column", gap: 16, maxWidth: 480 }}>
              <PixelInput label="Full Name" value={fullName} onChange={(e) => setFullName(e.target.value)} />
              {isStudent && (
                <PixelInput label="Student ID" value={studentId} onChange={(e) => setStudentId(e.target.value)} disabled />
              )}
              {isExternal && (
                <PixelInput label="University" value={university} onChange={(e) => setUniversity(e.target.value)} />
              )}

              {error && (
                <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: mono, fontSize: 11, padding: "10px 12px" }}>ERROR: {error}</div>
              )}
              {saved && (
                <div style={{ background: "rgba(34,197,94,0.08)", border: "1px solid rgba(34,197,94,0.35)", color: C.green, fontFamily: mono, fontSize: 12, padding: "10px 14px" }}>✓ Profile saved.</div>
              )}
              <div>
                <PixelButton variant="cyber" onClick={save} disabled={saving}>{saving ? "SAVING…" : "SAVE PROFILE"}</PixelButton>
              </div>
            </div>
          </PixelCard>

          {/* Change password */}
          <PixelCard style={{ padding: 24 }}>
            <SectionHeading>Change Password</SectionHeading>
            <div style={{ display: "flex", flexDirection: "column", gap: 16, maxWidth: 480 }}>
              <PixelInput label="Current Password" type="password" showToggle value={curPwd} onChange={(e) => setCurPwd(e.target.value)} />
              <PixelInput label="New Password" type="password" showToggle value={newPwd} onChange={(e) => setNewPwd(e.target.value)} />
              <PixelInput label="Confirm New Password" type="password" showToggle value={confirmPwd} onChange={(e) => setConfirmPwd(e.target.value)} />
              {pwdMsg && (
                <div style={{
                  background: pwdMsg.type === "error" ? "rgba(239,68,68,0.08)" : "rgba(34,197,94,0.08)",
                  border: `1px solid ${pwdMsg.type === "error" ? "rgba(239,68,68,0.35)" : "rgba(34,197,94,0.35)"}`,
                  color: pwdMsg.type === "error" ? C.red : C.green,
                  fontFamily: mono, fontSize: 11, padding: "10px 14px",
                }}>{pwdMsg.type === "success" ? "✓ " : ""}{pwdMsg.text}</div>
              )}
              <div>
                <PixelButton variant="cyber" onClick={submitPassword} disabled={pwdSaving}>
                  {pwdSaving ? "UPDATING…" : "UPDATE PASSWORD"}
                </PixelButton>
              </div>
            </div>
          </PixelCard>
        </div>
      )}
    </div>
  );
}

function SectionHeading({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ color: C.green, fontFamily: mono, fontSize: 15, fontWeight: 700, marginBottom: 16 }}>
      {children}
    </div>
  );
}

function Avatar({ src, name, size }: { src: string | null; name: string; size: number }) {
  if (src) {
    return (
      <img
        src={src}
        alt={name}
        style={{ width: size, height: size, objectFit: "cover", border: `1px solid ${C.border}`, background: C.surface2, borderRadius: 0 }}
      />
    );
  }
  const initials = name.trim().split(/\s+/).slice(0, 2).map(w => w[0]?.toUpperCase() ?? "").join("");
  return (
    <div style={{
      width: size, height: size, display: "grid", placeItems: "center",
      background: "rgba(34,197,94,0.12)", border: `1px solid ${C.border}`,
      color: C.green, fontFamily: mono, fontSize: size * 0.36, fontWeight: 800,
    }}>
      {initials || "?"}
    </div>
  );
}

function Field({ label, value, badge }: { label: string; value?: string; badge?: React.ReactNode }) {
  return (
    <div>
      <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 10, letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 6 }}>{label}</div>
      {badge ?? <div style={{ color: C.text, fontFamily: mono, fontSize: 14, fontWeight: 600 }}>{value}</div>}
    </div>
  );
}
