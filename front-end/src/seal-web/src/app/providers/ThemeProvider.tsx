import { createContext, useContext, useLayoutEffect, useState, type ReactNode } from "react";

type Theme = "dark" | "light";
const ThemeContext = createContext<{ theme: Theme; toggleTheme: () => void } | null>(null);

export function useTheme() {
  const value = useContext(ThemeContext);
  if (value === null) throw new Error("useTheme requires ThemeProvider");
  return value;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(() =>
    document.documentElement.dataset.theme === "light" ? "light" : "dark",
  );

  useLayoutEffect(() => {
    const root = document.documentElement;
    root.dataset.theme = theme;
    root.classList.toggle("dark", theme === "dark");
    try {
      localStorage.setItem("seal-theme", theme);
    } catch {
      // The theme still applies when storage is unavailable.
    }
  }, [theme]);

  return (
    <ThemeContext.Provider value={{
      theme,
      toggleTheme: () => setTheme(current => current === "dark" ? "light" : "dark"),
    }}>
      {children}
    </ThemeContext.Provider>
  );
}
