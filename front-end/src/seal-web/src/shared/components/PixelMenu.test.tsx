import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { PixelMenu, type PixelMenuEntry } from "@/shared/components/PixelMenu";

function renderMenu(items?: PixelMenuEntry[], onEdit = vi.fn(), onDelete = vi.fn()) {
  render(
    <PixelMenu
      ariaLabel="Row actions"
      items={items ?? [
        { label: "Edit", onClick: onEdit },
        "divider",
        { label: "Delete", danger: true, onClick: onDelete },
      ]}
    />,
  );
  return { onEdit, onDelete };
}

function trigger() {
  return screen.getByRole("button", { name: "Row actions" });
}

describe("PixelMenu", () => {
  it("is closed initially and opens on trigger click, rendering items and divider", () => {
    renderMenu();
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    fireEvent.click(trigger());
    expect(screen.getByRole("menu")).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Edit" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Delete" })).toBeInTheDocument();
    expect(screen.getByRole("separator")).toBeInTheDocument();
    expect(trigger()).toHaveAttribute("aria-expanded", "true");
  });

  it("fires the item's onClick and closes when an item is clicked", () => {
    const { onEdit, onDelete } = renderMenu();
    fireEvent.click(trigger());
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));
    expect(onEdit).toHaveBeenCalledTimes(1);
    expect(onDelete).not.toHaveBeenCalled();
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("closes on an outside pointerdown without firing any item", () => {
    const { onEdit, onDelete } = renderMenu();
    fireEvent.click(trigger());
    fireEvent.pointerDown(document.body);
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    expect(onEdit).not.toHaveBeenCalled();
    expect(onDelete).not.toHaveBeenCalled();
  });

  it("closes on Escape and returns focus to the trigger", () => {
    renderMenu();
    fireEvent.click(trigger());
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    expect(trigger()).toHaveFocus();
  });

  it("closes when the window scrolls (the fixed panel would detach from its anchor)", () => {
    renderMenu();
    fireEvent.click(trigger());
    fireEvent.scroll(window);
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("does not fire a disabled item's onClick", () => {
    const onClick = vi.fn();
    renderMenu([{ label: "Delete", danger: true, disabled: true, onClick }]);
    fireEvent.click(trigger());
    const item = screen.getByRole("menuitem", { name: "Delete" });
    expect(item).toBeDisabled();
    fireEvent.click(item);
    expect(onClick).not.toHaveBeenCalled();
    expect(screen.getByRole("menu")).toBeInTheDocument();
  });

  it("styles danger items red", () => {
    renderMenu();
    fireEvent.click(trigger());
    expect(screen.getByRole("menuitem", { name: "Delete" })).toHaveStyle({ color: "#f87171" });
    expect(screen.getByRole("menuitem", { name: "Edit" })).not.toHaveStyle({ color: "#f87171" });
  });

  it("opens on ArrowDown and focuses the first enabled item", () => {
    renderMenu([
      { label: "Disabled first", disabled: true, onClick: vi.fn() },
      { label: "Edit", onClick: vi.fn() },
    ]);
    trigger().focus();
    fireEvent.keyDown(trigger(), { key: "ArrowDown" });
    expect(screen.getByRole("menu")).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Edit" })).toHaveFocus();
  });

  it("cycles focus with arrow keys, skipping disabled items", () => {
    renderMenu([
      { label: "Edit", onClick: vi.fn() },
      { label: "Blocked", disabled: true, onClick: vi.fn() },
      { label: "Delete", danger: true, onClick: vi.fn() },
    ]);
    fireEvent.click(trigger());
    const menu = screen.getByRole("menu");
    expect(screen.getByRole("menuitem", { name: "Edit" })).toHaveFocus();
    fireEvent.keyDown(menu, { key: "ArrowDown" });
    expect(screen.getByRole("menuitem", { name: "Delete" })).toHaveFocus();
    fireEvent.keyDown(menu, { key: "ArrowDown" });
    expect(screen.getByRole("menuitem", { name: "Edit" })).toHaveFocus();
    fireEvent.keyDown(menu, { key: "ArrowUp" });
    expect(screen.getByRole("menuitem", { name: "Delete" })).toHaveFocus();
  });
});
