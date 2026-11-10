"use client";

import { useEffect, useState } from "react";
import { ApiRequestError, apiFetch } from "@railconnect/api";

type ChartPassengerView = {
  chartPassengerId: string;
  pnr: string;
  passengerName: string;
  coachNumber: string | null;
  berthNumber: string | null;
  berthType: string | null;
  coachClassCode: string;
  reservationStatus: string;
  positionNumber: number;
  fromStationCode: string;
  toStationCode: string;
};

type ChartView = {
  chartId: string;
  trainRunId: string;
  trainNumber: string;
  trainName: string;
  serviceDate: string;
  chartStatus: string;
  totalPassengers: number;
  confirmedPassengers: number;
  racPassengers: number;
  waitlistedPassengers: number;
  currentVersion: number;
  preparedAt: string | null;
  finalizedAt: string | null;
  passengers?: ChartPassengerView[];
};

export function OperationalChartConsole() {
  const [charts, setCharts] = useState<ChartView[]>([]);
  const [selectedChart, setSelectedChart] = useState<ChartView | null>(null);
  const [trainRunIdInput, setTrainRunIdInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [actionBusy, setActionBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  // Correction Form State
  const [selectedPaxId, setSelectedPaxId] = useState("");
  const [newCoach, setNewCoach] = useState("");
  const [newBerth, setNewBerth] = useState("");
  const [correctionReason, setCorrectionReason] = useState("");

  async function loadCharts() {
    setLoading(true);
    try {
      const res = await apiFetch<ChartView[]>("/api/v1/operations/charts");
      setCharts(res.data ?? []);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Failed to load charts");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadCharts();
  }, []);

  async function inspectChart(chartId: string) {
    setActionBusy(true);
    setError("");
    setNotice("");
    try {
      const res = await apiFetch<ChartView>(`/api/v1/operations/charts/${chartId}`);
      setSelectedChart(res.data);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Failed to load chart details");
    } finally {
      setActionBusy(false);
    }
  }

  async function prepareNewChart(e: React.FormEvent) {
    e.preventDefault();
    if (!trainRunIdInput.trim()) return;
    setActionBusy(true);
    setError("");
    setNotice("");
    try {
      const res = await apiFetch<ChartView>(`/api/v1/operations/train-runs/${trainRunIdInput.trim()}/prepare-chart`, {
        method: "POST"
      });
      setNotice(`Departure chart prepared successfully (Version ${res.data?.currentVersion ?? 1}).`);
      await loadCharts();
      if (res.data) setSelectedChart(res.data);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Chart preparation failed");
    } finally {
      setActionBusy(false);
    }
  }

  async function finalizeCurrentChart() {
    if (!selectedChart) return;
    setActionBusy(true);
    setError("");
    setNotice("");
    try {
      const res = await apiFetch<ChartView>(`/api/v1/operations/charts/${selectedChart.chartId}/finalize`, {
        method: "POST"
      });
      setNotice(`Chart finalized and frozen at ${new Date().toLocaleTimeString()}.`);
      setSelectedChart(res.data);
      await loadCharts();
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Chart finalization failed");
    } finally {
      setActionBusy(false);
    }
  }

  async function submitCorrection(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedChart || !selectedPaxId || !newCoach.trim() || !newBerth.trim() || !correctionReason.trim()) return;
    setActionBusy(true);
    setError("");
    setNotice("");
    try {
      const res = await apiFetch<ChartView>(`/api/v1/operations/charts/${selectedChart.chartId}/corrections`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          chartPassengerId: selectedPaxId,
          newCoach: newCoach.trim(),
          newBerth: newBerth.trim(),
          reason: correctionReason.trim()
        })
      });
      setNotice(`Chart correction authorized. Version incremented to v${res.data?.currentVersion ?? 2}.`);
      setSelectedChart(res.data);
      setSelectedPaxId("");
      setNewCoach("");
      setNewBerth("");
      setCorrectionReason("");
      await loadCharts();
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Chart correction authorization failed");
    } finally {
      setActionBusy(false);
    }
  }

  return (
    <section className="admin-panel" aria-labelledby="chart-ops-title">
      <div className="admin-title-row">
        <div>
          <p className="eyebrow">Departure Management</p>
          <h2 id="chart-ops-title">Reservation Chart &amp; Manifest Operations</h2>
        </div>
      </div>

      {error && <p className="admin-alert admin-alert--error" role="alert">{error}</p>}
      {notice && <p className="admin-alert admin-alert--success" role="status">{notice}</p>}

      <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: "1.5rem", marginTop: "1rem" }}>
        <div>
          <h3>Departure Charts</h3>
          <form onSubmit={prepareNewChart} style={{ display: "flex", gap: "0.5rem", marginBottom: "1rem" }}>
            <input
              placeholder="Train Run UUID"
              value={trainRunIdInput}
              onChange={(e) => setTrainRunIdInput(e.target.value)}
              style={{ flex: 1, padding: "0.4rem 0.6rem", borderRadius: "4px", border: "1px solid #cbd5e1", fontSize: "0.85rem" }}
              required
            />
            <button className="button button--primary" type="submit" disabled={actionBusy}>Prepare</button>
          </form>

          {loading && <p>Loading charts…</p>}
          <ul style={{ listStyle: "none", padding: 0, margin: 0, display: "grid", gap: "0.5rem" }}>
            {charts.map((c) => (
              <li
                key={c.chartId}
                onClick={() => void inspectChart(c.chartId)}
                style={{
                  padding: "0.75rem",
                  background: selectedChart?.chartId === c.chartId ? "#e2e8f0" : "#f8fafc",
                  borderRadius: "6px",
                  border: "1px solid #cbd5e1",
                  cursor: "pointer"
                }}
              >
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <strong>{c.trainNumber} · {c.trainName}</strong>
                  <span style={{ fontSize: "0.75rem", fontWeight: 700, textTransform: "uppercase" }}>{c.chartStatus} (v{c.currentVersion})</span>
                </div>
                <div style={{ fontSize: "0.8rem", color: "#64748b", marginTop: "0.25rem" }}>
                  {c.serviceDate} &middot; Total: {c.totalPassengers} (CNF: {c.confirmedPassengers}, RAC: {c.racPassengers}, WL: {c.waitlistedPassengers})
                </div>
              </li>
            ))}
          </ul>
        </div>

        <div>
          {selectedChart ? (
            <div>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", borderBottom: "1px solid #e2e8f0", paddingBottom: "0.75rem" }}>
                <div>
                  <h3 style={{ margin: 0 }}>{selectedChart.trainNumber} - {selectedChart.trainName} (v{selectedChart.currentVersion})</h3>
                  <span style={{ fontSize: "0.85rem", color: "#64748b" }}>Date: {selectedChart.serviceDate} &middot; Status: <strong>{selectedChart.chartStatus}</strong></span>
                </div>
                {selectedChart.chartStatus !== "FINALIZED" && (
                  <button className="button button--primary" onClick={finalizeCurrentChart} disabled={actionBusy} type="button">
                    Finalize &amp; Freeze Chart
                  </button>
                )}
              </div>

              {/* Manifest Passengers Table */}
              <div style={{ marginTop: "1rem", maxHeight: "400px", overflowY: "auto" }}>
                <h4>Passenger Manifest ({selectedChart.passengers?.length ?? 0} seats)</h4>
                <table style={{ width: "100%", fontSize: "0.85rem", borderCollapse: "collapse" }}>
                  <thead>
                    <tr style={{ background: "#f1f5f9", textAlign: "left" }}>
                      <th style={{ padding: "0.4rem" }}>PNR</th>
                      <th style={{ padding: "0.4rem" }}>Passenger</th>
                      <th style={{ padding: "0.4rem" }}>Class</th>
                      <th style={{ padding: "0.4rem" }}>Status</th>
                      <th style={{ padding: "0.4rem" }}>Coach/Berth</th>
                      <th style={{ padding: "0.4rem" }}>From &rarr; To</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(selectedChart.passengers ?? []).map((p) => (
                      <tr key={p.chartPassengerId} style={{ borderBottom: "1px solid #f1f5f9" }}>
                        <td style={{ padding: "0.4rem" }}><code>{p.pnr}</code></td>
                        <td style={{ padding: "0.4rem" }}>{p.passengerName}</td>
                        <td style={{ padding: "0.4rem" }}>{p.coachClassCode}</td>
                        <td style={{ padding: "0.4rem" }}><strong>{p.reservationStatus}</strong> {p.positionNumber > 0 ? `#${p.positionNumber}` : ""}</td>
                        <td style={{ padding: "0.4rem" }}>{p.coachNumber ? `${p.coachNumber} / ${p.berthNumber} (${p.berthType ?? ""})` : "-"}</td>
                        <td style={{ padding: "0.4rem" }}>{p.fromStationCode} &rarr; {p.toStationCode}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Authorized Post-Chart Correction Workflow */}
              <div style={{ marginTop: "1.5rem", background: "#f8fafc", padding: "1rem", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
                <h4>Authorized Operational Correction</h4>
                <p style={{ fontSize: "0.8rem", color: "#64748b" }}>Reassign berths post-chart with full operational audit trail.</p>
                <form onSubmit={submitCorrection} style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr 2fr auto", gap: "0.5rem", alignItems: "end" }}>
                  <div>
                    <label style={{ fontSize: "0.75rem", display: "block" }}>Select Passenger</label>
                    <select
                      value={selectedPaxId}
                      onChange={(e) => setSelectedPaxId(e.target.value)}
                      style={{ width: "100%", padding: "0.35rem", fontSize: "0.85rem", borderRadius: "4px", border: "1px solid #cbd5e1" }}
                      required
                    >
                      <option value="">-- Choose passenger --</option>
                      {(selectedChart.passengers ?? []).map((p) => (
                        <option key={p.chartPassengerId} value={p.chartPassengerId}>
                          {p.passengerName} ({p.coachNumber ? `${p.coachNumber}-${p.berthNumber}` : p.reservationStatus})
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label style={{ fontSize: "0.75rem", display: "block" }}>New Coach</label>
                    <input
                      placeholder="e.g. B2"
                      value={newCoach}
                      onChange={(e) => setNewCoach(e.target.value)}
                      style={{ width: "100%", padding: "0.35rem", fontSize: "0.85rem", borderRadius: "4px", border: "1px solid #cbd5e1" }}
                      required
                    />
                  </div>
                  <div>
                    <label style={{ fontSize: "0.75rem", display: "block" }}>New Berth</label>
                    <input
                      placeholder="e.g. 21"
                      value={newBerth}
                      onChange={(e) => setNewBerth(e.target.value)}
                      style={{ width: "100%", padding: "0.35rem", fontSize: "0.85rem", borderRadius: "4px", border: "1px solid #cbd5e1" }}
                      required
                    />
                  </div>
                  <div>
                    <label style={{ fontSize: "0.75rem", display: "block" }}>Correction Reason</label>
                    <input
                      placeholder="e.g. Coach defect, VIP escort"
                      value={correctionReason}
                      onChange={(e) => setCorrectionReason(e.target.value)}
                      style={{ width: "100%", padding: "0.35rem", fontSize: "0.85rem", borderRadius: "4px", border: "1px solid #cbd5e1" }}
                      required
                    />
                  </div>
                  <button className="button button--secondary" type="submit" disabled={actionBusy}>Authorize</button>
                </form>
              </div>
            </div>
          ) : (
            <p style={{ color: "#64748b" }}>Select a departure chart on the left to inspect manifests, finalize charts, or authorize corrections.</p>
          )}
        </div>
      </div>
    </section>
  );
}

