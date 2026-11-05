import { useState } from "react";
import { C } from "@/shared/components/PixelComponents";
import { API_BASE_URL } from "@/shared/apiClient";

function GoogleLogo() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844a4.14 4.14 0 01-1.796 2.716v2.259h2.908c1.702-1.567 2.684-3.875 2.684-6.615z" fill="#4285F4"/>
      <path d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 009 18z" fill="#34A853"/>
      <path d="M3.964 10.71A5.41 5.41 0 013.682 9c0-.593.102-1.17.282-1.71V4.958H.957A8.996 8.996 0 000 9c0 1.452.348 2.827.957 4.042l3.007-2.332z" fill="#FBBC05"/>
      <path d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 00.957 4.958L3.964 7.29C4.672 5.163 6.656 3.58 9 3.58z" fill="#EA4335"/>
    </svg>
  );
}

function GitHubLogo() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
      <path fillRule="evenodd" clipRule="evenodd" d="M12 0C5.37 0 0 5.37 0 12c0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61-.546-1.385-1.335-1.755-1.335-1.755-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23A11.509 11.509 0 0112 5.803c1.02.005 2.047.138 3.006.404 2.29-1.552 3.297-1.23 3.297-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 21.795 24 17.298 24 12c0-6.63-5.37-12-12-12z" />
    </svg>
  );
}

interface SocialButtonProps {
  onClick?: () => void;
  children: React.ReactNode;
  logo: React.ReactNode;
}

function SocialButton({ onClick, children, logo }: SocialButtonProps) {
  const [hovered, setHovered] = useState(false);
  return (
    <button
      type="button"
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        width: "100%",
        display: "flex",
        alignItems: "center",
        gap: 12,
        padding: "10px 16px",
        background: hovered ? "rgba(255,255,255,0.04)" : "#111",
        border: hovered ? `1px solid ${C.green}` : "1px solid #333",
        boxShadow: hovered ? `0 0 10px rgba(34,197,94,0.15)` : "none",
        color: hovered ? "#ffffff" : "#cccccc",
        fontFamily: "'JetBrains Mono', monospace",
        fontSize: 13,
        letterSpacing: "0.04em",
        cursor: "pointer",
        borderRadius: 0,
        transition: "border-color 0.15s, box-shadow 0.15s, background 0.15s, color 0.15s",
        textAlign: "left",
      }}
    >
      <span style={{ flexShrink: 0, display: "flex", alignItems: "center" }}>{logo}</span>
      <span style={{ flex: 1, textAlign: "center" }}>{children}</span>
    </button>
  );
}

export function SocialAuthButtons() {
  function handleGoogle() {
    window.location.href = `${API_BASE_URL}/oauth2/authorization/google`;
  }

  function handleGitHub() {
    window.location.href = `${API_BASE_URL}/oauth2/authorization/github`;
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 0 }}>
      {/* Divider */}
      <div style={{ display: "flex", alignItems: "center", gap: 12, margin: "20px 0 16px" }}>
        <div style={{ flex: 1, height: 1, background: "rgba(255,255,255,0.08)" }} />
        <span style={{ color: "rgba(255,255,255,0.25)", fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: "0.1em", whiteSpace: "nowrap" }}>
          — or continue with —
        </span>
        <div style={{ flex: 1, height: 1, background: "rgba(255,255,255,0.08)" }} />
      </div>

      {/* Buttons */}
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        <SocialButton logo={<GoogleLogo />} onClick={handleGoogle}>
          Continue with Google
        </SocialButton>
        <SocialButton logo={<GitHubLogo />} onClick={handleGitHub}>
          Continue with GitHub
        </SocialButton>
      </div>
    </div>
  );
}
