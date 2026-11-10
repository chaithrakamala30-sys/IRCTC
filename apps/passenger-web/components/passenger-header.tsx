"use client";

import {
  Brand,
  DeviceExperienceSwitch,
  PortalNavigation,
  type NavigationItem,
} from "@railconnect/ui";
import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiRequestError, apiFetch } from "../lib/api";
import { TrainTrackingModal } from "./train-tracking-modal";
import { NotificationBellDropdown } from "./notification-bell-dropdown";
import { ThemeToggle } from "./theme-toggle";
import { MobileNavDrawer } from "./mobile-nav-drawer";

const navigationItems: NavigationItem[] = [
  { href: "/laptop", label: "Home" },
  { href: "#journey", label: "Journey" },
  { href: "#status", label: "Service status" },
  { href: "/support", label: "Support" },
];

type PassengerHeaderProps = {
  experience: "laptop" | "mobile";
};

export function PassengerHeader({ experience }: PassengerHeaderProps) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [authStatus, setAuthStatus] = useState<
    "checking" | "signed-in" | "signed-out" | "error"
  >("checking");

  useEffect(() => {
    let active = true;
    void apiFetch("/api/v1/auth/me")
      .then(() => {
        if (active) setAuthStatus("signed-in");
      })
      .catch((error: unknown) => {
        if (!active) return;
        if (error instanceof ApiRequestError && [401, 403].includes(error.status)) {
          setAuthStatus("signed-out");
          return;
        }
        setAuthStatus("error");
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <header className="site-header">
      <Brand href={`/${experience}`} />
      {experience === "laptop" ? (
        <div className="passenger-header-actions">
          <PortalNavigation activeHref="/laptop" items={navigationItems} />
          <TrainTrackingModal />
          <NotificationBellDropdown />
          <ThemeToggle />
          {authStatus === "signed-in" ? (
            <Link className="button button--primary" href="/laptop/account">Passenger account</Link>
          ) : authStatus === "signed-out" ? (
            <>
              <Link className="button button--secondary" href="/laptop/login">Sign in</Link>
              <Link className="button button--primary" href="/laptop/register">Create account</Link>
            </>
          ) : authStatus === "error" ? (
            <span role="status">Account status unavailable</span>
          ) : null}
          {authStatus === "error" && (
            <Link className="button button--secondary" href="/laptop/login">Sign in</Link>
          )}
        </div>
      ) : (
        <div className="passenger-header-actions">
          <NotificationBellDropdown />
          <button
            type="button"
            onClick={() => setDrawerOpen(true)}
            aria-label="Open mobile menu"
            className="flex h-10 w-10 items-center justify-center rounded-xl border border-slate-200/80 bg-white/80 p-2 text-slate-700 shadow-sm transition hover:bg-slate-100 dark:border-slate-800 dark:bg-slate-900/80 dark:text-slate-200 dark:hover:bg-slate-800"
          >
            <svg
              className="h-5 w-5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M4 6h16M4 12h16M4 18h16"
              />
            </svg>
          </button>
        </div>
      )}
      <MobileNavDrawer
        isOpen={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        experience={experience}
        authStatus={authStatus}
      />
    </header>
  );
}
