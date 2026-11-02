import {
  createBrowserRouter,
  Navigate,
  Outlet,
  useLocation,
  useNavigate,
} from "react-router";
import { useAuth } from "@/app/providers/AuthProvider";
import { LandingPage } from "@/features/landing/LandingPage";
import { LoginPage } from "@/features/auth/LoginPage";
import { RegisterPage } from "@/features/auth/RegisterPage";
import { PendingApprovalPage } from "@/features/auth/PendingApprovalPage";
import { RoleSelector } from "@/features/auth/RoleSelector";
import { DashboardLayout } from "@/app/layouts/DashboardLayout";
import { RoleDashboardPage } from "@/features/dashboard/RoleDashboardPage";
import { JudgeDashboard } from "@/features/dashboard/dashboards/JudgeDashboard";
import { MentorDashboard } from "@/features/dashboard/dashboards/MentorDashboard";
import { CoordinatorDashboard } from "@/features/dashboard/dashboards/CoordinatorDashboard";
import { LeaderboardPage } from "@/features/scoring/LeaderboardPage";
import { ProfilePage } from "@/features/users/ProfilePage";
import { TeamCreatePage } from "@/features/teams/TeamCreatePage";
import { TeamSubmitPage } from "@/features/submissions/TeamSubmitPage";
import { TeamViewPage } from "@/features/teams/TeamViewPage";
import { HistoryPage } from "@/features/teams/HistoryPage";
import { MentorTracksPage } from "@/features/tracks/MentorTracksPage";
import { MentorHistoryPage } from "@/features/tracks/MentorHistoryPage";
import { JudgeScoringPage } from "@/features/scoring/JudgeScoringPage";
import { JudgeHistoryPage } from "@/features/scoring/JudgeHistoryPage";
import { CoordEventsPage } from "@/features/events/CoordEventsPage";
import { CoordAccountsPage } from "@/features/users/CoordAccountsPage";
import { CoordTeamsPage } from "@/features/teams/CoordTeamsPage";
import { CoordJudgesPage } from "@/features/scoring/CoordJudgesPage";
import { CoordAssignmentHistoryPage } from "@/features/scoring/CoordAssignmentHistoryPage";
import { CoordScoringPage } from "@/features/scoring/CoordScoringPage";
import { CoordPrizesPage } from "@/features/scoring/CoordPrizesPage";
import { AdminEventsPage } from "@/features/events/AdminEventsPage";
import { AdminAccountsPage } from "@/features/users/AdminAccountsPage";
import { AdminRolesPage } from "@/features/users/AdminRolesPage";
import { AdminSystemLogsPage } from "@/features/users/AdminSystemLogsPage";
import { ForgotPasswordPage } from "@/features/auth/ForgotPasswordPage";
import { CompleteProfilePage } from "@/features/auth/CompleteProfilePage";
import { OAuth2RedirectPage } from "@/features/auth/OAuth2RedirectPage";
import { AboutPage } from "@/features/landing/AboutPage";
import { TeamPage } from "@/features/landing/TeamPage";
import { ContactPage } from "@/features/landing/ContactPage";

function RequireAuth({
  allowedRoles,
}: {
  allowedRoles?: string[];
}) {
  const { currentUser, isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  // Wait for session restore before deciding to redirect
  if (isLoading) return null;

  if (!isAuthenticated)
    return (
      <Navigate
        to="/login"
        state={{ from: location }}
        replace
      />
    );
  // First-time OAuth users must finish signup before reaching any app screen.
  if (currentUser?.profile_incomplete && location.pathname !== "/complete-profile") {
    return <Navigate to="/complete-profile" replace />;
  }
  // Authenticated but not yet approved by a coordinator → hold on the pending page.
  if (currentUser && !currentUser.approved && !currentUser.profile_incomplete) {
    return <Navigate to="/pending-approval" replace />;
  }
  if (
    allowedRoles &&
    currentUser &&
    !allowedRoles.includes(currentUser.role)
  ) {
    return <Navigate to="/dashboard" replace />;
  }
  return <Outlet />;
}

// Keeps already-authenticated users off the public auth screens (/login,
// /register, /forgot-password). Without this, an active session could open
// /login by URL and sign into a second account on top of the first.
function RedirectIfAuthenticated() {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) return null;
  if (isAuthenticated) return <Navigate to="/dashboard" replace />;
  return <Outlet />;
}

// Guards staff-only routes: if a multi-role user hasn't selected yet, send to /select-role
function RoleGate() {
  const { availableRoles, activeRole } = useAuth();
  if (availableRoles.length > 1 && activeRole === null) {
    return <Navigate to="/select-role" replace />;
  }
  return <Outlet />;
}

function DashboardWrapper() {
  return (
    <DashboardLayout>
      <Outlet />
    </DashboardLayout>
  );
}

function RootLayout() {
  return <Outlet />;
}

