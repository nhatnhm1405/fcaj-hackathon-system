import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useEffect } from "react";
import { Link, MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, expect, it, vi } from "vitest";
import { demoPhasesApi, demoWavesApi } from "@/shared/apiClient";
import { CoordinatorDemo, useDemoEventId } from "./CoordinatorDemo";

vi.mock("@/shared/apiClient", () => ({
  demoPhasesApi: { getAll: vi.fn(), open: vi.fn() },
  demoWavesApi: { getAll: vi.fn(), run: vi.fn() },
  apiErrorMessage: (_error: unknown, fallback: string) => fallback,
}));
const addToast = vi.fn();
vi.mock("@/app/providers/NotificationProvider", () => ({
  useNotifications: () => ({ addToast }),
}));

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(demoPhasesApi.getAll).mockRejectedValue(new Error("off"));
  vi.mocked(demoWavesApi.getAll).mockRejectedValue(new Error("off"));
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

it("shows no demo control when both demo features are off", async () => {
  render(<MemoryRouter><CoordinatorDemo><p>Page</p></CoordinatorDemo></MemoryRouter>);
  await screen.findByText("Page");
  await Promise.resolve();
  expect(screen.queryByRole("button", { name: "Add demo data" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Choose a demo phase" })).not.toBeInTheDocument();
});

it("runs a ready wave, reloads the page underneath, and explains a wave that is not ready", async () => {
  const before = [{
    eventId: 3, eventName: "SEAL AWS", eventStatus: "OPEN",
    waves: [
      { wave: "REGISTRATION" as const, done: false, blockedBy: null },
      { wave: "SUBMISSIONS" as const, done: false, blockedBy: "Run the registration wave first." },
      { wave: "SCORES" as const, done: false, blockedBy: "Run the submissions wave first." },
    ],
  }];
  const after = [{ ...before[0], waves: [{ ...before[0].waves[0], done: true }, ...before[0].waves.slice(1)] }];
  vi.mocked(demoWavesApi.getAll).mockResolvedValueOnce({ data: before }).mockResolvedValueOnce({ data: after });
  vi.mocked(demoWavesApi.run).mockResolvedValue({ data: { eventId: 3, wave: "REGISTRATION", added: { teams: 12 } } });
  let mounts = 0;
  function Page() {
    useEffect(() => { mounts += 1; }, []);
    return <p>Page</p>;
  }
  render(<MemoryRouter><CoordinatorDemo><Page /></CoordinatorDemo></MemoryRouter>);

  fireEvent.click(await screen.findByRole("button", { name: "Add demo data" }));
  fireEvent.click(screen.getByRole("menuitem", { name: /Submissions from demo teams \(not ready\)/ }));
  expect(addToast).toHaveBeenLastCalledWith(expect.objectContaining({ message: "Run the registration wave first." }));
  expect(demoWavesApi.run).not.toHaveBeenCalled();

  fireEvent.click(screen.getByRole("button", { name: "Add demo data" }));
  fireEvent.click(screen.getByRole("menuitem", { name: "SEAL AWS · 1. Registration: teams, judges, mentors" }));
  expect(demoWavesApi.run).toHaveBeenCalledWith(3, "REGISTRATION");
  await waitFor(() => expect(addToast).toHaveBeenLastCalledWith(expect.objectContaining({ message: "teams 12" })));
  await waitFor(() => expect(mounts).toBe(2));
  fireEvent.click(await screen.findByRole("button", { name: "Add demo data" }));
  expect(await screen.findByRole("menuitem", { name: /Registration.*✓/ })).toBeDisabled();
});
