"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiRequestError, apiFetch } from "../lib/api";
import { ThemeToggle } from "./theme-toggle";
import { MobileNavDrawer } from "./mobile-nav-drawer";
import { TrainTrackingModal } from "./train-tracking-modal";

type HeaderProps = {
  experience: "laptop" | "mobile";
  activeTab?: string;
};

export function ReferenceHeader({ experience, activeTab = "home" }: HeaderProps) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [authStatus, setAuthStatus] = useState<"checking" | "signed-in" | "signed-out" | "error">("checking");
  const [pnrOpen, setPnrOpen] = useState(false);

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
    <>
      <header className="ref-nav-header">
        {/* Left: Indian Railways Wheel Crest + RailConnect Logo */}
        <div className="ref-brand-container">
          <Link href={`/${experience}`} className="ref-brand-anchor">
            {/* Ashoka Chakra / Indian Railway Wheel Emblem */}
            <div className="ref-chakra-mark" aria-hidden="true">
              <svg width="34" height="34" viewBox="0 0 100 100">
                <circle cx="50" cy="50" r="46" stroke="#73dfff" strokeWidth="4" fill="none" opacity="0.9" />
                <circle cx="50" cy="50" r="38" stroke="#258bff" strokeWidth="2" fill="none" />
                <circle cx="50" cy="50" r="14" fill="#1264c5" stroke="#73dfff" strokeWidth="3" />
                {Array.from({ length: 24 }).map((_, i) => {
                  const angle = (i * 360) / 24;
                  return (
                    <line
                      key={i}
                      x1="50"
                      y1="50"
                      x2={50 + 38 * Math.cos((angle * Math.PI) / 180)}
                      y2={50 + 38 * Math.sin((angle * Math.PI) / 180)}
                      stroke="#73dfff"
                      strokeWidth="2.5"
                    />
                  );
                })}
              </svg>
            </div>

            {/* Aerodynamic Train Mark */}
            <div className="ref-train-icon-mark" aria-hidden="true">
              <svg width="32" height="22" viewBox="0 0 60 40" fill="none">
                <path
                  d="M5 25 C15 10, 35 8, 55 12 L55 24 C45 28, 20 28, 5 25 Z"
                  fill="url(#trainGrad)"
                  stroke="#73dfff"
                  strokeWidth="1.5"
                />
                <circle cx="48" cy="18" r="3.5" fill="#ffffff" />
                <defs>
                  <linearGradient id="trainGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                    <stop offset="0%" stopColor="#1264c5" />
                    <stop offset="100%" stopColor="#73dfff" />
                  </linearGradient>
                </defs>
              </svg>
            </div>

            <div className="ref-brand-titles">
              <span className="ref-brand-text">RailConnect</span>
              <span className="ref-brand-subtext">Travel India. Together.</span>
            </div>
          </Link>
        </div>

        {/* Center: Desktop Top Pill Navigation Bar matching Reference */}
        {experience === "laptop" && (
          <nav aria-label="Main Navigation" className="ref-center-nav">
            <Link
              href="/laptop"
              className={`ref-pill-link ${activeTab === "home" ? "is-active" : ""}`}
            >
              <span className="ref-pill-icon">🏠</span>
              <span>Home</span>
            </Link>

            <Link
              href="/laptop#journey"
              className={`ref-pill-link ${activeTab === "booking" ? "is-active" : ""}`}
            >
              <span>Book Tickets</span>
              <span className="ref-chevron-down">▾</span>
            </Link>

            <button
              type="button"
              onClick={() => setPnrOpen(true)}
              className={`ref-pill-link ${activeTab === "pnr" ? "is-active" : ""}`}
            >
              <span>PNR Status</span>
            </button>

            <Link
              href="/laptop/train-details"
              className={`ref-pill-link ${activeTab === "train-info" ? "is-active" : ""}`}
            >
              <span>Train Info</span>
              <span className="ref-chevron-down">▾</span>
            </Link>

            <Link
              href="/support"
              className={`ref-pill-link ${activeTab === "services" ? "is-active" : ""}`}
            >
              <span>Services</span>
              <span className="ref-chevron-down">▾</span>
            </Link>

            <Link
              href="/notifications"
              className={`ref-pill-link ${activeTab === "more" ? "is-active" : ""}`}
            >
              <span>More</span>
              <span className="ref-chevron-down">▾</span>
            </Link>
          </nav>
        )}

        {/* Right: Theme Toggle + Login / Register Buttons matching Reference */}
        <div className="ref-right-actions">
          <ThemeToggle />

          {experience === "laptop" ? (
            authStatus === "signed-in" ? (
              <Link className="ref-btn-login" href="/laptop/account">
                <span className="ref-user-glyph">👤</span>
                <span>My Account</span>
              </Link>
            ) : (
              <div className="ref-auth-buttons">
                <Link className="ref-btn-login" href="/laptop/login">
                  <span className="ref-user-glyph">👤</span>
                  <span>Login</span>
                </Link>
                <Link className="ref-btn-register" href="/laptop/register">
                  Register
                </Link>
              </div>
            )
          ) : (
            <button
              type="button"
              onClick={() => setDrawerOpen(true)}
              aria-label="Open mobile menu"
              className="ref-mobile-hamburger"
            >
              <svg width="24" height="24" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.2} d="M4 6h16M4 12h16M4 18h16" />
              </svg>
            </button>
          )}
        </div>

        <MobileNavDrawer
          isOpen={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          experience={experience}
          authStatus={authStatus}
        />
      </header>
    </>
  );
}
