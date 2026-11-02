import { RouterProvider } from "react-router";
import { router } from "@/app/routes/index";
import { AuthProvider } from "@/app/providers/AuthProvider";
import { NotificationProvider } from "@/app/providers/NotificationProvider";
import { PendingAccountsProvider } from "@/app/providers/PendingAccountsProvider";
import { PendingTeamsProvider } from "@/app/providers/PendingTeamsProvider";
import { RulesProvider } from "@/app/providers/RulesProvider";
import { TourProvider } from "@/app/providers/TourProvider";
import { ThemeProvider } from "@/app/providers/ThemeProvider";

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <NotificationProvider>
          <PendingAccountsProvider>
            <PendingTeamsProvider>
              <TourProvider>
                <RulesProvider>
                  <RouterProvider router={router} />
                </RulesProvider>
              </TourProvider>
            </PendingTeamsProvider>
          </PendingAccountsProvider>
        </NotificationProvider>
      </AuthProvider>
    </ThemeProvider>
  );
}
