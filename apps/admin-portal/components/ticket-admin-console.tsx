"use client";

import { useState, type FormEvent } from "react";
import { ApiRequestError, apiFetch } from "@railconnect/api";

type Summary = {
  ticketId: string;
  pnr: string;
  bookingReference: string;
  ticketStatus: string;
  trainNumber: string;
  trainName: string;
  journeyDate: string;
  boardingStation: string;
  destinationStation: string;
  passengerCount: number;
  issuedAt: string;
};
type Page = { items: Summary[]; page: number; size: number; total: number };
type Diagnostics = {
  pnr: string;
  issuanceStatus: string;
  attemptCount: number;
  lastFailureCode: string | null;
  issueHistory: { eventType: string; reasonCode: string; occurredAt: string }[];
};
type TicketDetails = {
  ticketId: string;
  pnr: string;
  bookingReference: string;
  ticketStatus: string;
  trainNumber: string;
  trainName: string;
  journeyDate: string;
  boardingStation: string;
  destinationStation: string;
  issuedAt: string;
  paymentReference: string | null;
  paymentStatus: string;
  passengers: {
    passengerNumber: number;
    passengerName: string;
    coachNumber: string;
    berthNumber: string;
    berthType: string;
  }[];
};
type IssuanceFailure = {
  bookingReference: string;
  trainNumber: string;
  journeyDate: string;
  jobStatus: string;
  attemptCount: number;
  lastFailureCode: string | null;
  updatedAt: string;
};

function errorText(error: unknown): string {
  return error instanceof ApiRequestError ? error.message : "The ticket search could not be completed.";
}

