// FPT students never type in a university at registration — they're inherently
// FPT University, so a null `university` on their record isn't missing data,
// it's implied. Every place that displays a member/user's university should
// show that instead of a bare "—", which reads as broken/missing data.
// Matches both raw userType values ("FPT_STUDENT") and the normalized
// student_type used on the auth profile ("FPT").
// Returns null (not "—") when there's truly nothing to show, so callers that
// already render their own dim "empty" state (e.g. TeamDetailModal's Field)
// keep doing so consistently — only pass a literal "—" fallback yourself if
// your call site doesn't already have one.
export function universityLabel(userType: string | null | undefined, university: string | null | undefined): string | null {
  if (university) return university;
  return (userType ?? '').toUpperCase().includes('FPT') ? 'FPT University' : null;
}
