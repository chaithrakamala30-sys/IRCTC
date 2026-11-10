"use client";

import { useTheme } from "./theme-provider";

export function ThemeToggle({ className = "" }: { className?: string }) {
  const { theme, setTheme } = useTheme();

  return (
    <div
      className={`inline-flex items-center rounded-full border border-slate-200/80 bg-slate-100/80 p-0.5 backdrop-blur-md dark:border-slate-800 dark:bg-slate-900/80 ${className}`}
      role="radiogroup"
      aria-label="Theme selection"
    >
      <button
        type="button"
        onClick={() => setTheme("light")}
        aria-checked={theme === "light"}
        role="radio"
        title="Light Mode"
        className={`flex h-7 w-7 items-center justify-center rounded-full text-xs font-medium transition-all ${
          theme === "light"
            ? "bg-white text-amber-600 shadow-sm dark:bg-slate-800 dark:text-amber-400"
            : "text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-200"
        }`}
      >
        <span aria-hidden="true">☀️</span>
        <span className="sr-only">Light</span>
      </button>

      <button
        type="button"
        onClick={() => setTheme("system")}
        aria-checked={theme === "system"}
        role="radio"
        title="System Default"
        className={`flex h-7 w-7 items-center justify-center rounded-full text-xs font-medium transition-all ${
          theme === "system"
            ? "bg-white text-indigo-600 shadow-sm dark:bg-slate-800 dark:text-indigo-400"
            : "text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-200"
        }`}
      >
        <span aria-hidden="true">💻</span>
        <span className="sr-only">System</span>
      </button>

      <button
        type="button"
        onClick={() => setTheme("dark")}
        aria-checked={theme === "dark"}
        role="radio"
        title="Dark Mode"
        className={`flex h-7 w-7 items-center justify-center rounded-full text-xs font-medium transition-all ${
          theme === "dark"
            ? "bg-white text-sky-600 shadow-sm dark:bg-slate-800 dark:text-sky-400"
            : "text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-200"
        }`}
      >
        <span aria-hidden="true">🌙</span>
        <span className="sr-only">Dark</span>
      </button>
    </div>
  );
}

