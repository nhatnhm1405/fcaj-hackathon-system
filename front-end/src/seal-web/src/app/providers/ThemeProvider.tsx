import { useLayoutEffect, ReactNode } from "react";

function applyDarkTheme() {
  const root = document.documentElement;
  root.setAttribute("data-theme", "dark");
  root.classList.add("dark");
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  useLayoutEffect(() => {
    applyDarkTheme();
    try {
      localStorage.removeItem("seal-theme");
    } catch {
      // Storage can be unavailable in hardened browser contexts.
    }
  }, []);

  return <>{children}</>;
}
