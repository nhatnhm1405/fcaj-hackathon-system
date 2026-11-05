import { describe, expect, it } from "vitest";
import { isValidFptStudentId } from "./fptStudentId";

describe("isValidFptStudentId", () => {
  it.each([
    "HE000000",
    "SE000001",
    "DE100000",
    "QE220000",
    "CE229999",
  ])("accepts %s", (studentId) => {
    expect(isValidFptStudentId(studentId)).toBe(true);
  });

  it.each([
    "SE230000",
    "SE999999",
    "se123456",
    "AE123456",
    "SE12345",
    "SE1234567",
    "SE12A456",
    " SE123456",
    "SE123456 ",
  ])("rejects %s", (studentId) => {
    expect(isValidFptStudentId(studentId)).toBe(false);
  });
});
