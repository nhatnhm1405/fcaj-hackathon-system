import { PixelBadge } from "@/shared/components/PixelComponents";

// Shared role-name → badge mapping used by both AdminRolesPage and
// AdminDashboard, so a role always renders the same color + label everywhere
// instead of leaking the raw enum value (e.g. "EVENT_COORDINATOR") as plain
// uppercase text.
export function roleBadge(roleName: string) {
  if (roleName === 'SYSTEM_ADMIN') return <PixelBadge color="red">SYSTEM ADMIN</PixelBadge>;
  if (roleName === 'EVENT_COORDINATOR') return <PixelBadge color="purple">COORDINATOR</PixelBadge>;
  if (roleName === 'MENTOR') return <PixelBadge color="cyan">MENTOR</PixelBadge>;
  if (roleName === 'JUDGE') return <PixelBadge color="blue">JUDGE</PixelBadge>;
  return <PixelBadge color="gray">{roleName}</PixelBadge>;
}
