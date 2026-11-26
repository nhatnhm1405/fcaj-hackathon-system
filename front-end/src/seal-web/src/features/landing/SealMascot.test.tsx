import { describe, it, expect } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { SealMascot } from "./SealMascot";

function renderSeal() {
  render(<SealMascot />);
  return screen.getByRole("button", { name: /seal mascot/i });
}

// jsdom has no PointerEvent constructor, so dress up a MouseEvent.
function pointer(type: string, clientX: number, clientY: number) {
  return Object.assign(new MouseEvent(type, { bubbles: true, button: 0, clientX, clientY }), { pointerId: 1 });
}

describe("SealMascot", () => {
  it("speaks when petted", () => {
    const seal = renderSeal();
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
    fireEvent.click(seal);
    expect(screen.getByRole("status")).not.toBeEmptyDOMElement();
  });

  it("celebrates after five quick pets", () => {
    const seal = renderSeal();
    for (let i = 0; i < 5; i++) fireEvent.click(seal);
    expect(screen.getByRole("status")).toHaveTextContent("SEAL OF APPROVAL!");
  });

  it("does not count a drag as a pet", () => {
    const seal = renderSeal();
    fireEvent(seal, pointer("pointerdown", 10, 10));
    fireEvent(window, pointer("pointermove", 80, 80));
    const heldLine = screen.getByRole("status").textContent ?? "";
    expect(heldLine).not.toBe("");
    fireEvent(window, pointer("pointerup", 80, 80));
    fireEvent.click(seal, { detail: 1 });
    expect(screen.getByRole("status")).toHaveTextContent(heldLine);
  });
});
