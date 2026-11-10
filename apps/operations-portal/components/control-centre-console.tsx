"use client";

import { useEffect, useState } from "react";
import { ApiRequestError, apiFetch } from "@railconnect/api";

type DashboardSummary = {
  totalScheduledRuns: number;
  runningTrains: number;
  delayedTrains: number;
  haltedTrains: number;
  completedTrains: number;
  cancelledTrains: number;
  activeIncidents: number;
  platformConflicts: number;
  activeAnnouncements: AnnouncementView[];
  activeRuns: TrainRunView[];
};

type TrainRunView = {
  trainRunId: string;
  trainNumber: string;
  trainName: string;
  serviceDate: string;
  operationalStatus: string;
  currentStationCode: string | null;
  currentStationName: string | null;
  nextStationCode: string | null;
  nextStationName: string | null;
  lastReportedEvent: string | null;
  lastReportedAt: string | null;
  delayMinutes: number;
  routeProgressPercent: number;
  totalStops: number;
  completedStops: number;
};

type IncidentView = {
  incidentId: string;
  incidentNumber: string;
  title: string;
  description: string;
  category: string;
  priority: string;
  status: string;
  trainNumber: string | null;
  stationName: string | null;
  reportedByName: string;
  createdAt: string;
};

type AnnouncementView = {
  announcementId: string;
  title: string;
  content: string;
  announcementType: string;
  priority: string;
  targetScope: string;
  status: string;
  startsAt: string;
};

type PlatformView = {
  platformId: string;
  platformNumber: string;
  trackNumber: string;
  status: string;
};

