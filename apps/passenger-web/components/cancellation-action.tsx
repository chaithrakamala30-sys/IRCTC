"use client";

import { useEffect, useState } from "react";
import { ApiRequestError, apiFetch } from "../lib/api";

type DeviceExperience = "mobile" | "laptop";

type Eligibility = {
  bookingId: string;
  bookingReference: string;
  pnr: string;
  eligible: boolean;
  ineligibilityReason: string | null;
  bookingStatus: string;
  ticketStatus: string;
  journeyDate: string;
  trainNumber: string;
  trainName: string;
  departureTime: string;
  hoursBeforeDeparture: number;
  totalPassengers: number;
  totalFarePaid: number;
  currency: string;
  passengers: {
    bookingPassengerId: string;
    ticketPassengerId: string;
    passengerNumber: number;
    passengerName: string;
    coachNumber: string;
    berthNumber: string;
    berthType: string;
    farePaid: number;
    eligible: boolean;
  }[];
};

type Quote = {
  bookingId: string;
  bookingReference: string;
  pnr: string;
  policyVersionNumber: number;
  policyName: string;
  appliedTierCode: string;
  hoursBeforeDeparture: number;
  originalFare: number;
  cancellationFee: number;
  refundableAmount: number;
  currency: string;
  passengers: {
    bookingPassengerId: string;
    passengerName: string;
    coachNumber: string;
    berthNumber: string;
    farePaid: number;
    cancellationFee: number;
    refundableAmount: number;
    appliedTierCode: string;
  }[];
};

type Receipt = {
  cancellationId: string;
  cancellationReference: string;
  bookingReference: string;
  pnr: string;
  cancellationType: string;
  cancellationStatus: string;
  hoursBeforeDeparture: number;
  totalOriginalAmount: number;
  totalCancellationFee: number;
  totalRefundableAmount: number;
  currency: string;
  cancelledAt: string;
  refundSummary: {
    refundOrderId: string;
    merchantRefundRef: string;
    amount: number;
    currency: string;
    refundStatus: string;
    verified: boolean;
  } | null;
  cancelledPassengers: {
    bookingPassengerId: string;
    passengerName: string;
    coachNumber: string;
    berthNumber: string;
    farePaid: number;
    cancellationFee: number;
    refundableAmount: number;
  }[];
};

export function CancellationAction({
  bookingId,
  device,
  onCancelled,
}: {
  bookingId: string;
  device: DeviceExperience;
  onCancelled?: () => void;
}) {
  const [eligibility, setEligibility] = useState<Eligibility | null>(null);
  const [quote, setQuote] = useState<Quote | null>(null);
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [showModal, setShowModal] = useState(false);
  const [reason, setReason] = useState("");

  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const res = await apiFetch<Eligibility>(`/api/v1/bookings/${encodeURIComponent(bookingId)}/cancellation-eligibility`);
        if (active) setEligibility(res.data);
      } catch {
        // Ignored if not eligible
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [bookingId]);

  async function openCancellationFlow() {
    setError("");
    setLoading(true);
    try {
      const res = await apiFetch<Quote>(`/api/v1/bookings/${encodeURIComponent(bookingId)}/cancellation-quote`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({}),
      });
      setQuote(res.data);
      setShowModal(true);
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "Could not calculate cancellation charge");
    } finally {
      setLoading(false);
    }
  }

  async function confirmCancellation() {
    setError("");
    setLoading(true);
    try {
      const res = await apiFetch<Receipt>(`/api/v1/bookings/${encodeURIComponent(bookingId)}/cancel`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          reason: reason.trim() || "Passenger requested cancellation",
          idempotencyKey: `can_cli_${bookingId}_${Date.now()}`,
        }),
      });
      setReceipt(res.data);
      onCancelled?.();
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "Cancellation failed");
    } finally {
      setLoading(false);
    }
  }

  if (!eligibility || !eligibility.eligible) {
    return null;
  }

  if (receipt) {
    return (
      <section className="cancellation-receipt-card" aria-label="Cancellation Receipt">
        <p className="eyebrow">Cancellation confirmed</p>
        <h4>Reference: {receipt.cancellationReference}</h4>
        <p>PNR: <strong>{receipt.pnr}</strong> · Booking: {receipt.bookingReference}</p>
        <div className="cancellation-breakdown">
          <div><span>Original Fare</span><strong>₹{receipt.totalOriginalAmount.toFixed(2)}</strong></div>
          <div><span>Cancellation Fee</span><strong>- ₹{receipt.totalCancellationFee.toFixed(2)}</strong></div>
          <div className="total"><span>Net Refundable</span><strong>₹{receipt.totalRefundableAmount.toFixed(2)}</strong></div>
        </div>
        {receipt.refundSummary && (
          <p className="refund-status-pill">
            Refund Status: <strong>{receipt.refundSummary.refundStatus}</strong> (Ref: {receipt.refundSummary.merchantRefundRef})
          </p>
        )}
      </section>
    );
  }

  return (
    <div className={`cancellation-container cancellation-container--${device}`}>
      {error && <p className="identity-alert identity-alert--error">{error}</p>}
      <button
        className="button button--secondary button--danger"
        disabled={loading}
        onClick={() => void openCancellationFlow()}
        type="button"
      >
        {loading ? "Checking rules…" : "Cancel ticket & request refund"}
      </button>

      {showModal && quote && (
        <div className="cancellation-modal-backdrop" role="presentation">
          <div className="cancellation-modal-card" role="dialog" aria-modal="true">
            <p className="eyebrow">Review cancellation charge</p>
            <h3>Cancel ticket for {quote.bookingReference}?</h3>
            <p>Policy: <strong>{quote.policyName}</strong> ({quote.hoursBeforeDeparture.toFixed(1)} hrs before departure)</p>

            <div className="cancellation-breakdown">
              <div><span>Total fare paid</span><span>₹{quote.originalFare.toFixed(2)}</span></div>
              <div><span>RailConnect cancellation fee</span><span>- ₹{quote.cancellationFee.toFixed(2)}</span></div>
              <hr />
              <div className="total"><span>Estimated refund</span><strong>₹{quote.refundableAmount.toFixed(2)}</strong></div>
            </div>

            <label style={{ display: "block", marginTop: "12px", textAlign: "left" }}>
              Cancellation reason (optional)
              <input
                type="text"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Change of travel plans"
                style={{ width: "100%", padding: "8px", marginTop: "4px" }}
              />
            </label>

            <p className="identity-note">
              Once confirmed, your seat berth reservation will be safely released and the refund will be credited back via Dhruva Bank.
            </p>

            <div style={{ display: "flex", gap: "10px", justifyContent: "flex-end", marginTop: "16px" }}>
              <button
                className="button button--secondary"
                disabled={loading}
                onClick={() => setShowModal(false)}
                type="button"
              >
                Keep ticket
              </button>
              <button
                className="button button--primary button--danger"
                disabled={loading}
                onClick={() => void confirmCancellation()}
                type="button"
              >
                {loading ? "Processing…" : "Confirm cancellation"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
