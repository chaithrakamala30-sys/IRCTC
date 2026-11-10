"use client";

import { useState } from "react";
import { ApiRequestError, apiFetch } from "../lib/api";
import { GlassModal } from "./glass-modal";

type TrackingStopItem = {
  stopIndex: number;
  stationCode: string;
  stationName: string;
  scheduledArrival: string;
  scheduledDeparture: string;
  actualArrival: string;
  actualDeparture: string;
  delayMinutes: number;
  platformNumber: string;
  stopStatus: string;
};

type AnnouncementView = {
  announcementId: string;
  title: string;
  content: string;
  announcementType: string;
  priority: string;
};

type PublicTrainTrackingView = {
  trainNumber: string;
  trainName: string;
  serviceDate: string;
  operationalStatus: string;
  currentDelayMinutes: number;
  routeProgressPercent: number;
  currentStationCode: string | null;
  currentStationName: string | null;
  nextStationCode: string | null;
  nextStationName: string | null;
  lastReportedAt: string | null;
  dataReliabilityStatus: string;
  stops: TrackingStopItem[];
  relevantAnnouncements: AnnouncementView[];
};

export function TrainTrackingModal() {
  const [isOpen, setIsOpen] = useState(false);
  const [trainNumber, setTrainNumber] = useState("");
  const [trackingData, setTrackingData] = useState<PublicTrainTrackingView | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    if (!trainNumber.trim()) return;
    setLoading(true);
    setError("");
    try {
      const res = await apiFetch<PublicTrainTrackingView>(`/api/v1/tracking/trains/${encodeURIComponent(trainNumber.trim())}`);
      setTrackingData(res.data);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Train tracking information not found");
      setTrackingData(null);
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <button
        onClick={() => setIsOpen(true)}
        className="button button--secondary"
        type="button"
        style={{ display: "inline-flex", alignItems: "center", gap: "0.5rem" }}
      >
        <span>Live Train Tracking</span>
      </button>

      <GlassModal
        isOpen={isOpen}
        onClose={() => setIsOpen(false)}
        title="Live Train Tracking &amp; Operational Status"
        maxWidth="740px"
      >
        <div>
          <form onSubmit={handleSearch} style={{ display: "flex", gap: "0.5rem", marginBottom: "1rem" }}>
            <input
              placeholder="Enter 5-digit Train Number (e.g. 12301)"
              value={trainNumber}
              onChange={(e) => setTrainNumber(e.target.value)}
              style={{ flex: 1, padding: "0.5rem 0.75rem", borderRadius: "6px", border: "1px solid #cbd5e1" }}
              required
            />
            <button className="button button--primary" type="submit" disabled={loading}>
              {loading ? "Tracking…" : "Track Train"}
            </button>
          </form>

          {error && <p className="alert alert--error" style={{ color: "#b91c1c" }}>{error}</p>}

          {trackingData && (
            <div>
              {/* Header Badge */}
              <div style={{ background: "#f8fafc", padding: "1rem", borderRadius: "8px", border: "1px solid #e2e8f0", marginBottom: "1rem" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <div>
                    <h3 style={{ margin: 0 }}>{trackingData.trainNumber} - {trackingData.trainName}</h3>
                    <span style={{ fontSize: "0.85rem", color: "#64748b" }}>Date: {trackingData.serviceDate}</span>
                  </div>
                  <span
                    style={{
                      padding: "0.3rem 0.8rem",
                      borderRadius: "9999px",
                      fontWeight: 700,
                      fontSize: "0.85rem",
                      background: trackingData.currentDelayMinutes > 0 ? "#fef2f2" : "#f0fdf4",
                      color: trackingData.currentDelayMinutes > 0 ? "#b91c1c" : "#166534",
                      border: `1px solid ${trackingData.currentDelayMinutes > 0 ? "#fca5a5" : "#bbf7d0"}`
                    }}
                  >
                    {trackingData.currentDelayMinutes > 0 ? `Delayed by ${trackingData.currentDelayMinutes} min` : "Right Time"}
                  </span>
                </div>

                <div style={{ display: "flex", gap: "1.5rem", marginTop: "0.75rem", fontSize: "0.85rem", color: "#334155" }}>
                  <div><strong>Current:</strong> {trackingData.currentStationName ?? "Origin Station"}</div>
                  <div><strong>Next:</strong> {trackingData.nextStationName ?? "Destination Station"}</div>
                  <div><strong>Progress:</strong> {trackingData.routeProgressPercent.toFixed(0)}%</div>
                </div>

                {/* Reliability Banner */}
                <div style={{ marginTop: "0.75rem", padding: "0.4rem 0.6rem", background: "#f1f5f9", borderRadius: "4px", fontSize: "0.75rem", color: "#475569" }}>
                  Status: <strong>{trackingData.dataReliabilityStatus}</strong> &middot; Last staff update:{" "}
                  {trackingData.lastReportedAt ? new Date(trackingData.lastReportedAt).toLocaleTimeString() : "Scheduled timetable"}
                  <div style={{ fontSize: "0.7rem", color: "#64748b", marginTop: "2px" }}>
                    * Updates are authorized reports from railway stations and control centres (no synthetic or third-party GPS tracking).
                  </div>
                </div>
              </div>

              {/* Station Route Timeline */}
              <div style={{ maxHeight: "300px", overflowY: "auto", border: "1px solid #e2e8f0", borderRadius: "8px" }}>
                <table style={{ width: "100%", fontSize: "0.85rem", borderCollapse: "collapse" }}>
                  <thead>
                    <tr style={{ background: "#f8fafc", textAlign: "left", borderBottom: "1px solid #e2e8f0" }}>
                      <th style={{ padding: "0.5rem" }}>Station</th>
                      <th style={{ padding: "0.5rem" }}>Sched Arr / Dep</th>
                      <th style={{ padding: "0.5rem" }}>Actual / Rep</th>
                      <th style={{ padding: "0.5rem" }}>Delay</th>
                      <th style={{ padding: "0.5rem" }}>Platform</th>
                    </tr>
                  </thead>
                  <tbody>
                    {trackingData.stops.map((stop) => (
                      <tr key={stop.stopIndex} style={{ borderBottom: "1px solid #f1f5f9" }}>
                        <td style={{ padding: "0.5rem" }}>
                          <strong>{stop.stationCode}</strong> - {stop.stationName}
                        </td>
                        <td style={{ padding: "0.5rem" }}>{stop.scheduledArrival} / {stop.scheduledDeparture}</td>
                        <td style={{ padding: "0.5rem" }}>{stop.actualArrival} / {stop.actualDeparture}</td>
                        <td style={{ padding: "0.5rem", color: stop.delayMinutes > 0 ? "#b91c1c" : "#166534" }}>
                          {stop.delayMinutes > 0 ? `+${stop.delayMinutes}m` : "On time"}
                        </td>
                        <td style={{ padding: "0.5rem" }}>PF {stop.platformNumber}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      </GlassModal>
    </>
  );
}
