"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ThemeToggle } from "./theme-toggle";

type NavLink = {
  label: string;
  href: string;
  icon?: string;
};

type NavGroup = {
  title: string;
  icon?: string;
  items: NavLink[];
};

type MobileNavDrawerProps = {
  isOpen: boolean;
  onClose: () => void;
  experience?: "laptop" | "mobile";
  authStatus: "checking" | "signed-in" | "signed-out" | "error";
  onSignOut?: () => void;
};

export function MobileNavDrawer({
  isOpen,
  onClose,
  experience = "mobile",
  authStatus,
  onSignOut,
}: MobileNavDrawerProps) {
  const [expandedGroups, setExpandedGroups] = useState<Record<string, boolean>>({
    journeys: false,
    support: false,
    settings: false,
  });

  const toggleGroup = (groupKey: string) => {
    setExpandedGroups((prev) => ({ ...prev, [groupKey]: !prev[groupKey] }));
  };

  useEffect(() => {
    if (!isOpen) return;

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") {
        onClose();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = originalOverflow;
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-[1100] flex animate-fadeIn"
      style={{
        backgroundColor: "rgba(10, 15, 29, 0.65)",
        backdropFilter: "blur(10px)",
        WebkitBackdropFilter: "blur(10px)",
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget) {
          onClose();
        }
      }}
      role="dialog"
      aria-modal="true"
    >
      <div className="relative flex h-full w-full max-w-xs flex-col overflow-y-auto border-r border-slate-200/80 bg-white/95 p-6 pb-24 shadow-2xl backdrop-blur-xl transition-all dark:border-slate-800/80 dark:bg-slate-900/95 dark:text-slate-100 animate-slideInLeft sm:max-w-sm">
        {/* Drawer Header */}
        <div className="flex items-center justify-between border-b border-slate-200/80 pb-4 dark:border-slate-800">
          <div className="flex items-center gap-2">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-orange-600 text-base font-extrabold text-white shadow-md">
              RC
            </span>
            <div>
              <p className="text-sm font-bold tracking-tight text-slate-900 dark:text-white">
                RailConnect
              </p>
              <p className="text-[10px] font-medium text-orange-600 dark:text-orange-400">
                Enterprise Express
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="flex h-10 w-10 items-center justify-center rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-slate-800 dark:hover:text-slate-200"
            aria-label="Close navigation menu"
          >
            ✕
          </button>
        </div>

        {/* User Identity Banner (if logged in) */}
        {authStatus === "signed-in" && (
          <div className="mt-4 rounded-xl border border-orange-200/60 bg-orange-50/50 p-3.5 dark:border-orange-500/20 dark:bg-orange-950/20">
            <p className="text-[10px] font-semibold uppercase tracking-wider text-orange-700 dark:text-orange-400">
              Authenticated Passenger
            </p>
            <p className="mt-0.5 text-xs font-medium text-slate-800 dark:text-slate-200">
              Access your digital reservations
            </p>
            <Link
              href={`/${experience}/account`}
              onClick={onClose}
              className="mt-2 inline-flex items-center text-xs font-semibold text-orange-600 hover:underline dark:text-orange-400"
            >
              Open Dashboard →
            </Link>
          </div>
        )}

        {/* Navigation Links & Accordions */}
        <nav className="mt-6 flex-1 space-y-1.5 text-sm font-medium">
          <Link
            href={`/${experience}`}
            onClick={onClose}
            className="flex h-11 items-center gap-3 rounded-lg px-3 text-slate-700 transition-colors hover:bg-slate-100 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white"
          >
            <span>🚆</span>
            <span>Book Tickets</span>
          </Link>

          {/* Accordion: My Journeys */}
          <div>
            <button
              onClick={() => toggleGroup("journeys")}
              className="flex h-11 w-full items-center justify-between rounded-lg px-3 text-slate-700 transition-colors hover:bg-slate-100 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white"
            >
              <div className="flex items-center gap-3">
                <span>🎫</span>
                <span>My Journeys</span>
              </div>
              <span className="text-xs">{expandedGroups.journeys ? "▲" : "▼"}</span>
            </button>
            {expandedGroups.journeys && (
              <div className="ml-7 mt-1 space-y-1 border-l-2 border-slate-200 pl-3 dark:border-slate-800">
                <Link
                  href={`/${experience}/account#bookings`}
                  onClick={onClose}
                  className="block py-1.5 text-xs text-slate-600 hover:text-orange-600 dark:text-slate-400 dark:hover:text-orange-400"
                >
                  Upcoming Bookings
                </Link>
                <Link
                  href={`/${experience}/account#history`}
                  onClick={onClose}
                  className="block py-1.5 text-xs text-slate-600 hover:text-orange-600 dark:text-slate-400 dark:hover:text-orange-400"
                >
                  Past Journeys
                </Link>
                <Link
                  href="/verify-ticket"
                  onClick={onClose}
                  className="block py-1.5 text-xs text-slate-600 hover:text-orange-600 dark:text-slate-400 dark:hover:text-orange-400"
                >
                  Verify PNR & Ticket
                </Link>
              </div>
            )}
          </div>

          <Link
            href="/notifications"
            onClick={onClose}
            className="flex h-11 items-center gap-3 rounded-lg px-3 text-slate-700 transition-colors hover:bg-slate-100 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white"
          >
            <span>🔔</span>
            <span>Notifications & Alerts</span>
          </Link>

          <Link
            href="/support"
            onClick={onClose}
            className="flex h-11 items-center gap-3 rounded-lg px-3 text-slate-700 transition-colors hover:bg-slate-100 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white"
          >
            <span>💬</span>
            <span>Customer Support</span>
          </Link>
        </nav>

        {/* Drawer Footer: Theme Selector & Auth Actions */}
        <div className="mt-auto border-t border-slate-200/80 pt-4 dark:border-slate-800">
          <div className="flex items-center justify-between pb-4">
            <span className="text-xs font-medium text-slate-500 dark:text-slate-400">
              Appearance
            </span>
            <ThemeToggle />
          </div>

          {authStatus === "signed-in" ? (
            <button
              onClick={() => {
                onClose();
                onSignOut?.();
              }}
              className="flex h-11 w-full items-center justify-center rounded-xl bg-slate-100 text-xs font-semibold text-rose-600 transition-colors hover:bg-rose-50 dark:bg-slate-800 dark:text-rose-400 dark:hover:bg-rose-950/40"
            >
              Sign out of account
            </button>
          ) : (
            <div className="grid grid-cols-2 gap-2">
              <Link
                href={`/${experience}/login`}
                onClick={onClose}
                className="flex h-11 items-center justify-center rounded-xl border border-slate-300 bg-white text-xs font-semibold text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
              >
                Sign in
              </Link>
              <Link
                href={`/${experience}/register`}
                onClick={onClose}
                className="flex h-11 items-center justify-center rounded-xl bg-orange-600 text-xs font-semibold text-white shadow-sm hover:bg-orange-700"
              >
                Create account
              </Link>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