export function TicketAdminConsole() {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Summary[]>([]);
  const [diagnostics, setDiagnostics] = useState<Diagnostics | null>(null);
  const [selected, setSelected] = useState<TicketDetails | null>(null);
  const [failures, setFailures] = useState<IssuanceFailure[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setDiagnostics(null);
    try {
      const params = new URLSearchParams({ page: "0", size: "20" });
      if (query.trim()) params.set("query", query.trim());
      const response = await apiFetch<Page>(`/api/v1/admin/tickets?${params}`);
      setResults(response.data?.items ?? []);
    } catch (caught) {
      setError(errorText(caught));
    } finally {
      setBusy(false);
    }
  }

  async function loadDiagnostics(ticketId: string) {
    setError("");
    setDiagnostics(null);
    try {
      const response = await apiFetch<Diagnostics>(
        `/api/v1/admin/tickets/${encodeURIComponent(ticketId)}/diagnostics`,
      );
      setDiagnostics(response.data);
    } catch (caught) {
      setError(errorText(caught));
    }
  }

  async function loadTicket(ticketId: string) {
    setError("");
    setSelected(null);
    try {
      const response = await apiFetch<TicketDetails>(`/api/v1/admin/tickets/${encodeURIComponent(ticketId)}`);
      setSelected(response.data);
    } catch (caught) {
      setError(errorText(caught));
    }
  }

  async function loadFailures() {
    setError("");
    try {
      const response = await apiFetch<IssuanceFailure[]>("/api/v1/admin/tickets/issuance-failures");
      setFailures(response.data ?? []);
    } catch (caught) {
      setError(errorText(caught));
    }
  }

  async function retryIssuance(bookingReference: string) {
    setError("");
    try {
      await apiFetch<void>(
        `/api/v1/admin/tickets/issuance-failures/${encodeURIComponent(bookingReference)}/retry`,
        { method: "POST" },
      );
      await loadFailures();
    } catch (caught) {
      setError(errorText(caught));
    }
  }

  return (
    <section className="admin-panel ticket-admin-panel" aria-labelledby="ticket-admin-title">
      <p className="eyebrow">Permission-protected staff lookup</p>
      <h2 id="ticket-admin-title">RailConnect ticket management</h2>
      <p>Search by RailConnect PNR, ticket ID, or booking reference. All lookups are audited.</p>
      <form className="admin-filters" onSubmit={(event) => void search(event)}>
        <label>
          Ticket reference
          <input maxLength={80} onChange={(event) => setQuery(event.target.value)} value={query} />
        </label>
        <button className="button button--primary" disabled={busy} type="submit">
          {busy ? "Searching…" : "Search tickets"}
        </button>
      </form>
      <button className="button button--secondary" onClick={() => void loadFailures()} type="button">
        Review pending and failed issuance
      </button>
      {error && <p className="identity-alert identity-alert--error" role="alert">{error}</p>}
      {results.length > 0 && (
        <div className="ticket-admin-table-wrap">
          <table>
            <thead><tr><th>PNR</th><th>Booking</th><th>Journey</th><th>Status</th><th>Issue time</th><th>Actions</th></tr></thead>
            <tbody>{results.map((ticket) => (
              <tr key={ticket.ticketId}>
                <td>{ticket.pnr}</td>
                <td>{ticket.bookingReference}</td>
                <td>{ticket.trainNumber} · {ticket.journeyDate} · {ticket.boardingStation} to {ticket.destinationStation}</td>
                <td>{ticket.ticketStatus}</td>
                <td>{new Date(ticket.issuedAt).toLocaleString()}</td>
                <td>
                  <button className="text-button" onClick={() => void loadTicket(ticket.ticketId)} type="button">Details</button>
                  {" · "}
                  <button className="text-button" onClick={() => void loadDiagnostics(ticket.ticketId)} type="button">History</button>
                </td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
      {diagnostics && (
        <div className="ticket-admin-diagnostics">
          <h3>{diagnostics.pnr} · Issuance diagnostics</h3>
          <p>Job: {diagnostics.issuanceStatus} · Attempts: {diagnostics.attemptCount}</p>
          {diagnostics.lastFailureCode && <p>Last failure: {diagnostics.lastFailureCode}</p>}
          <ol>{diagnostics.issueHistory.map((entry, index) => (
            <li key={`${entry.eventType}-${entry.occurredAt}-${index}`}>
              {entry.eventType} · {entry.reasonCode} · {new Date(entry.occurredAt).toLocaleString()}
            </li>
          ))}</ol>
        </div>
      )}
      {selected && (
        <div className="ticket-admin-diagnostics">
          <h3>{selected.pnr} · {selected.trainNumber} {selected.trainName}</h3>
          <p>{selected.boardingStation} to {selected.destinationStation} · {selected.journeyDate}</p>
          <p>Booking {selected.bookingReference} · {selected.ticketStatus} · Payment {selected.paymentStatus}</p>
          {selected.paymentReference && <p>Payment reference: {selected.paymentReference}</p>}
          <ul>{selected.passengers.map((passenger) => (
            <li key={passenger.passengerNumber}>
              {passenger.passengerNumber}. {passenger.passengerName} · {passenger.coachNumber} · berth {passenger.berthNumber} ({passenger.berthType})
            </li>
          ))}</ul>
        </div>
      )}
      {failures.length > 0 && (
        <div className="ticket-admin-diagnostics">
          <h3>Pending and failed ticket issuance</h3>
          <ul>{failures.map((failure) => (
            <li key={`${failure.bookingReference}-${failure.updatedAt}`}>
              {failure.bookingReference} · Train {failure.trainNumber} · {failure.journeyDate} · {failure.jobStatus}
              {" · "}attempts {failure.attemptCount}
              {failure.lastFailureCode ? ` · ${failure.lastFailureCode}` : ""}
              {failure.jobStatus === "FAILED" && (
                <button
                  className="text-button"
                  onClick={() => void retryIssuance(failure.bookingReference)}
                  type="button"
                >
                  Retry issuance
                </button>
              )}
            </li>
          ))}</ul>
        </div>
      )}
      <p className="identity-note">RailConnect PNRs are not official Indian Railways or IRCTC PNRs.</p>
    </section>
  );
}