function LandingPageWrapper() {
  const navigate = useNavigate();
  // Everyone — visitors and logged-in users — gets the full standalone home page
  // with its own navbar + footer intact. The home navbar shows a "Go to Dashboard"
  // button when authenticated, so logged-in users can jump back into the app.
  return (
    <LandingPage
      navigate={(page) => {
        if (page === "auth") navigate("/login");
        else if (page === "register") navigate("/register");
        else if (page === "dashboard") navigate("/dashboard");
      }}
    />
  );
}

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      { path: "/", Component: LandingPageWrapper },
      { path: "/about", Component: AboutPage },
      { path: "/team", Component: TeamPage },
      { path: "/contact", Component: ContactPage },
      {
        element: <RedirectIfAuthenticated />,
        children: [
          { path: "/login", Component: LoginPage },
          { path: "/register", Component: RegisterPage },
          {
            path: "/forgot-password",
            Component: ForgotPasswordPage,
          },
        ],
      },
      {
        path: "/pending-approval",
        Component: PendingApprovalPage,
      },
      {
        path: "/oauth2/redirect",
        Component: OAuth2RedirectPage,
      },
      {
        element: <RequireAuth />,
        children: [
          // Full-page (no dashboard chrome) — first-time OAuth profile completion
          { path: "/complete-profile", Component: CompleteProfilePage },
          {
            element: <DashboardWrapper />,
            children: [
              // Role selector — accessible to any authenticated user
              {
                path: "/select-role",
                Component: RoleSelector,
              },
              // Shared routes (all authenticated roles)
              {
                path: "/dashboard",
                Component: RoleDashboardPage,
              },
              {
                path: "/leaderboard",
                Component: LeaderboardPage,
              },
              { path: "/profile", Component: ProfilePage },
              // Participant routes — is_leader check happens inside each page
              {
                element: (
                  <RequireAuth allowedRoles={["PARTICIPANT"]} />
                ),
                children: [
                  {
                    path: "/team/view",
                    Component: TeamViewPage,
                  },
                  {
                    path: "/team/create",
                    Component: TeamCreatePage,
                  },
                  {
                    path: "/team/manage",
                    element: <Navigate to="/team/view" replace />,
                  },
                  {
                    path: "/team/submit",
                    Component: TeamSubmitPage,
                  },
                  {
                    path: "/history",
                    Component: HistoryPage,
                  },
                ],
              },
              // Admin routes — SYSTEM_ADMIN is single-role, no RoleGate needed
              {
                element: <RequireAuth allowedRoles={["ADMIN"]} />,
                children: [
                  {
                    path: "/admin/dashboard",
                    Component: RoleDashboardPage,
                  },
                  {
                    path: "/admin/events",
                    Component: AdminEventsPage,
                  },
                  {
                    path: "/admin/accounts",
                    Component: AdminAccountsPage,
                  },
                  {
                    path: "/admin/roles",
                    Component: AdminRolesPage,
                  },
                  {
                    path: "/admin/logs",
                    Component: AdminSystemLogsPage,
                  },
                ],
              },
              // Staff routes — guarded by RoleGate (requires activeRole to be set)
              {
                element: <RoleGate />,
                children: [
                  // Role-specific dashboard entry points (from RoleSelector)
                  {
                    path: "/dashboard/judge",
                    element: <JudgeDashboard />,
                  },
                  {
                    path: "/dashboard/mentor",
                    element: <MentorDashboard />,
                  },
                  {
                    path: "/dashboard/coordinator",
                    element: <CoordinatorDashboard />,
                  },
                  {
                    element: (
                      <RequireAuth allowedRoles={["MENTOR"]} />
                    ),
                    children: [
                      {
                        path: "/mentor/tracks",
                        Component: MentorTracksPage,
                      },
                      {
                        path: "/mentor/history",
                        Component: MentorHistoryPage,
                      },
                    ],
                  },
                  {
                    element: (
                      <RequireAuth allowedRoles={["JUDGE"]} />
                    ),
                    children: [
                      {
                        path: "/judge/score",
                        Component: JudgeScoringPage,
                      },
                      {
                        path: "/judge/history",
                        Component: JudgeHistoryPage,
                      },
                    ],
                  },
                  {
                    element: (
                      <RequireAuth allowedRoles={["COORDINATOR"]} />
                    ),
                    children: [
                      {
                        path: "/coordinator/dashboard",
                        Component: RoleDashboardPage,
                      },
                      {
                        path: "/coordinator/events",
                        Component: CoordEventsPage,
                      },
                      {
                        path: "/coordinator/accounts",
                        Component: CoordAccountsPage,
                      },
                      {
                        path: "/coordinator/teams",
                        Component: CoordTeamsPage,
                      },
                      {
                        path: "/coordinator/judges",
                        Component: CoordJudgesPage,
                      },
                      {
                        path: "/coordinator/judges/history",
                        Component: CoordAssignmentHistoryPage,
                      },
                      {
                        path: "/coordinator/scoring",
                        Component: CoordScoringPage,
                      },
                      {
                        path: "/coordinator/prizes",
                        Component: CoordPrizesPage,
                      },
                    ],
                  },
                ],
              },
            ],
          },
        ],
      },
      { path: "*", element: <Navigate to="/" replace /> },
    ],
  },
]);
