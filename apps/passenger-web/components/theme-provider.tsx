"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";

type Theme = "dark";

type ThemeContextType = {
  theme: Theme;
  effectiveTheme: "dark";
  setTheme: (theme: Theme) => void;
};

const ThemeContext = createContext<ThemeContextType>({
  theme: "dark",
  effectiveTheme: "dark",
  setTheme: () => {},
});

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme] = useState<Theme>("dark");
  const effectiveTheme = "dark";

  useEffect(() => {
    // Force dark theme system-wide
    localStorage.setItem("railconnect_theme", "dark");
    const root = document.documentElement;
    root.classList.remove("light");
    root.classList.add("dark");
    root.style.colorScheme = "dark";
  }, []);

  const setTheme = (_newTheme: Theme) => {
    // Fixed to dark mode
    localStorage.setItem("railconnect_theme", "dark");
  };

  return (
    <ThemeContext.Provider value={{ theme, effectiveTheme, setTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  return useContext(ThemeContext);
}

