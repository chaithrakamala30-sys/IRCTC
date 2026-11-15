"use client";

import { useState, type FormEvent } from "react";
import { ApiRequestError, apiFetch } from "@railconnect/api";
import { CameraQrScannerModal } from "./camera-qr-scanner-modal";

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
  const [showScanner, setShowScanner] = useState(false);

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

  async function executeTokenVerification(tokenToVerify: string) {
    const cleanToken = tokenToVerify.trim();
    if (!cleanToken) return;
    setBusy(true);
    setError("");
    setResult(null);
    try {
      const response = await apiFetch<Result>(
        `/api/v1/operations/tickets/verify/${encodeURIComponent(cleanToken)}`,
      );
      setResult(response.data);
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "QR verification could not be completed.");
    } finally {
      setBusy(false);
    }
  }

  async function verifyQr(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await executeTokenVerification(token);
  }

  function handleCameraScanResult(scannedData: string) {
    setShowScanner(false);
    // If the scanned data is a full URL containing ?token=..., extract token or PNR
    let extracted = scannedData;
    try {
      if (scannedData.startsWith("http://") || scannedData.startsWith("https://")) {
        const url = new URL(scannedData);
        const tokenParam = url.searchParams.get("token");
        const pnrParam = url.searchParams.get("pnr");
        if (tokenParam) extracted = tokenParam;
        else if (pnrParam) {
          setPnr(pnrParam);
          return;
        }
      }
    } catch {
      // not a url, use raw string
    }

    setToken(extracted);
    void executeTokenVerification(extracted);
  }

  return (
    <section className="ops-panel ticket-verify-panel" aria-labelledby="ops-ticket-verification">
      <p className="eyebrow">Limited operational access</p>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap", gap: "1rem" }}>
        <div>
          <h2 id="ops-ticket-verification">Verify a RailConnect ticket</h2>
          <p>Displays journey and assigned berth information only. Passenger names and payment configuration are not shown.</p>
        </div>
        <button
          type="button"
          onClick={() => setShowScanner(true)}
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: "0.5rem",
            padding: "0.65rem 1.25rem",
            background: "linear-gradient(135deg, #0284c7, #2563eb)",
            color: "#ffffff",
            border: "none",
            borderRadius: "10px",
            fontSize: "0.9rem",
            fontWeight: 700,
            cursor: "pointer",
            boxShadow: "0 4px 12px rgba(2, 132, 199, 0.35)",
            transition: "all 0.15s ease",
          }}
        >
          <span style={{ fontSize: "1.1rem" }}>📷</span>
          <span>Scan with Phone Camera</span>
        </button>
      </div>

      <form className="ops-form ops-form--inline" onSubmit={(event) => void verify(event)}>
        <label>RailConnect PNR
          <input
            maxLength={24}
            onChange={(event) => setPnr(event.target.value)}
            pattern="RC-TK-[A-Z2-7]{16}"
            placeholder="e.g. RC-TK-ABCD1234EFGH5678"
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
          <div style={{ display: "flex", gap: "0.5rem" }}>
            <input
              maxLength={200}
              onChange={(event) => setToken(event.target.value)}
              placeholder="Paste token or scan via camera..."
              required
              value={token}
              style={{ flex: 1 }}
            />
            <button
              type="button"
              onClick={() => setShowScanner(true)}
              style={{
                padding: "0.5rem 0.85rem",
                borderRadius: "8px",
                border: "1px solid #cbd5e1",
                background: "#f1f5f9",
                color: "#1e293b",
                cursor: "pointer",
                fontWeight: 600,
                fontSize: "0.85rem",
                display: "inline-flex",
                alignItems: "center",
                gap: "0.35rem",
                whiteSpace: "nowrap"
              }}
              title="Open camera to scan QR code"
            >
              <span>📷 Scan</span>
            </button>
          </div>
        </label>
        <button className="ops-button ops-button--quiet" disabled={busy} type="submit">
          {busy ? "Verifying…" : "Verify QR token"}
        </button>
      </form>

      {showScanner && (
        <CameraQrScannerModal
          onScan={handleCameraScanResult}
          onClose={() => setShowScanner(false)}
        />
      )}

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