export function ControlCentreConsole() {
  const [tab, setTab] = useState<"dashboard" | "tracking" | "platforms" | "incidents" | "announcements">("dashboard");
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [incidents, setIncidents] = useState<IncidentView[]>([]);
  const [loading, setLoading] = useState(false);
  const [actionBusy, setActionBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  // Station update form state
  const [updateRunId, setUpdateRunId] = useState("");
  const [updateStationId, setUpdateStationId] = useState("");
  const [updateStopIndex, setUpdateStopIndex] = useState(0);
  const [updateEventType, setUpdateEventType] = useState("ARRIVAL");
  const [updateScheduled, setUpdateScheduled] = useState("");
  const [updateActual, setUpdateActual] = useState("");
  const [updatePlatform, setUpdatePlatform] = useState("1");
  const [updateRemarks, setUpdateRemarks] = useState("");

  // Incident form state
  const [incTitle, setIncTitle] = useState("");
  const [incDesc, setIncDesc] = useState("");
  const [incCat, setIncCat] = useState("BREAKDOWN");
  const [incPriority, setIncPriority] = useState("MEDIUM");

  // Announcement form state
  const [annTitle, setAnnTitle] = useState("");
  const [annContent, setAnnContent] = useState("");
  const [annType, setAnnType] = useState("DELAY");
  const [annPriority, setAnnPriority] = useState("NORMAL");

  async function loadDashboard() {
    setLoading(true);
    try {
      const res = await apiFetch<DashboardSummary>("/api/v1/operations/dashboard");
      setSummary(res.data);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Failed to load operations dashboard");
    } finally {
      setLoading(false);
    }
  }

  async function loadIncidents() {
    try {
      const res = await apiFetch<IncidentView[]>("/api/v1/operations/incidents");
      setIncidents(res.data ?? []);
    } catch {
      // silently handle
    }
  }

  useEffect(() => {
    void loadDashboard();
    void loadIncidents();
  }, []);

  async function submitStationUpdate(e: React.FormEvent) {
    e.preventDefault();
    if (!updateRunId || !updateStationId || !updateScheduled || !updateActual) return;
    setActionBusy(true);
    setError("");
    setNotice("");
    try {
      await apiFetch("/api/v1/operations/station-updates", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          trainRunId: updateRunId.trim(),
          stationId: updateStationId.trim(),
          stopIndex: Number(updateStopIndex),
          eventType: updateEventType,
          scheduledTime: new Date(updateScheduled).toISOString(),
          reportedActualTime: new Date(updateActual).toISOString(),
          platformNumber: updatePlatform,
          haltDurationMinutes: 5,
          remarks: updateRemarks
        })
      });
      setNotice("Station arrival/departure update recorded and delay propagated.");
      await loadDashboard();
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Failed to record station update");
    } finally {
      setActionBusy(false);
    }
  }

  async function submitIncident(e: React.FormEvent) {
    e.preventDefault();
    if (!incTitle.trim() || !incDesc.trim()) return;
    setActionBusy(true);
    setError("");
    setNotice("");
    try {
      await apiFetch("/api/v1/operations/incidents", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title: incTitle.trim(),
          description: incDesc.trim(),
          category: incCat,
          priority: incPriority
        })
      });
      setNotice("Operational incident logged in Control Centre.");
      setIncTitle("");
      setIncDesc("");
      await loadIncidents();
      await loadDashboard();
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Failed to create incident");
    } finally {
      setActionBusy(false);
    }
  }

  async function submitAnnouncement(e: React.FormEvent) {
    e.preventDefault();
    if (!annTitle.trim() || !annContent.trim()) return;
    setActionBusy(true);
    setError("");
    setNotice("");
    try {
      await apiFetch("/api/v1/operations/announcements", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title: annTitle.trim(),
          content: annContent.trim(),
          announcementType: annType,
          priority: annPriority,
          targetScope: "ALL"
        })
      });
      setNotice("Operational announcement published to network.");
      setAnnTitle("");
      setAnnContent("");
      await loadDashboard();
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Failed to publish announcement");
    } finally {
      setActionBusy(false);
    }
  }

  return (
    <div style={{ background: "#0f172a", color: "#f8fafc", minHeight: "100vh", padding: "1.5rem" }}>
      {/* Control Centre Header */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", borderBottom: "1px solid #1e293b", paddingBottom: "1rem", marginBottom: "1.5rem" }}>
        <div>
          <span style={{ fontSize: "0.75rem", letterSpacing: "0.1em", textTransform: "uppercase", color: "#38bdf8", fontWeight: 700 }}>RailConnect Network Operations</span>
          <h1 style={{ margin: "0.25rem 0 0 0", fontSize: "1.75rem", fontWeight: 800 }}>Railway Control &amp; Monitoring Centre</h1>
        </div>
        <div style={{ display: "flex", gap: "0.5rem" }}>
          {(["dashboard", "tracking", "incidents", "announcements"] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              type="button"
              style={{
                padding: "0.5rem 1rem",
                borderRadius: "6px",
                border: "1px solid #334155",
                background: tab === t ? "#0284c7" : "#1e293b",
                color: "#f8fafc",
                fontWeight: 600,
                fontSize: "0.85rem",
                cursor: "pointer",
                textTransform: "capitalize"
              }}
            >
              {t}
            </button>
          ))}
          <button
            onClick={() => void loadDashboard()}
            type="button"
            style={{ padding: "0.5rem 1rem", borderRadius: "6px", border: "1px solid #334155", background: "#1e293b", color: "#94a3b8", cursor: "pointer" }}
          >
            Refresh
          </button>
        </div>
      </div>

      {error && <div style={{ background: "#7f1d1d", color: "#fecaca", padding: "0.75rem 1rem", borderRadius: "6px", marginBottom: "1rem" }}>{error}</div>}
      {notice && <div style={{ background: "#065f46", color: "#a7f3d0", padding: "0.75rem 1rem", borderRadius: "6px", marginBottom: "1rem" }}>{notice}</div>}

      {/* 1. Dashboard Tab */}
      {tab === "dashboard" && summary && (
        <div>
          {/* Telemetry Metric Cards */}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: "1rem", marginBottom: "1.5rem" }}>
            <div style={{ background: "#1e293b", padding: "1rem", borderRadius: "8px", border: "1px solid #334155" }}>
              <div style={{ fontSize: "0.8rem", color: "#94a3b8" }}>Active Scheduled Runs</div>
              <div style={{ fontSize: "1.75rem", fontWeight: 700, color: "#38bdf8" }}>{summary.totalScheduledRuns}</div>
            </div>
            <div style={{ background: "#1e293b", padding: "1rem", borderRadius: "8px", border: "1px solid #334155" }}>
              <div style={{ fontSize: "0.8rem", color: "#94a3b8" }}>Running On-Route</div>
              <div style={{ fontSize: "1.75rem", fontWeight: 700, color: "#4ade80" }}>{summary.runningTrains}</div>
            </div>
            <div style={{ background: "#1e293b", padding: "1rem", borderRadius: "8px", border: "1px solid #334155" }}>
              <div style={{ fontSize: "0.8rem", color: "#94a3b8" }}>Delayed (&gt;15 min)</div>
              <div style={{ fontSize: "1.75rem", fontWeight: 700, color: "#facc15" }}>{summary.delayedTrains}</div>
            </div>
            <div style={{ background: "#1e293b", padding: "1rem", borderRadius: "8px", border: "1px solid #334155" }}>
              <div style={{ fontSize: "0.8rem", color: "#94a3b8" }}>Halted Trains</div>
              <div style={{ fontSize: "1.75rem", fontWeight: 700, color: "#f87171" }}>{summary.haltedTrains}</div>
            </div>
            <div style={{ background: "#1e293b", padding: "1rem", borderRadius: "8px", border: "1px solid #334155" }}>
              <div style={{ fontSize: "0.8rem", color: "#94a3b8" }}>Active Incidents</div>
              <div style={{ fontSize: "1.75rem", fontWeight: 700, color: "#fb923c" }}>{summary.activeIncidents}</div>
            </div>
          </div>

          {/* Active Train Run Monitoring Table */}
          <div style={{ background: "#1e293b", borderRadius: "8px", border: "1px solid #334155", padding: "1.25rem" }}>
            <h3 style={{ margin: "0 0 1rem 0", fontSize: "1.1rem" }}>Day-of-Service Train Telemetry</h3>
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.85rem" }}>
                <thead>
                  <tr style={{ borderBottom: "1px solid #334155", color: "#94a3b8", textAlign: "left" }}>
                    <th style={{ padding: "0.6rem" }}>Train</th>
                    <th style={{ padding: "0.6rem" }}>Date</th>
                    <th style={{ padding: "0.6rem" }}>Status</th>
                    <th style={{ padding: "0.6rem" }}>Current Station</th>
                    <th style={{ padding: "0.6rem" }}>Next Station</th>
                    <th style={{ padding: "0.6rem" }}>Delay</th>
                    <th style={{ padding: "0.6rem" }}>Route Progress</th>
                    <th style={{ padding: "0.6rem" }}>Last Event</th>
                  </tr>
                </thead>
                <tbody>
                  {summary.activeRuns.map((r) => (
                    <tr key={r.trainRunId} style={{ borderBottom: "1px solid #334155" }}>
                      <td style={{ padding: "0.6rem" }}>
                        <strong>{r.trainNumber}</strong> · {r.trainName}
                      </td>
                      <td style={{ padding: "0.6rem" }}>{r.serviceDate}</td>
                      <td style={{ padding: "0.6rem" }}>
                        <span style={{
                          padding: "0.2rem 0.5rem", borderRadius: "4px", fontSize: "0.75rem", fontWeight: 700,
                          background: r.operationalStatus === "RUNNING" ? "#065f46" : r.operationalStatus === "DELAYED" ? "#854d0e" : "#334155",
                          color: "#f8fafc"
                        }}>
                          {r.operationalStatus}
                        </span>
                      </td>
                      <td style={{ padding: "0.6rem" }}>{r.currentStationName ?? "Origin Yard"}</td>
                      <td style={{ padding: "0.6rem" }}>{r.nextStationName ?? "Destination"}</td>
                      <td style={{ padding: "0.6rem", color: r.delayMinutes > 0 ? "#f87171" : "#4ade80", fontWeight: 600 }}>
                        {r.delayMinutes > 0 ? `+${r.delayMinutes} min` : "Right Time"}
                      </td>
                      <td style={{ padding: "0.6rem" }}>
                        <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                          <div style={{ width: "80px", height: "6px", background: "#334155", borderRadius: "3px", overflow: "hidden" }}>
                            <div style={{ width: `${r.routeProgressPercent}%`, height: "100%", background: "#38bdf8" }} />
                          </div>
                          <span>{r.routeProgressPercent.toFixed(0)}%</span>
                        </div>
                      </td>
                      <td style={{ padding: "0.6rem", color: "#94a3b8" }}>
                        {r.lastReportedEvent ? `${r.lastReportedEvent} @ ${r.lastReportedAt ? new Date(r.lastReportedAt).toLocaleTimeString() : ""}` : "Awaiting update"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* 2. Station Operational Updates Tab */}
      {tab === "tracking" && (
        <div style={{ background: "#1e293b", padding: "1.5rem", borderRadius: "8px", border: "1px solid #334155" }}>
          <h3 style={{ margin: "0 0 0.5rem 0" }}>Record Station Arrival / Departure</h3>
          <p style={{ fontSize: "0.85rem", color: "#94a3b8", marginBottom: "1.5rem" }}>
            Operational updates originate exclusively from authorized station personnel. No third-party GPS tracking is used.
          </p>

          <form onSubmit={submitStationUpdate} style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem" }}>
            <div>
              <label style={{ fontSize: "0.8rem", color: "#94a3b8", display: "block" }}>Train Run UUID</label>
              <input
                value={updateRunId}
                onChange={(e) => setUpdateRunId(e.target.value)}
                placeholder="Enter Train Run UUID"
                style={{ width: "100%", padding: "0.5rem", borderRadius: "4px", border: "1px solid #475569", background: "#0f172a", color: "#f8fafc" }}
                required
              />
            </div>
            <div>
              <label style={{ fontSize: "0.8rem", color: "#94a3b8", display: "block" }}>Station UUID</label>
              <input
                value={updateStationId}
                onChange={(e) => setUpdateStationId(e.target.value)}
                placeholder="Enter Station UUID"
                style={{ width: "100%", padding: "0.5rem", borderRadius: "4px", border: "1px solid #475569", background: "#0f172a", color: "#f8fafc" }}
                required
              />
            </div>
            <div>
              <label style={{ fontSize: "0.8rem", color: "#94a3b8", display: "block" }}>Stop Index</label>
              <input
                type="number"
                value={updateStopIndex}
                onChange={(e) => setUpdateStopIndex(Number(e.target.value))}
                style={{ width: "100%", padding: "0.5rem", borderRadius: "4px", border: "1px solid #475569", background: "#0f172a", color: "#f8fafc" }}
                required
              />
            </div>
            <div>
              <label style={{ fontSize: "0.8rem", color: "#94a3b8", display: "block" }}>Event Type</label>
              <select
                value={updateEventType}
                onChange={(e) => setUpdateEventType(e.target.value)}
                style={{ width: "100%", padding: "0.5rem", borderRadius: "4px", border: "1px solid #475569", background: "#0f172a", color: "#f8fafc" }}
              >
                <option value="ARRIVAL">ARRIVAL</option>
                <option value="DEPARTURE">DEPARTURE</option>
                <option value="PASSING">PASSING</option>
              </select>
            </div>
            <div>
              <label style={{ fontSize: "0.8rem", color: "#94a3b8", display: "block" }}>Scheduled Time</label>
              <input
                type="datetime-local"
                value={updateScheduled}
                onChange={(e) => setUpdateScheduled(e.target.value)}
                style={{ width: "100%", padding: "0.5rem", borderRadius: "4px", border: "1px solid #475569", background: "#0f172a", color: "#f8fafc" }}
                required
              />
            </div>
            <div>
              <label style={{ fontSize: "0.8rem", color: "#94a3b8", display: "block" }}>Reported Actual Time</label>
              <input
                type="datetime-local"
                value={updateActual}
                onChange={(e) => setUpdateActual(e.target.value)}
                style={{ width: "100%", padding: "0.5rem", borderRadius: "4px", border: "1px solid #475569", background: "#0f172a", color: "#f8fafc" }}
                required
              />
            </div>
            <div>
              <label style={{ fontSize: "0.8rem", color: "#94a3b8", display: "block" }}>Platform Number</label>
              <input
                value={updatePlatform}
                onChange={(e) => setUpdatePlatform(e.target.value)}
                style={{ width: "100%", padding: "0.5rem", borderRadius: "4px", border: "1px solid #475569", background: "#0f172a", color: "#f8fafc" }}
              />
            </div>
            <div>
              <label style={{ fontSize: "0.8rem", color: "#94a3b8", display: "block" }}>Operational Remarks</label>
              <input
                value={updateRemarks}
                onChange={(e) => setUpdateRemarks(e.target.value)}
                placeholder="e.g. Signal clearance delayed"
                style={{ width: "100%", padding: "0.5rem", borderRadius: "4px", border: "1px solid #475569", background: "#0f172a", color: "#f8fafc" }}
              />
            </div>
            <div style={{ gridColumn: "1 / -1", marginTop: "0.5rem" }}>
              <button
                type="submit"
                disabled={actionBusy}
                style={{ padding: "0.6rem 1.5rem", borderRadius: "6px", background: "#0284c7", color: "#fff", border: "none", fontWeight: 700, cursor: "pointer" }}
              >
                Record Station Event
              </button>
            </div>
          </form>
        </div>
      )}

      {/* 3. Incidents Tab */}
      {tab === "incidents" && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: "1.5rem" }}>
          <div style={{ background: "#1e293b", padding: "1.25rem", borderRadius: "8px", border: "1px solid #334155" }}>
            <h3 style={{ margin: "0 0 1rem 0" }}>Report Operational Incident</h3>
            <form onSubmit={submitIncident} style={{ display: "grid", gap: "0.75rem" }}>
              <div>
                <label style={{ fontSize: "0.8rem", color: "#94a3b8", display: "block" }}>Title</label>
                <input
                  value={incTitle}
                  onChange={(e) => setIncTitle(e.target.value)}
                  style={{ width: "100%", padding: "0.4rem", borderRadius: "4px", border: "1px solid #475569", background: "#0f172a", color: "#f8fafc" }}
                  required
                />
              </div>
              <div>
                <label style={{ fontSize: "0.8rem", color: "#94a3b8", display: "block" }}>Category</label>
                <select
                  value={incCat}
                  onChange={(e) => setIncCat(e.target.value)}
                  style={{ width: "100%", padding: "0.4rem", borderRadius: "4px", border: "1px solid #475569", background: "#0f172a", color: "#f8fafc" }}
                >
                  <option value="BREAKDOWN">BREAKDOWN</option>
                  <option value="TECHNICAL_ISSUE">TECHNICAL_ISSUE</option>
                  <option value="SAFETY_INCIDENT">SAFETY_INCIDENT</option>
                  <option value="ROUTE_DISRUPTION">ROUTE_DISRUPTION</option>
                  <option value="PLATFORM_CONFLICT">PLATFORM_CONFLICT</option>
                  <option value="SERVICE_DELAY">SERVICE_DELAY</option>
                </select>
              </div>
              <div>
                <label style={{ fontSize: "0.8rem", color: "#94a3b8", display: "block" }}>Priority</label>
                <select
                  value={incPriority}
                  onChange={(e) => setIncPriority(e.target.value)}
                  style={{ width: "100%", padding: "0.4rem", borderRadius: "4px", border: "1px solid #475569", background: "#0f172a", color: "#f8fafc" }}
                >
                  <option value="LOW">LOW</option>
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="HIGH">HIGH</option>
                  <option value="CRITICAL">CRITICAL</option>
                </select>
              </div>
              <div>
                <label style={{ fontSize: "0.8rem", color: "#94a3b8", display: "block" }}>Description</label>
                <textarea
                  value={incDesc}
                  onChange={(e) => setIncDesc(e.target.value)}
                  rows={3}
                  style={{ width: "100%", padding: "0.4rem", borderRadius: "4px", border: "1px solid #475569", background: "#0f172a", color: "#f8fafc" }}
                  required
                />
              </div>
              <button type="submit" disabled={actionBusy} style={{ padding: "0.5rem", borderRadius: "4px", background: "#ea580c", color: "#fff", border: "none", fontWeight: 700, cursor: "pointer" }}>
                Submit Incident
              </button>
            </form>
          </div>

          <div style={{ background: "#1e293b", padding: "1.25rem", borderRadius: "8px", border: "1px solid #334155" }}>
            <h3 style={{ margin: "0 0 1rem 0" }}>Active Control Centre Incidents ({incidents.length})</h3>
            <ul style={{ listStyle: "none", padding: 0, margin: 0, display: "grid", gap: "0.75rem" }}>
              {incidents.map((inc) => (
                <li key={inc.incidentId} style={{ padding: "0.75rem", background: "#0f172a", borderRadius: "6px", border: "1px solid #334155" }}>
                  <div style={{ display: "flex", justifyContent: "space-between" }}>
                    <strong>{inc.incidentNumber}: {inc.title}</strong>
                    <span style={{
                      padding: "0.1rem 0.4rem", borderRadius: "4px", fontSize: "0.75rem", fontWeight: 700,
                      background: inc.priority === "CRITICAL" ? "#991b1b" : inc.priority === "HIGH" ? "#c2410c" : "#334155"
                    }}>
                      {inc.priority} · {inc.status}
                    </span>
                  </div>
                  <p style={{ margin: "0.4rem 0", fontSize: "0.85rem", color: "#cbd5e1" }}>{inc.description}</p>
                  <div style={{ fontSize: "0.75rem", color: "#94a3b8" }}>
                    Category: {inc.category} &middot; Reported by: {inc.reportedByName} &middot; {new Date(inc.createdAt).toLocaleString()}
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}

      {/* 4. Announcements Tab */}
      {tab === "announcements" && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: "1.5rem" }}>
          <div style={{ background: "#1e293b", padding: "1.25rem", borderRadius: "8px", border: "1px solid #334155" }}>
            <h3 style={{ margin: "0 0 1rem 0" }}>Publish Operational Bulletin</h3>
            <form onSubmit={submitAnnouncement} style={{ display: "grid", gap: "0.75rem" }}>
              <div>
                <label style={{ fontSize: "0.8rem", color: "#94a3b8", display: "block" }}>Headline</label>
                <input
                  value={annTitle}
                  onChange={(e) => setAnnTitle(e.target.value)}
                  style={{ width: "100%", padding: "0.4rem", borderRadius: "4px", border: "1px solid #475569", background: "#0f172a", color: "#f8fafc" }}
                  required
                />
              </div>
              <div>
                <label style={{ fontSize: "0.8rem", color: "#94a3b8", display: "block" }}>Announcement Type</label>
                <select
                  value={annType}
                  onChange={(e) => setAnnType(e.target.value)}
                  style={{ width: "100%", padding: "0.4rem", borderRadius: "4px", border: "1px solid #475569", background: "#0f172a", color: "#f8fafc" }}
                >
                  <option value="DELAY">DELAY</option>
                  <option value="PLATFORM_CHANGE">PLATFORM_CHANGE</option>
                  <option value="DIVERSION">DIVERSION</option>
                  <option value="CANCELLATION">CANCELLATION</option>
                  <option value="GENERAL">GENERAL</option>
                </select>
              </div>
              <div>
                <label style={{ fontSize: "0.8rem", color: "#94a3b8", display: "block" }}>Message Content</label>
                <textarea
                  value={annContent}
                  onChange={(e) => setAnnContent(e.target.value)}
                  rows={4}
                  style={{ width: "100%", padding: "0.4rem", borderRadius: "4px", border: "1px solid #475569", background: "#0f172a", color: "#f8fafc" }}
                  required
                />
              </div>
              <button type="submit" disabled={actionBusy} style={{ padding: "0.5rem", borderRadius: "4px", background: "#0284c7", color: "#fff", border: "none", fontWeight: 700, cursor: "pointer" }}>
                Publish Broadcast
              </button>
            </form>
          </div>

          <div style={{ background: "#1e293b", padding: "1.25rem", borderRadius: "8px", border: "1px solid #334155" }}>
            <h3 style={{ margin: "0 0 1rem 0" }}>Active Passenger &amp; Station Bulletins</h3>
            <ul style={{ listStyle: "none", padding: 0, margin: 0, display: "grid", gap: "0.75rem" }}>
              {summary?.activeAnnouncements.map((ann) => (
                <li key={ann.announcementId} style={{ padding: "0.75rem", background: "#0f172a", borderRadius: "6px", border: "1px solid #334155" }}>
                  <div style={{ display: "flex", justifyContent: "space-between" }}>
                    <strong>{ann.title}</strong>
                    <span style={{ fontSize: "0.75rem", color: "#38bdf8", fontWeight: 700 }}>{ann.announcementType}</span>
                  </div>
                  <p style={{ margin: "0.4rem 0", fontSize: "0.85rem", color: "#cbd5e1" }}>{ann.content}</p>
                  <div style={{ fontSize: "0.75rem", color: "#94a3b8" }}>
                    Scope: {ann.targetScope} &middot; Broadcasted at: {new Date(ann.startsAt).toLocaleString()}
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}
    </div>
  );
}
