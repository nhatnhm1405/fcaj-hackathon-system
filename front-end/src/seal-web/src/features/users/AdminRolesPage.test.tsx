import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { adminApi, eventsApi } from "@/shared/apiClient";
import { AdminRolesPage } from "./AdminRolesPage";

vi.mock("@/shared/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/shared/apiClient")>()),
  adminApi: { getRoleGrants: vi.fn(), getUsers: vi.fn(), grantRole: vi.fn(), revokeRole: vi.fn() },
  eventsApi: { getAll: vi.fn() },
}));
vi.mock("@/app/providers/NotificationProvider", () => ({
  useNotifications: () => ({ addToast: vi.fn() }),
}));

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(adminApi.getRoleGrants).mockResolvedValue({ data: [] } as never);
  vi.mocked(adminApi.getUsers).mockResolvedValue({
    data: [{ userId: 2, fullName: "Event Coordinator", email: "coordinator@example.test" }],
  } as never);
  vi.mocked(eventsApi.getAll).mockResolvedValue({
    data: [{ eventId: 3, name: "SEAL AWS", startDate: "2027-04-10T08:00:00", endDate: "2027-04-11T23:59:59" }],
  } as never);
  vi.mocked(adminApi.grantRole).mockResolvedValue({ data: {} } as never);
});

async function form() {
  await screen.findByRole("option", { name: "SEAL AWS" });
  const [user, role] = screen.getAllByRole("combobox");
  fireEvent.change(user, { target: { value: "2" } });
  return { role, scope: screen.getByLabelText("Event scope") };
}

it("grants a coordinator for one event", async () => {
  render(<AdminRolesPage />);
  const { scope } = await form();
  fireEvent.change(scope, { target: { value: "3" } });
  fireEvent.click(screen.getByRole("button", { name: /grant role/i }));
  await waitFor(() => expect(adminApi.grantRole).toHaveBeenCalledWith(
    { userId: 2, roleName: "EVENT_COORDINATOR", eventId: 3 }));
});

it("still grants a system-wide coordinator", async () => {
  render(<AdminRolesPage />);
  const { scope } = await form();
  fireEvent.change(scope, { target: { value: "" } });
  fireEvent.click(screen.getByRole("button", { name: /grant role/i }));
  await waitFor(() => expect(adminApi.grantRole).toHaveBeenCalledWith(
    { userId: 2, roleName: "EVENT_COORDINATOR", eventId: null }));
});

it("keeps a system admin system-wide", async () => {
  render(<AdminRolesPage />);
  const { role, scope } = await form();
  fireEvent.change(role, { target: { value: "SYSTEM_ADMIN" } });
  expect(scope).toBeDisabled();
});
