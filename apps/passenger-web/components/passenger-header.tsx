"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiRequestError, apiFetch } from "../lib/api";
import { TrainTrackingModal } from "./train-tracking-modal";
import { NotificationBellDropdown } from "./notification-bell-dropdown";
import { MobileNavDrawer } from "./mobile-nav-drawer";
import { GlassModal } from "./glass-modal";

type PassengerHeaderProps = {
  experience: "laptop" | "mobile";
};

export function PassengerHeader({ experience }: PassengerHeaderProps) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [authStatus, setAuthStatus] = useState<"checking" | "signed-in" | "signed-out" | "error">("checking");
  const [pnrModalOpen, setPnrModalOpen] = useState(false);
  const [pnrInput, setPnrInput] = useState("");

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
      <header className="cinematic-navbar">
        {/* Left: Indian Railways Crest + RailConnect Logo */}
        <div className="cinematic-brand-group">
          <Link href={`/${experience}`} className="cinematic-brand-link">
            <div className="ir-emblem" aria-hidden="true">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
                <circle cx="12" cy="12" r="10" stroke="#73dfff" />
                <path d="M12 2v20M2 12h20M5 5l14 14M5 19L19 5" stroke="#258bff" strokeWidth="1.2" />
              </svg>
            </div>
            <div className="brand-titles">
              <div className="brand-main">
                <span className="brand-name">RailConnect</span>
              </div>
              <span className="brand-tagline">भारतीय रेल • INDIAN RAILWAYS</span>
            </div>
          </Link>
        </div>

        {/* Center: Desktop Navigation Links matching Reference Design */}
        {experience === "laptop" && (
          <nav aria-label="Main Navigation" className="cinematic-nav-links">
            <Link href="/laptop" className="cinematic-nav-item is-active">
              Home
            </Link>
            <Link href="/laptop#journey" className="cinematic-nav-item">
              Book Tickets
            </Link>
            <button
              onClick={() => setPnrModalOpen(true)}
              className="cinematic-nav-item"
              type="button"
            >
              PNR Status
            </button>
            <Link href="/laptop/train-details" className="cinematic-nav-item">
              Train Info
            </Link>
            <Link href="/support" className="cinematic-nav-item">
              Services
            </Link>
            <Link href="/laptop/account" className="cinematic-nav-item">
              My Bookings
            </Link>
          </nav>
        )}

        {/* Right: Quick Tools, Theme Switcher & User Actions */}
        <div className="cinematic-header-actions">
          <div className="desktop-tracking-btn">
            <TrainTrackingModal />
          </div>

          <NotificationBellDropdown />

          {experience === "laptop" ? (
            authStatus === "signed-in" ? (
              <Link className="cinematic-btn-primary" href="/laptop/account">
                <span className="user-icon">👤</span> Account
              </Link>
            ) : (
              <div className="auth-button-group">
                <Link className="cinematic-btn-quiet" href="/laptop/login">
                  <span className="user-icon">👤</span> Login
                </Link>
                <Link className="cinematic-btn-primary" href="/laptop/register">
                  Register
                </Link>
              </div>
            )
          ) : (
            <button
              type="button"
              onClick={() => setDrawerOpen(true)}
              aria-label="Open mobile navigation menu"
              className="mobile-hamburger-btn"
            >
              <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
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

      {/* PNR Status Quick Modal for Desktop & Mobile Header Link */}
      <GlassModal
        isOpen={pnrModalOpen}
        onClose={() => setPnrModalOpen(false)}
        title="Check PNR Status"
        subtitle="Retrieve live reservation status, assigned coach/berth, and chart preparation status from the railway database."
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (pnrInput.trim()) {
              window.location.href = `/verify-ticket`;
            }
          }}
          className="pnr-quick-form"
        >
          <label className="pnr-input-label">
            10-Digit PNR / Booking Reference
            <input
              type="text"
              required
              maxLength={24}
              placeholder="Enter PNR Number"
              value={pnrInput}
              onChange={(e) => setPnrInput(e.target.value)}
              className="pnr-text-input"
            />
          </label>
          <div className="pnr-modal-actions" style={{ marginTop: "16px" }}>
            <button type="submit" className="button button--primary" style={{ width: "100%" }}>
              Check Status →
            </button>
          </div>
        </form>
      </GlassModal>
    </>
  );
}
