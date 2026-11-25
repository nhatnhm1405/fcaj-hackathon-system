import { fireEvent, render, screen } from "@testing-library/react";
import { Link, MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, expect, it, vi } from "vitest";
import { demoPhasesApi } from "@/shared/apiClient";
import { CoordinatorDemo, useDemoEventId } from "./CoordinatorDemo";

vi.mock("@/shared/apiClient", () => ({
  demoPhasesApi: { getAll: vi.fn(), open: vi.fn() },
}));
vi.mock("@/app/providers/NotificationProvider", () => ({
  useNotifications: () => ({ addToast: vi.fn() }),
}));

beforeEach(() => {
  vi.resetAllMocks();
});

function EventPage({ name }: { name: string }) {
  const eventId = useDemoEventId();
  return <h1>{name} {eventId ?? "regular"}</h1>;
}

it("opens a phase, keeps the demo event across navigation, and exits to regular events", async () => {
  vi.mocked(demoPhasesApi.getAll).mockResolvedValue({ data: ["SETUP", "SCORING"] });
  vi.mocked(demoPhasesApi.open)
    .mockResolvedValueOnce({ data: { eventId: 13 } })
    .mockResolvedValueOnce({ data: { eventId: 14 } });
  render(
    <MemoryRouter initialEntries={["/coordinator/events"]}>
      <CoordinatorDemo>
        <Link to="/coordinator/prizes">Awards</Link>
        <Routes>
          <Route path="/coordinator/events" element={<EventPage name="Events" />} />
          <Route path="/coordinator/prizes" element={<EventPage name="Awards" />} />
          <Route path="/coordinator/scoring" element={<EventPage name="Scoring" />} />
        </Routes>
      </CoordinatorDemo>
    </MemoryRouter>,
  );

  const openMenu = () => fireEvent.click(screen.getByRole("button", { name: "Choose a demo phase" }));
  await screen.findByRole("button", { name: "Choose a demo phase" });
  openMenu();
  fireEvent.click(screen.getByRole("menuitem", { name: "Setup" }));
  expect(await screen.findByRole("heading", { name: "Events 13" })).toBeInTheDocument();

  fireEvent.click(screen.getByRole("link", { name: "Awards" }));
  expect(await screen.findByRole("heading", { name: "Awards 13" })).toBeInTheDocument();
  openMenu();
  fireEvent.click(screen.getByRole("menuitem", { name: "Restart this phase" }));
  expect(await screen.findByRole("heading", { name: "Events 14" })).toBeInTheDocument();
  expect(demoPhasesApi.open).toHaveBeenLastCalledWith("SETUP", true);

  openMenu();
  fireEvent.click(screen.getByRole("menuitem", { name: "Exit demo" }));
  expect(await screen.findByRole("heading", { name: "Events regular" })).toBeInTheDocument();
});
