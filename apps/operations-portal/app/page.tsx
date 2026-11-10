"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { ApiRequestError, apiFetch } from "@railconnect/api";
import { Brand } from "@railconnect/ui";
import { TicketVerificationConsole } from "../components/ticket-verification-console";
import { OperationalChartConsole } from "../components/operational-chart-console";

type User = { userId: string; fullName: string; roles: string[]; mustChangePassword: boolean };
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
};
type TrainOption = { trainId: string; trainNumber: string; trainName: string };
type Page<T> = { items: T[]; page: number; size: number; total: number };
type Stop = { stopIndex: number; stationId: string; stationCode: string; stationName: string };
type RunDetails = { run: Run; stops: Stop[] };
type Berth = {
  inventoryBerthId: string;
  coachNumber: string;
  berthNumber: string;
  coachClassCode: string;
  berthType: string;
  status: string;
};
type Block = {
  inventoryBlockId: string;
  inventoryBerthId: string;
  coachNumber: string;
  berthNumber: string;
  blockType: string;
  reason: string;
  status: string;
};
type Audit = {
  inventoryAuditId: string;
  eventType: string;
  actorName: string | null;
  occurredAt: string;
  details: string;
};
type ReservationCount = { status: string; count: number };

function errorText(error: unknown): string {
  return error instanceof ApiRequestError ? error.message : "The request could not be completed.";
}

