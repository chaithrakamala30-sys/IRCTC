"use client";

import { useState } from "react";
import Link from "next/link";
import { TrainTrackingModal } from "./train-tracking-modal";
import { GlassModal } from "./glass-modal";
import { RailwayGlossaryTooltip } from "./railway-glossary-tooltip";

export function QuickServicesDock() {
  const [activeModal, setActiveModal] = useState<"pnr" | "schedule" | "coach" | null>(null);
  const [pnrInput, setPnrInput] = useState("");
  const [pnrResult, setPnrResult] = useState<string | null>(null);

  const handlePnrCheck = (e: React.FormEvent) => {
    e.preventDefault();
    if (!pnrInput.trim()) return;
    // Redirect to ticket verification route with token/reference
    window.location.href = `/verify-ticket`;
  };

  return (
    <div className="quick-services-dock">
      <div className="quick-services-grid">
        <button
          className="quick-service-pill"
          onClick={() => setActiveModal("pnr")}
          type="button"
        >
          <span className="quick-service-icon">🎫</span>
          <div className="quick-service-text">
            <strong>PNR Status</strong>
            <span>Check confirmation</span>
          </div>
        </button>

        <div className="quick-service-pill-wrapper">
          <TrainTrackingModal />
        </div>

        <Link className="quick-service-pill" href="/support">
          <span className="quick-service-icon">💬</span>
          <div className="quick-service-text">
            <strong>24x7 Helpdesk</strong>
            <span>Support & disputes</span>
          </div>
        </Link>

        <Link className="quick-service-pill" href="/notifications">
          <span className="quick-service-icon">🔔</span>
          <div className="quick-service-text">
            <strong>Live Alerts</strong>
            <span>Platform & delays</span>
          </div>
        </Link>
      </div>

      {/* PNR Quick Status Modal */}
      <GlassModal
        isOpen={activeModal === "pnr"}
        onClose={() => setActiveModal(null)}
        title="Check PNR Confirmation"
        subtitle="Verify real-time booking, berth allotment, and RAC/waiting list progression directly from the railway database."
      >
        <form onSubmit={handlePnrCheck} className="pnr-quick-form">
          <label className="pnr-input-label">
            Enter 10-Digit PNR / Booking Reference
            <input
              type="text"
              required
              maxLength={24}
              placeholder="e.g. RC-2026-X89K"
              value={pnrInput}
              onChange={(e) => setPnrInput(e.target.value)}
              className="pnr-text-input"
            />
          </label>
          <div className="pnr-modal-actions">
            <button type="submit" className="button button--primary" style={{ width: "100%" }}>
              Search Booking Record
            </button>
          </div>
          <p className="pnr-note">
            Authoritative ticket records are signed cryptographically and verify against the RailConnect PostgreSQL ledger.
          </p>
        </form>
      </GlassModal>
    </div>
  );
}

