"use client";

import { useState, type FormEvent } from "react";
import { ApiRequestError, apiFetch } from "@railconnect/api";

type Result = {
  verification: {
    valid: boolean;
    pnr: string | null;
    ticketStatus: string;
    trainNumber: string | null;
    trainName: string | null;
    journeyDate: string | null;
    boardingStation: string | null;
    destinationStation: string | null;
    passengerCount: number;
    notice: string;
  };
  allocations: { passengerNumber: number; coachNumber: string; berthNumber: string; berthType: string }[];
};

export function TicketVerificationConsole() {
  const [pnr, setPnr] = useState("");
  const [token, setToken] = useState("");
  const [result, setResult] = useState<Result | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function verify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setResult(null);
    try {
      const response = await apiFetch<Result>("/api/v1/ticket-verification/pnr", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ pnr: pnr.trim().toUpperCase() }),
      });
      setResult(response.data);
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "Ticket verification could not be completed.");
    } finally {
      setBusy(false);
    }
  }

  async function verifyQr(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setResult(null);
    try {
      const response = await apiFetch<Result>(
        `/api/v1/operations/tickets/verify/${encodeURIComponent(token.trim())}`,
      );
      setResult(response.data);
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "QR verification could not be completed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="ops-panel ticket-verify-panel" aria-labelledby="ops-ticket-verification">
      <p className="eyebrow">Limited operational access</p>
      <h2 id="ops-ticket-verification">Verify a RailConnect ticket</h2>
      <p>Displays journey and assigned berth information only. Passenger names and payment configuration are not shown.</p>
      <form className="ops-form ops-form--inline" onSubmit={(event) => void verify(event)}>
        <label>RailConnect PNR
          <input
            maxLength={24}
            onChange={(event) => setPnr(event.target.value)}
            pattern="RC-TK-[A-Z2-7]{16}"
            required
            value={pnr}
          />
        </label>
        <button className="ops-button ops-button--primary" disabled={busy} type="submit">
          {busy ? "Verifying…" : "Verify ticket"}
        </button>
      </form>
      <form className="ops-form ops-form--inline" onSubmit={(event) => void verifyQr(event)}>
        <label>Scanned QR token
          <input
            maxLength={200}
            onChange={(event) => setToken(event.target.value)}
            required
            value={token}
          />
        </label>
        <button className="ops-button ops-button--quiet" disabled={busy} type="submit">
          {busy ? "Verifying…" : "Verify QR token"}
        </button>
      </form>
      {error && <p className="ops-alert ops-alert--error" role="alert">{error}</p>}
      {result && (
        <div className={`ops-ticket-result ${result.verification.valid ? "is-valid" : "is-invalid"}`} role="status">
          <h3>{result.verification.valid ? "Valid RailConnect record" : "Invalid ticket"}</h3>
          {result.verification.valid && (
            <>
              <p>{result.verification.pnr} · {result.verification.ticketStatus}</p>
              <p>{result.verification.trainNumber} · {result.verification.trainName} · {result.verification.journeyDate}</p>
              <p>{result.verification.boardingStation} to {result.verification.destinationStation}</p>
              <ul>{result.allocations.map((allocation) => (
                <li key={allocation.passengerNumber}>
                  Passenger {allocation.passengerNumber}: {allocation.coachNumber} · berth {allocation.berthNumber} ({allocation.berthType})
                </li>
              ))}</ul>
            </>
          )}
          <p>{result.verification.notice}</p>
        </div>
      )}
    </section>
  );
}
