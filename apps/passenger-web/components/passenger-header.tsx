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
          <DeviceExperienceSwitch current={experience} />
          <NotificationBellDropdown />
          {authStatus === "signed-in" ? (
            <Link className="header-account-link" href="/mobile/account">Passenger account</Link>
          ) : authStatus === "signed-out" ? (
            <>
              <Link className="header-account-link" href="/mobile/login">Sign in</Link>
              <Link className="header-account-link" href="/mobile/register">Join</Link>
            </>
          ) : authStatus === "error" ? (
            <>
              <span role="status">Account status unavailable</span>
              <Link className="header-account-link" href="/mobile/login">Sign in</Link>
            </>
          ) : null}
        </div>
      )}
    </header>
  );
}
