"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ThemeToggle } from "./theme-toggle";

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
    document.body.classList.add("is-drawer-open");

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = originalOverflow;
      document.body.classList.remove("is-drawer-open");
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-[1100] flex animate-fadeIn"
      style={{
        backgroundColor: "rgba(5, 11, 24, 0.8)",
        backdropFilter: "blur(14px)",
        WebkitBackdropFilter: "blur(14px)",
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget) {
          onClose();
        }
      }}
      role="dialog"
      aria-modal="true"
    >
      <div className="relative flex h-full w-full max-w-xs flex-col overflow-y-auto border-r border-[#258bff]/30 bg-[#050b18]/95 p-6 pb-28 shadow-2xl backdrop-blur-2xl transition-all text-[#eaf3ff] animate-slideInLeft sm:max-w-sm">
        {/* Drawer Header */}
        <div className="flex items-center justify-between border-b border-[#258bff]/20 pb-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#1264c5] text-base font-extrabold text-white shadow-lg shadow-[#258bff]/30 border border-[#73dfff]/40">
              RC
            </div>
            <div>
              <p className="text-base font-bold tracking-tight text-white flex items-center gap-1.5">
                RailConnect
              </p>
              <p className="text-[11px] font-medium text-[#73dfff]">
                भारतीय रेल • INDIAN RAILWAYS
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="flex h-10 w-10 items-center justify-center rounded-full text-[#8fa3c7] hover:bg-white/10 hover:text-white"
            aria-label="Close navigation menu"
          >
            <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Navigation Items Matching Reference Image */}
        <nav aria-label="Mobile Drawer Navigation" className="mt-5 flex flex-col gap-1.5">
          <Link
            href="/mobile"
            onClick={onClose}
            className="flex h-12 items-center gap-3.5 rounded-xl px-3.5 text-sm font-semibold text-[#eaf3ff] transition-all hover:bg-[#1264c5]/25 hover:text-[#73dfff]"
          >
            <span className="text-lg">🏠</span>
            <span>Home</span>
          </Link>

          <Link
            href="/mobile#journey"
            onClick={onClose}
            className="flex h-12 items-center gap-3.5 rounded-xl px-3.5 text-sm font-semibold text-[#eaf3ff] transition-all hover:bg-[#1264c5]/25 hover:text-[#73dfff]"
          >
            <span className="text-lg">🎫</span>
            <span>Book Tickets</span>
          </Link>

          <Link
            href="/verify-ticket"
            onClick={onClose}
            className="flex h-12 items-center gap-3.5 rounded-xl px-3.5 text-sm font-semibold text-[#eaf3ff] transition-all hover:bg-[#1264c5]/25 hover:text-[#73dfff]"
          >
            <span className="text-lg">🔍</span>
            <span>PNR Status</span>
          </Link>

          <Link
            href="/mobile/train-details"
            onClick={onClose}
            className="flex h-12 items-center gap-3.5 rounded-xl px-3.5 text-sm font-semibold text-[#eaf3ff] transition-all hover:bg-[#1264c5]/25 hover:text-[#73dfff]"
          >
            <span className="text-lg">📍</span>
            <span>Live Train Status</span>
          </Link>

          <Link
            href="/mobile/train-details"
            onClick={onClose}
            className="flex h-12 items-center gap-3.5 rounded-xl px-3.5 text-sm font-semibold text-[#eaf3ff] transition-all hover:bg-[#1264c5]/25 hover:text-[#73dfff]"
          >
            <span className="text-lg">📅</span>
            <span>Train Schedule</span>
          </Link>

          <Link
            href="/mobile/train-details"
            onClick={onClose}
            className="flex h-12 items-center gap-3.5 rounded-xl px-3.5 text-sm font-semibold text-[#eaf3ff] transition-all hover:bg-[#1264c5]/25 hover:text-[#73dfff]"
          >
            <span className="text-lg">💺</span>
            <span>Coach Position</span>
          </Link>

          <Link
            href="/mobile/account"
            onClick={onClose}
            className="flex h-12 items-center gap-3.5 rounded-xl px-3.5 text-sm font-semibold text-[#eaf3ff] transition-all hover:bg-[#1264c5]/25 hover:text-[#73dfff]"
          >
            <span className="text-lg">📜</span>
            <span>My Bookings</span>
          </Link>

          <Link
            href="/notifications"
            onClick={onClose}
            className="flex h-12 items-center gap-3.5 rounded-xl px-3.5 text-sm font-semibold text-[#eaf3ff] transition-all hover:bg-[#1264c5]/25 hover:text-[#73dfff]"
          >
            <span className="text-lg">🔔</span>
            <span>Notifications</span>
          </Link>

          <Link
            href="/mobile/account"
            onClick={onClose}
            className="flex h-12 items-center gap-3.5 rounded-xl px-3.5 text-sm font-semibold text-[#eaf3ff] transition-all hover:bg-[#1264c5]/25 hover:text-[#73dfff]"
          >
            <span className="text-lg">👤</span>
            <span>User Profile</span>
          </Link>

          <Link
            href="/support"
            onClick={onClose}
            className="flex h-12 items-center gap-3.5 rounded-xl px-3.5 text-sm font-semibold text-[#eaf3ff] transition-all hover:bg-[#1264c5]/25 hover:text-[#73dfff]"
          >
            <span className="text-lg">💬</span>
            <span>Help and Support</span>
          </Link>
        </nav>

        {/* Drawer Footer: Theme Selector & Auth Actions */}
        <div className="mt-auto border-t border-[#258bff]/20 pt-4">
          <div className="flex items-center justify-between pb-4">
            <span className="text-xs font-semibold text-[#8fa3c7]">
              Interface Theme
            </span>
            <ThemeToggle />
          </div>

          {authStatus === "signed-in" ? (
            <button
              onClick={() => {
                onClose();
                onSignOut?.();
              }}
              className="flex h-11 w-full items-center justify-center rounded-xl bg-rose-950/40 border border-rose-500/30 text-xs font-semibold text-rose-400 hover:bg-rose-900/50"
            >
              Sign out of account
            </button>
          ) : (
            <div className="grid grid-cols-2 gap-2">
              <Link
                href={`/${experience}/login`}
                onClick={onClose}
                className="flex h-11 items-center justify-center rounded-xl border border-[#258bff]/40 bg-[#0a1730] text-xs font-semibold text-[#eaf3ff] hover:bg-[#1264c5]/20"
              >
                Login
              </Link>
              <Link
                href={`/${experience}/register`}
                onClick={onClose}
                className="flex h-11 items-center justify-center rounded-xl bg-[#258bff] text-xs font-semibold text-white shadow-lg shadow-[#258bff]/30 hover:bg-[#1264c5]"
              >
                Register
              </Link>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
