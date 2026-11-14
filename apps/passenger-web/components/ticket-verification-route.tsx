"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { TicketVerification } from "./ticket-verification";
import { GoogleIcon } from "./google-icon";

export function TicketVerificationRoute() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const pnrParam = searchParams.get("pnr") || "";
  const [inputVal, setInputVal] = useState(pnrParam);

  if (!token) {
    return (
      <main className="ticket-verify-shell">
        <header className="ticket-verify-header">
          <p className="eyebrow">Official RailConnect Reservation System</p>
          <h1>Ticket &amp; PNR Verification</h1>
          <p className="text-[#8fa3c7] text-sm mt-1 mb-6">
            Verify the authenticity of digital rail tickets or look up your booking verification status using a signed verification token or 10-digit PNR.
          </p>
        </header>

        <div className="bg-[rgba(6,16,36,0.85)] border border-[rgba(56,189,248,0.25)] rounded-2xl p-6 mb-6">
          <h2 className="text-lg font-bold text-white mb-2 flex items-center gap-2">
            <GoogleIcon name="qr_code_scanner" size={20} color="#38bdf8" />
            <span>Verify via Security Token</span>
          </h2>
          <p className="text-sm text-[#8fa3c7] mb-4">
            If you have a digital ticket PDF or scanned the QR code from a RailConnect ticket, enter the signed token below:
          </p>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              const trimmed = inputVal.trim();
              if (trimmed) {
                window.location.href = `/verify-ticket?token=${encodeURIComponent(trimmed)}`;
              }
            }}
            className="flex flex-col sm:flex-row gap-3"
          >
            <input
              type="text"
              placeholder="Enter signed verification token or paste QR URL"
              value={inputVal}
              onChange={(e) => setInputVal(e.target.value)}
              className="flex-1 bg-[rgba(10,24,52,0.95)] border border-[rgba(56,189,248,0.3)] rounded-xl px-4 py-3 text-white text-sm outline-none focus:border-[#38bdf8]"
            />
            <button
              type="submit"
              className="px-6 py-3 bg-gradient-to-r from-[#1877f2] to-[#0284c7] hover:from-[#2583fa] hover:to-[#0396e0] text-white font-semibold text-sm rounded-xl transition flex items-center justify-center gap-2"
            >
              <span>Verify Ticket</span>
              <GoogleIcon name="arrow_forward" size={16} />
            </button>
          </form>
        </div>

        <div className="bg-[rgba(6,16,36,0.65)] border border-[rgba(255,255,255,0.08)] rounded-xl p-5 mb-6 text-sm text-[#8fa3c7] flex items-start gap-3">
          <GoogleIcon name="info" size={20} color="#38bdf8" className="flex-shrink-0 mt-0.5" />
          <div>
            <strong className="text-white block mb-0.5">Looking for your reservation / PNR details?</strong>
            <span>
              If you booked a ticket and want to view your assigned berths, coach number, or download your PDF receipt, visit{" "}
              <Link href="/laptop/account" className="text-[#38bdf8] underline hover:text-[#73dfff]">
                My Bookings
              </Link>
              {" "}or return to the{" "}
              <Link href="/laptop" className="text-[#38bdf8] underline hover:text-[#73dfff]">
                Home Page
              </Link>.
            </span>
          </div>
        </div>

        <Link
          className="inline-flex items-center gap-2 px-5 py-2.5 bg-white/10 hover:bg-white/15 text-white font-medium text-sm rounded-xl transition"
          href="/laptop"
        >
          <GoogleIcon name="arrow_back" size={16} />
          <span>Return to RailConnect</span>
        </Link>
      </main>
    );
  }

  return <TicketVerification token={token} />;
}
