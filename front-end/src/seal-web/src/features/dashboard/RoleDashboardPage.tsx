import { useAuth } from "@/app/providers/AuthProvider";
import { ParticipantDashboard } from "@/features/dashboard/dashboards/ParticipantDashboard";
import { MentorDashboard } from "@/features/dashboard/dashboards/MentorDashboard";
import { JudgeDashboard } from "@/features/dashboard/dashboards/JudgeDashboard";
import { CoordinatorDashboard } from "@/features/dashboard/dashboards/CoordinatorDashboard";
import { AdminDashboard } from "@/features/dashboard/dashboards/AdminDashboard";

export function RoleDashboardPage() {
  const { currentUser } = useAuth();
  if (!currentUser) return null;
  switch (currentUser.role) {
    case 'PARTICIPANT': return <ParticipantDashboard />;
    case 'MENTOR': return <MentorDashboard />;
    case 'JUDGE': return <JudgeDashboard />;
    case 'COORDINATOR': return <CoordinatorDashboard />;
    case 'ADMIN': return <AdminDashboard />;
    default: return null;
  }
}
