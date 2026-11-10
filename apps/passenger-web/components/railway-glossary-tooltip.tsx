"use client";

import { useState } from "react";

const RAILWAY_TERMS: { term: string; abbr: string; full: string; explanation: string }[] = [
  {
    term: "PNR",
    abbr: "PNR",
    full: "Passenger Name Record",
    explanation: "A unique 10-character booking identifier containing journey details and passenger status.",
  },
  {
    term: "CNF",
    abbr: "CNF",
    full: "Confirmed",
    explanation: "Guaranteed berth and coach assigned for the entire journey segment.",
  },
  {
    term: "RAC",
    abbr: "RAC",
    full: "Reservation Against Cancellation",
    explanation: "Permits boarding the train with guaranteed seating (shared side-lower berth), with automatic upgrade if berths cancel.",
  },
  {
    term: "WL",
    abbr: "WL",
    full: "Waiting List",
    explanation: "Waitlisted ticket. If not confirmed prior to chart preparation, fare is automatically refunded.",
  },
  {
    term: "Dynamic Fare",
    abbr: "Dynamic",
    full: "Demand-Based Pricing",
    explanation: "Real-time ticket price based on base distance fare plus dynamic occupancy multiplier.",
  },
  {
    term: "Tatkal",
    abbr: "Tatkal",
    full: "Emergency Quota",
    explanation: "Dedicated short-notice booking quota opening 24 hours prior to train departure.",
  },
];

export function RailwayGlossaryTooltip() {
  const [open, setOpen] = useState(false);

  return (
    <div className="rail-glossary-widget">
      <button
        aria-expanded={open}
        aria-haspopup="dialog"
        className="rail-glossary-toggle-btn"
        onClick={() => setOpen(!open)}
        type="button"
      >
        <svg aria-hidden="true" fill="none" height="16" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" width="16">
          <circle cx="12" cy="12" r="10" />
          <path d="M12 16v-4M12 8h.01" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <span>Railway Terms Guide</span>
      </button>

      {open && (
        <div
          aria-label="Railway Terminology Definitions"
          className="rail-glossary-popover"
          role="dialog"
        >
          <div className="rail-glossary-header">
            <h4>Common Railway Terms</h4>
            <button
              aria-label="Close glossary"
              className="rail-glossary-close"
              onClick={() => setOpen(false)}
              type="button"
            >
              ×
            </button>
          </div>
          <div className="rail-glossary-list">
            {RAILWAY_TERMS.map((item) => (
              <div className="rail-glossary-item" key={item.term}>
                <div className="rail-glossary-term-badge">
                  <strong>{item.abbr}</strong>
                  <span>{item.full}</span>
                </div>
                <p>{item.explanation}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

