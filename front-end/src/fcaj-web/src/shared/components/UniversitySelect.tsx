import { useState } from "react";
import { C } from "./PixelComponents";
import { HCMC_UNIVERSITIES, UNIVERSITY_OTHER } from "@/shared/constants/universities";

interface UniversitySelectProps {
  label?: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}

/**
 * Dropdown of Ho Chi Minh City universities for external-student registration.
 * Picking "Other (not listed)…" reveals a free-text field so students from a
 * school outside the list can still enter their university name. The parent only
 * ever sees the final university string via onChange.
 */
export function UniversitySelect({
  label = "University",
  value,
  onChange,
  placeholder = "Select your university",
}: UniversitySelectProps) {
  // "Other" mode is active when the user explicitly picks it, or when a
  // pre-filled value doesn't match any listed university (e.g. editing later).
  const valueInList = value !== "" && HCMC_UNIVERSITIES.includes(value);
  const [otherMode, setOtherMode] = useState<boolean>(value !== "" && !valueInList);
  const [focused, setFocused] = useState(false);

  const selectValue = otherMode ? UNIVERSITY_OTHER : valueInList ? value : "";

  function handleSelect(next: string) {
    if (next === UNIVERSITY_OTHER) {
      setOtherMode(true);
      onChange(""); // clear until the user types a custom name
    } else {
      setOtherMode(false);
      onChange(next);
    }
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
      {label && (
        <label style={{ fontFamily: "'JetBrains Mono', monospace", color: C.greenMuted, fontSize: 12, letterSpacing: "0.04em", fontWeight: 500 }}>
          {label}
        </label>
      )}
      <select
        value={selectValue}
        onChange={(e) => handleSelect(e.target.value)}
        style={{
          width: "100%", padding: "10px 12px", background: C.surface2,
          border: `1px solid ${C.border}`, color: value || otherMode ? C.text : C.textMuted,
          fontFamily: "'JetBrains Mono', monospace", fontSize: 13, borderRadius: 0,
          outline: "none", boxSizing: "border-box", cursor: "pointer",
        }}
      >
        <option value="" disabled>{placeholder}</option>
        {HCMC_UNIVERSITIES.map((u) => (
          <option key={u} value={u}>{u}</option>
        ))}
        <option value={UNIVERSITY_OTHER}>Other (not listed)…</option>
      </select>

      {otherMode && (
        <input
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          placeholder="Enter your university name"
          style={{
            width: "100%", padding: "10px 12px", background: C.surface2,
            border: focused ? `1px solid ${C.green}` : `1px solid ${C.border}`,
            boxShadow: focused ? "0 0 12px rgba(34,197,94,0.15)" : "none",
            color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 14,
            borderRadius: 0, outline: "none", boxSizing: "border-box", caretColor: C.green,
            transition: "all 0.15s ease",
          }}
        />
      )}
    </div>
  );
}