export default function OperationsHomePage() {
  const [user, setUser] = useState<User | null>(null);
  const [runs, setRuns] = useState<Run[]>([]);
  const [trains, setTrains] = useState<TrainOption[]>([]);
  const [selectedRun, setSelectedRun] = useState<RunDetails | null>(null);
  const [berths, setBerths] = useState<Berth[]>([]);
  const [blocks, setBlocks] = useState<Block[]>([]);
  const [audit, setAudit] = useState<Audit[]>([]);
  const [reservationCounts, setReservationCounts] = useState<ReservationCount[]>([]);
  const [reservationSummaryError, setReservationSummaryError] = useState("");
  const [fromStationId, setFromStationId] = useState("");
  const [toStationId, setToStationId] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [initializing, setInitializing] = useState(false);

  const loadRuns = useCallback(async () => {
    const response = await apiFetch<Page<Run>>("/api/v1/admin/inventory/runs?page=0&size=100");
    setRuns(response.data?.items ?? []);
  }, []);

  const loadTrains = useCallback(async () => {
    const response = await apiFetch<TrainOption[]>("/api/v1/admin/inventory/trains");
    setTrains(response.data ?? []);
  }, []);

  const loadRun = useCallback(async (runId: string) => {
    const [detailsResponse, blocksResponse, auditResponse] = await Promise.all([
      apiFetch<RunDetails>(`/api/v1/public/inventory/runs/${encodeURIComponent(runId)}`),
      apiFetch<Page<Block>>(`/api/v1/admin/inventory/runs/${encodeURIComponent(runId)}/blocks?page=0&size=100`),
      apiFetch<Audit[]>(`/api/v1/admin/inventory/runs/${encodeURIComponent(runId)}/audit?limit=100`),
    ]);
    setSelectedRun(detailsResponse.data);
    setBlocks(blocksResponse.data?.items ?? []);
    setAudit(auditResponse.data ?? []);
    const stops = detailsResponse.data?.stops ?? [];
    setFromStationId(stops[0]?.stationId ?? "");
    setToStationId(stops[1]?.stationId ?? "");
  }, []);

  const refreshBerths = useCallback(async () => {
    if (!selectedRun || !fromStationId || !toStationId) return;
    const query = new URLSearchParams({
      trainRunId: selectedRun.run.trainRunId,
      fromStationId,
      toStationId,
      page: "0",
      size: "100",
    });
    const response = await apiFetch<{ items: Berth[] }>(`/api/v1/public/inventory/berths?${query}`);
    setBerths(response.data?.items ?? []);
  }, [fromStationId, selectedRun, toStationId]);

  useEffect(() => {
    let active = true;
    void apiFetch<User>("/api/v1/auth/me")
      .then(async (response) => {
        if (!active) return;
        setUser(response.data);
        if (!response.data?.mustChangePassword
            && response.data?.roles.some((role) => ["OPERATIONS_MANAGER", "RAILWAY_ADMIN", "SUPER_ADMIN"].includes(role))) {
          await loadRuns();
          if (response.data.roles.some((role) => ["RAILWAY_ADMIN", "SUPER_ADMIN"].includes(role))) {
            await loadTrains();
          }
        }
      })
      .catch((caught: unknown) => {
        if (active) setError(errorText(caught));
      });
    return () => {
      active = false;
    };
  }, [loadRuns, loadTrains]);

  useEffect(() => {
    if (selectedRun) void refreshBerths().catch((caught: unknown) => setError(errorText(caught)));
  }, [refreshBerths, selectedRun]);

  useEffect(() => {
    if (!selectedRun || !user?.roles.some((role) => ["OPERATIONS_MANAGER", "SUPER_ADMIN"].includes(role))) {
      setReservationCounts([]);
      setReservationSummaryError("");
      return;
    }
    let active = true;
    setReservationCounts([]);
    setReservationSummaryError("");
    const query = new URLSearchParams({ trainRunId: selectedRun.run.trainRunId });
    void apiFetch<ReservationCount[]>(`/api/v1/operations/allocations/summary?${query}`)
      .then((response) => {
        if (active) setReservationCounts(response.data ?? []);
      })
      .catch((caught: unknown) => {
        if (active) setReservationSummaryError(errorText(caught));
      });
    return () => {
      active = false;
    };
  }, [selectedRun, user]);

  async function signIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setError("");
    setBusy(true);
    try {
      const response = await apiFetch<User>("/api/v1/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ identifier: form.get("identifier"), password: form.get("password") }),
      });
      setUser(response.data);
      if (response.data?.roles.some((role) => ["OPERATIONS_MANAGER", "RAILWAY_ADMIN", "SUPER_ADMIN"].includes(role))) {
        await loadRuns();
        if (response.data.roles.some((role) => ["RAILWAY_ADMIN", "SUPER_ADMIN"].includes(role))) {
          await loadTrains();
        }
      }
    } catch (caught) {
      setError(errorText(caught));
    } finally {
      setBusy(false);
    }
  }

  async function initializeRun(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setError("");
    setNotice("");
    setInitializing(true);
    try {
      const response = await apiFetch<Run>("/api/v1/admin/inventory/runs", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ trainId: form.get("trainId"), serviceDate: form.get("serviceDate") }),
      });
      await loadRuns();
      if (response.data) await loadRun(response.data.trainRunId);
      setNotice("Dated inventory snapshot is ready.");
    } catch (caught) {
      setError(errorText(caught));
    } finally {
      setInitializing(false);
    }
  }

  async function createBlock(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedRun) return;
    const form = new FormData(event.currentTarget);
    setError("");
    setNotice("");
    setBusy(true);
    try {
      await apiFetch<Block>(`/api/v1/admin/inventory/runs/${encodeURIComponent(selectedRun.run.trainRunId)}/blocks`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          inventoryBerthId: form.get("inventoryBerthId"),
          fromStationId,
          toStationId,
          blockType: form.get("blockType"),
          reason: form.get("reason"),
        }),
      });
      await loadRun(selectedRun.run.trainRunId);
      setNotice("Berth segment blocked and recorded in the audit trail.");
    } catch (caught) {
      setError(errorText(caught));
    } finally {
      setBusy(false);
    }
  }

  async function releaseBlock(blockId: string) {
    setError("");
    setNotice("");
    setBusy(true);
    try {
      await apiFetch<Block>(`/api/v1/admin/inventory/blocks/${encodeURIComponent(blockId)}`, { method: "DELETE" });
      if (selectedRun) await loadRun(selectedRun.run.trainRunId);
      setNotice("Operational block released.");
    } catch (caught) {
      setError(errorText(caught));
    } finally {
      setBusy(false);
    }
  }

  async function signOut() {
    setError("");
    try {
      await apiFetch("/api/v1/auth/logout", { method: "POST" });
      setUser(null);
      setRuns([]);
      setTrains([]);
      setSelectedRun(null);
    } catch (caught) {
      setError(errorText(caught));
    }
  }

  const roles = user?.roles ?? [];
  const canOperate = roles.some((role) => ["OPERATIONS_MANAGER", "RAILWAY_ADMIN", "SUPER_ADMIN"].includes(role));
  const canInitialize = roles.some((role) => ["RAILWAY_ADMIN", "SUPER_ADMIN"].includes(role));
  const selectedFromIndex = selectedRun?.stops.find((stop) => stop.stationId === fromStationId)?.stopIndex ?? -1;

  return (
    <div className="ops-shell">
      <header className="ops-header">
        <Brand href="/" />
        {user && (
          <div className="ops-user">
            <span>{user.fullName}</span>
            <button className="ops-button ops-button--quiet" onClick={() => void signOut()} type="button">Sign out</button>
          </div>
        )}
      </header>
      <main className="ops-main" id="main-content">
        <div className="ops-title">
          <p className="eyebrow">Railway operations</p>
          <h1>Train-run seat inventory</h1>
          <p>Manage dated inventory snapshots and operational berth blocks. Reservation data here is limited to aggregate allocation counts.</p>
        </div>
        {error && <p className="ops-alert ops-alert--error" role="alert">{error}</p>}
        {notice && <p className="ops-alert ops-alert--success" role="status">{notice}</p>}
        {!user && (
          <section className="ops-panel ops-login">
            <h2>Staff sign in</h2>
            <form className="ops-form" onSubmit={(event) => void signIn(event)}>
              <label>Work email or mobile<input autoComplete="username" name="identifier" required /></label>
              <label>Password<input autoComplete="current-password" name="password" required type="password" /></label>
              <button className="ops-button ops-button--primary" disabled={busy} type="submit">{busy ? "Signing in…" : "Sign in"}</button>
            </form>
          </section>
        )}
        {user && !canOperate && (
          <p className="ops-alert" role="status">Your account is authenticated but lacks an inventory operations role.</p>
        )}
        {user && canOperate && (
          <>
            <OperationalChartConsole />
            <TicketVerificationConsole />
            {canInitialize && (
              <section className="ops-panel">
                <h2>Initialize a dated train run</h2>
                <p>Uses the currently published schedule, route, composition, and physical layout. Existing run snapshots are returned unchanged.</p>
                <form className="ops-form ops-form--inline" onSubmit={(event) => void initializeRun(event)}>
                  <label>Train<select name="trainId" required>
                    <option value="">Select a configured train</option>
                    {trains.map((train) => <option key={train.trainId} value={train.trainId}>{train.trainNumber} · {train.trainName}</option>)}
                  </select></label>
                  <label>Service date<input name="serviceDate" required type="date" /></label>
                  <button className="ops-button ops-button--primary" disabled={initializing} type="submit">{initializing ? "Initializing…" : "Initialize run"}</button>
                </form>
              </section>
            )}
            <section className="ops-panel">
              <div className="ops-panel-heading">
                <div><h2>Inventory runs</h2><p>{runs.length} loaded run{runs.length === 1 ? "" : "s"}</p></div>
                <button className="ops-button ops-button--quiet" onClick={() => void loadRuns().catch((caught: unknown) => setError(errorText(caught)))} type="button">Refresh</button>
              </div>
              <div className="ops-table-wrap">
                <table className="ops-table">
                  <thead><tr><th>Train</th><th>Service date</th><th>Inventory</th><th>Action</th></tr></thead>
                  <tbody>{runs.map((run) => (
                    <tr key={run.trainRunId}>
                      <td><strong>{run.trainNumber}</strong><span>{run.trainName}</span></td>
                      <td>{run.serviceDate}</td>
                      <td>{run.coaches} coaches · {run.berths} berths · {run.stops} stops</td>
                      <td><button className="ops-button ops-button--quiet" onClick={() => void loadRun(run.trainRunId).catch((caught: unknown) => setError(errorText(caught)))} type="button">Manage</button></td>
                    </tr>
                  ))}{runs.length === 0 && <tr><td colSpan={4}>No dated inventory snapshots are initialized.</td></tr>}</tbody>
                </table>
              </div>
            </section>
            {selectedRun && (
              <>
                {user.roles.some((role) => ["OPERATIONS_MANAGER", "SUPER_ADMIN"].includes(role)) && (
                  <section className="ops-panel" aria-labelledby="reservation-summary-title">
                    <h2 id="reservation-summary-title">Reservation allocation counts</h2>
                    <p>Aggregate status totals only; passenger names and booking details are not exposed in operations.</p>
                    {reservationSummaryError && (
                      <p className="ops-alert ops-alert--error" role="alert">{reservationSummaryError}</p>
                    )}
                    {!reservationSummaryError && reservationCounts.length === 0 && (
                      <p>No reservation allocations are recorded for this run.</p>
                    )}
                    <div className="ops-count-grid">
                      {reservationCounts.map((item) => (
                        <div className="ops-count-card" key={item.status}>
                          <span>{item.status.replaceAll("_", " ")}</span><strong>{item.count}</strong>
                        </div>
                      ))}
                    </div>
                  </section>
                )}
                <section className="ops-panel">
                  <div className="ops-panel-heading">
                    <div><h2>{selectedRun.run.trainNumber} · {selectedRun.run.serviceDate}</h2><p>Selected run snapshot and journey segment controls</p></div>
                    <span className="ops-status">{selectedRun.run.status}</span>
                  </div>
                  <div className="ops-form ops-form--inline">
                    <label>From stop<select onChange={(event) => setFromStationId(event.target.value)} value={fromStationId}>
                      {selectedRun.stops.map((stop) => <option key={stop.stationId} value={stop.stationId}>{stop.stationCode} · {stop.stationName}</option>)}
                    </select></label>
                    <label>To stop<select onChange={(event) => setToStationId(event.target.value)} value={toStationId}>
                      {selectedRun.stops.map((stop) => <option disabled={stop.stopIndex <= selectedFromIndex} key={stop.stationId} value={stop.stationId}>{stop.stationCode} · {stop.stationName}</option>)}
                    </select></label>
                  </div>
                  <form className="ops-form ops-form--inline ops-block-form" onSubmit={(event) => void createBlock(event)}>
                    <label>Available berth<select name="inventoryBerthId" required>
                      {berths.map((berth) => <option key={berth.inventoryBerthId} value={berth.inventoryBerthId}>{berth.coachNumber} · {berth.berthNumber} · {berth.coachClassCode} · {berth.berthType}</option>)}
                    </select></label>
                    <label>Block type<select name="blockType"><option>BLOCKED</option><option>MAINTENANCE</option><option>UNAVAILABLE</option></select></label>
                    <label>Reason<input maxLength={500} name="reason" required /></label>
                    <button className="ops-button ops-button--primary" disabled={busy || berths.length === 0} type="submit">Block segment</button>
                  </form>
                  <div className="ops-table-wrap">
                    <table className="ops-table">
                      <thead><tr><th>Berth</th><th>Type</th><th>Reason</th><th>Status</th><th>Action</th></tr></thead>
                      <tbody>{blocks.map((block) => (
                        <tr key={block.inventoryBlockId}>
                          <td>{block.coachNumber} · {block.berthNumber}</td><td>{block.blockType}</td><td>{block.reason}</td><td>{block.status}</td>
                          <td>{block.status === "ACTIVE" && <button className="ops-button ops-button--quiet" disabled={busy} onClick={() => void releaseBlock(block.inventoryBlockId)} type="button">Release</button>}</td>
                        </tr>
                      ))}{blocks.length === 0 && <tr><td colSpan={5}>No operational blocks.</td></tr>}</tbody>
                    </table>
                  </div>
                </section>
                <section className="ops-panel">
                  <h2>Inventory audit events</h2>
                  <div className="ops-table-wrap">
                    <table className="ops-table">
                      <thead><tr><th>Time</th><th>Event</th><th>Actor ID</th><th>Details</th></tr></thead>
                      <tbody>{audit.map((entry) => (
                        <tr key={entry.inventoryAuditId}><td>{new Date(entry.occurredAt).toLocaleString()}</td><td>{entry.eventType}</td><td>{entry.actorName ?? "System"}</td><td><code>{entry.details}</code></td></tr>
                      ))}{audit.length === 0 && <tr><td colSpan={4}>No inventory audit events.</td></tr>}</tbody>
                    </table>
                  </div>
                </section>
              </>
            )}
          </>
        )}
      </main>
    </div>
  );
}
