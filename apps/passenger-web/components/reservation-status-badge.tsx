"use client";

import { useEffect, useState } from "react";
import { ApiRequestError, apiFetch } from "../lib/api";

type PassengerStatusItem = {
  bookingPassengerId: string;
  passengerNumber: number;
  passengerName: string;
  reservationStatus: string;
  positionNumber: number;
  coachNumber: string | null;
  berthNumber: string | null;
  berthType: string | null;
};

type StatusHistoryItem = {
  previousState: string;
  newState: string;
  reason: string;
  details: string;
  occurredAt: string;
};

type ReservationStatusView = {
  bookingId: string;
  bookingReference: string;
  pnr: string | null;
  trainNumber: string;
  trainName: string;
  serviceDate: string;
  coachClassCode: string;
  quotaCode: string;
  overallStatus: string;
  passengers: PassengerStatusItem[];
  history: StatusHistoryItem[];
};

export function ReservationStatusBadge({ bookingId }: { bookingId: string }) {
  const [data, setData] = useState<ReservationStatusView | null>(null);
  const [loading, setLoading] = useState(false);
  const [showTimeline, setShowTimeline] = useState(false);

  useEffect(() => {
    let active = true;
    async function loadStatus() {
      setLoading(true);
      try {
        const res = await apiFetch<ReservationStatusView>(`/api/v1/bookings/${bookingId}/reservation-status`);
        if (active) setData(res.data);
      } catch {
        // Fallback silently if not available yet
      } finally {
        if (active) setLoading(false);
      }
    }
    void loadStatus();
    return () => { active = false; };
  }, [bookingId]);

  if (!data) return null;

  return (
    <div className="reservation-status-panel" style={{ margin: "1rem 0", padding: "1rem", borderRadius: "8px", background: "#f8fafc", border: "1px solid #e2e8f0" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.5rem" }}>
        <div>
          <span style={{ fontSize: "0.85rem", textTransform: "uppercase", color: "#64748b", fontWeight: 600 }}>Quota: {data.quotaCode}</span>
          <h4 style={{ margin: "0.25rem 0 0 0" }}>Live Reservation Allocation</h4>
        </div>
        <button
          className="text-button"
          onClick={() => setShowTimeline(!showTimeline)}
          type="button"
          style={{ fontSize: "0.85rem" }}
        >
          {showTimeline ? "Hide timeline" : "View history timeline"}
        </button>
      </div>

      <div style={{ display: "grid", gap: "0.5rem", marginTop: "0.5rem" }}>
        {data.passengers.map((pax) => {
          const isConfirmed = pax.reservationStatus === "CONFIRMED";
          const isRac = pax.reservationStatus.includes("RAC");
          const isWaitlist = pax.reservationStatus.includes("WAITLIST");

          const badgeColor = isConfirmed ? "#166534" : isRac ? "#854d0e" : "#991b1b";
          const badgeBg = isConfirmed ? "#dcfce7" : isRac ? "#fef9c3" : "#fee2e2";

          return (
            <div
              key={pax.bookingPassengerId}
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                padding: "0.5rem 0.75rem",
                borderRadius: "6px",
                background: "#ffffff",
                border: "1px solid #cbd5e1"
              }}
            >
              <div>
                <strong>{pax.passengerName}</strong>
                <span style={{ marginLeft: "0.5rem", fontSize: "0.85rem", color: "#475569" }}>
                  {isConfirmed && pax.coachNumber ? `${pax.coachNumber} · Berth ${pax.berthNumber} (${pax.berthType?.replaceAll("_", " ") ?? ""})` : ""}
                  {isRac ? `RAC Position: #${pax.positionNumber}` : ""}
                  {isWaitlist ? `Waitlist: WL #${pax.positionNumber}` : ""}
                </span>
              </div>
              <span
                style={{
                  display: "inline-block",
                  padding: "0.2rem 0.6rem",
                  borderRadius: "9999px",
                  fontSize: "0.75rem",
                  fontWeight: 700,
                  color: badgeColor,
                  background: badgeBg,
                  textTransform: "uppercase"
                }}
              >
                {pax.reservationStatus}
              </span>
            </div>
          );
        })}
      </div>

      {showTimeline && data.history.length > 0 && (
        <div style={{ marginTop: "1rem", borderTop: "1px solid #e2e8f0", paddingTop: "0.75rem" }}>
          <h5 style={{ margin: "0 0 0.5rem 0", fontSize: "0.85rem", color: "#334155" }}>Promotion &amp; Allocation Timeline</h5>
          <ul style={{ margin: 0, paddingLeft: "1.2rem", fontSize: "0.8rem", color: "#475569" }}>
            {data.history.map((h, i) => (
              <li key={i} style={{ marginBottom: "0.25rem" }}>
                <strong>{h.previousState} &rarr; {h.newState}</strong> ({h.reason}) - {h.details} &middot;{" "}
                <em>{new Date(h.occurredAt).toLocaleString()}</em>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
