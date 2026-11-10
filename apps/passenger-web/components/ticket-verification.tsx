"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ApiRequestError, apiFetch } from "../lib/api";

type Verification = {
  valid: boolean;
  pnr: string | null;
  ticketStatus: string;
  trainNumber: string | null;
  trainName: string | null;
  journeyDate: string | null;
  boardingStation: string | null;
  destinationStation: string | null;
  passengerCount: number;
  verifiedAt: string;
  notice: string;
};

export function TicketVerification({ token }: { token: string }) {
  const [result, setResult] = useState<Verification | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    void apiFetch<Verification>(`/api/v1/ticket-verification/${encodeURIComponent(token)}`)
      .then((response) => {
        if (active) setResult(response.data);
      })
      .catch((caught: unknown) => {
        if (!active) return;
        setError(caught instanceof ApiRequestError
          ? caught.message
          : "The RailConnect record could not be verified right now.");
      });
    return () => {
      active = false;
    };
  }, [token]);

  return (
    <main className="ticket-verify-shell">
      <header className="ticket-verify-header">
        <p className="eyebrow">Independent reservation platform</p>
        <h1>RailConnect ticket verification</h1>
      </header>
      {!result && !error && <p role="status">Checking the signed ticket record…</p>}
      {error && <p className="ticket-verify-result ticket-verify-result--invalid" role="alert">{error}</p>}
      {result && (
        <section
          className={`ticket-verify-result ${result.valid ? "ticket-verify-result--valid" : "ticket-verify-result--invalid"}`}
          aria-live="polite"
        >
          <p className="eyebrow">{result.valid ? "Valid RailConnect record" : "Invalid ticket record"}</p>
          <h2>{result.valid ? "Ticket verified" : "Verification failed"}</h2>
          {result.valid && (
            <dl className="ticket-verify-details">
              <div><dt>RailConnect PNR</dt><dd>{result.pnr}</dd></div>
              <div><dt>Train</dt><dd>{result.trainNumber} · {result.trainName}</dd></div>
              <div><dt>Journey date</dt><dd>{result.journeyDate}</dd></div>
              <div><dt>From</dt><dd>{result.boardingStation}</dd></div>
              <div><dt>To</dt><dd>{result.destinationStation}</dd></div>
              <div><dt>Ticket status</dt><dd>{result.ticketStatus}</dd></div>
              <div><dt>Passengers</dt><dd>{result.passengerCount}</dd></div>
              <div><dt>Verified</dt><dd>{new Date(result.verifiedAt).toLocaleString()}</dd></div>
            </dl>
          )}
          <p>{result.notice}</p>
        </section>
      )}
      <Link className="button button--secondary" href="/laptop">Return to RailConnect</Link>
    </main>
  );
}
