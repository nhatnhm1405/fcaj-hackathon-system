export const FPT_STUDENT_ID_ERROR =
  "FPT Student ID must start with HE, SE, DE, QE, or CE followed by 6 digits from 000000 to 229999.";

const FPT_STUDENT_ID_PATTERN = /^(HE|SE|DE|QE|CE)([0-9]{6})$/;
const MAX_FPT_STUDENT_NUMBER = 229_999;

export function isValidFptStudentId(studentId: string): boolean {
  const match = FPT_STUDENT_ID_PATTERN.exec(studentId);
  return match !== null && Number(match[2]) <= MAX_FPT_STUDENT_NUMBER;
}
