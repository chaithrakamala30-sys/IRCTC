"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { ApiRequestError, apiFetch } from "@railconnect/api";

type Run = {
  trainRunId: string;
  trainId: string;
  trainNumber: string;
  trainName: string;
  serviceDate: string;
  status: string;
  stops: number;
  coaches: number;
  berths: number;
  initializedAt: string;
};
type TrainOption = { trainId: string; trainNumber: string; trainName: string };
type Page<T> = { items: T[]; page: number; size: number; total: number };
type Audit = {
  inventoryAuditId: string;
  eventType: string;
  actorName: string | null;
  occurredAt: string;
  details: string;
};

function message(error: unknown): string {
  return error instanceof ApiRequestError ? error.message : "Inventory data could not be loaded.";
}

export function AdminInventoryConsole() {
  const [runs, setRuns] = useState<Run[]>([]);
  const [trains, setTrains] = useState<TrainOption[]>([]);
  const [audit, setAudit] = useState<Audit[]>([]);
  const [selectedRunId, setSelectedRunId] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  const loadRuns = useCallback(async () => {
    const response = await apiFetch<Page<Run>>("/api/v1/admin/inventory/runs?page=0&size=100");
    const items = response.data?.items ?? [];
    setRuns(items);
    if (selectedRunId && !items.some((run) => run.trainRunId === selectedRunId)) {
      setSelectedRunId("");
    }
  }, [selectedRunId]);

  const loadTrains = useCallback(async () => {
    const response = await apiFetch<TrainOption[]>("/api/v1/admin/inventory/trains");
    setTrains(response.data ?? []);
  }, []);

  const loadAudit = useCallback(async (runId: string) => {
    const response = await apiFetch<Audit[]>(
      `/api/v1/admin/inventory/runs/${encodeURIComponent(runId)}/audit?limit=100`,
    );
    setAudit(response.data ?? []);
  }, []);

  useEffect(() => {
    void Promise.all([loadRuns(), loadTrains()]).catch((caught: unknown) => setError(message(caught)));
  }, [loadRuns, loadTrains]);

  useEffect(() => {
    if (selectedRunId) void loadAudit(selectedRunId).catch((caught: unknown) => setError(message(caught)));
    else setAudit([]);
  }, [loadAudit, selectedRunId]);

  async function initializeRun(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setError("");
    setNotice("");
    setBusy(true);
    try {
      const response = await apiFetch<Run>("/api/v1/admin/inventory/runs", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ trainId: form.get("trainId"), serviceDate: form.get("serviceDate") }),
      });
      await loadRuns();
      if (response.data) setSelectedRunId(response.data.trainRunId);
      setNotice("Dated inventory snapshot is ready.");
    } catch (caught) {
      setError(message(caught));
    } finally {
      setBusy(false);
    }
  }

  const selectedRun = runs.find((run) => run.trainRunId === selectedRunId);

  return (
    <section className="admin-console">
      <div className="admin-title-row">
        <div><p className="eyebrow">Train-run inventory</p><h1>Inventory administration</h1></div>
        <button className="button button--secondary" onClick={() => void loadRuns().catch((caught: unknown) => setError(message(caught)))} type="button">Refresh</button>
      </div>
      {error && <p className="admin-alert admin-alert--error" role="alert">{error}</p>}
      {notice && <p className="admin-alert admin-alert--success" role="status">{notice}</p>}
      <section className="admin-panel">
        <h2>Initialize inventory from published master data</h2>
        <p>Each run snapshots its published route, schedule, composition, and berth layout. Existing dated runs are immutable and returned as-is.</p>
        <form className="admin-form admin-form--inventory" onSubmit={(event) => void initializeRun(event)}>
          <label>Train<select name="trainId" required>
            <option value="">Select a configured train</option>
            {trains.map((train) => <option key={train.trainId} value={train.trainId}>{train.trainNumber} · {train.trainName}</option>)}
          </select></label>
          <label>Service date<input name="serviceDate" required type="date" /></label>
          <button className="button button--primary" disabled={busy} type="submit">{busy ? "Initializing…" : "Initialize dated run"}</button>
        </form>
      </section>
      <section className="admin-panel">
        <h2>Initialized train runs</h2>
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead><tr><th>Train</th><th>Service date</th><th>Snapshot</th><th>Action</th></tr></thead>
            <tbody>{runs.map((run) => (
              <tr key={run.trainRunId}>
                <td>{run.trainNumber} · {run.trainName}</td>
                <td>{run.serviceDate}</td>
                <td>{run.status} · {run.stops} stops · {run.coaches} coaches · {run.berths} berths</td>
                <td><button className="button button--secondary" onClick={() => setSelectedRunId(run.trainRunId)} type="button">Audit</button></td>
              </tr>
            ))}{runs.length === 0 && <tr><td colSpan={4}>No dated run inventory has been initialized.</td></tr>}</tbody>
          </table>
        </div>
      </section>
      {selectedRun && (
        <section className="admin-panel">
          <h2>Audit · {selectedRun.trainNumber} · {selectedRun.serviceDate}</h2>
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead><tr><th>Time</th><th>Event</th><th>Actor ID</th><th>Details</th></tr></thead>
              <tbody>{audit.map((entry) => (
                <tr key={entry.inventoryAuditId}>
                  <td>{new Date(entry.occurredAt).toLocaleString()}</td><td>{entry.eventType}</td>
                  <td>{entry.actorName ?? "System"}</td><td><code>{entry.details}</code></td>
                </tr>
              ))}{audit.length === 0 && <tr><td colSpan={4}>No inventory audit events.</td></tr>}</tbody>
            </table>
          </div>
        </section>
      )}
    </section>
  );
}
