"use client";

import { useEffect, useState } from "react";
import { ApiRequestError, apiFetch } from "@railconnect/api";

type RefundView = {
  refundOrderId: string;
  cancellationReference: string;
  bookingReference: string;
  pnr: string;
  merchantRefundRef: string;
  providerCode: string;
  providerRefundId: string | null;
  paymentIntentId: string;
  amount: number;
  currency: string;
  refundStatus: string;
  verified: boolean;
  failureMessage?: string;
  createdAt: string;
};

export function AdminCancellationConsole() {
  const [refundIdInput, setRefundIdInput] = useState("");
  const [selectedRefund, setSelectedRefund] = useState<RefundView | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  async function loadRefund(id: string) {
    if (!id.trim()) return;
    setError("");
    setNotice("");
    setBusy(true);
    try {
      const res = await apiFetch<RefundView>(`/api/v1/admin/refunds/${encodeURIComponent(id.trim())}`);
      setSelectedRefund(res.data);
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "Refund order could not be loaded.");
    } finally {
      setBusy(false);
    }
  }

  async function reconcileRefund(id: string) {
    setError("");
    setNotice("");
    setBusy(true);
    try {
      const res = await apiFetch<RefundView>(`/api/v1/admin/refunds/${encodeURIComponent(id)}/reconcile`, {
        method: "POST",
      });
      setSelectedRefund(res.data);
      setNotice(`Refund reconciled with status: ${res.data?.refundStatus}`);
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "Reconciliation failed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="admin-panel ticket-admin-panel" aria-labelledby="cancellation-admin-title">
      <p className="eyebrow">Financial reconciliation &amp; audit</p>
      <h2 id="cancellation-admin-title">Cancellation &amp; Refund Management</h2>
      <p>Inspect Dhruva Bank refund settlements, double-entry status, and resolve reconciliation holds.</p>

      {error && <p className="admin-alert admin-alert--error">{error}</p>}
      {notice && <p className="admin-alert admin-alert--success">{notice}</p>}

      <form
        className="admin-filters"
        onSubmit={(e) => {
          e.preventDefault();
          void loadRefund(refundIdInput);
        }}
      >
        <label>
          Refund Order ID
          <input
            placeholder="e.g. 11223344-5566-7788-99aa-bbccddeeff00"
            value={refundIdInput}
            onChange={(e) => setRefundIdInput(e.target.value)}
          />
        </label>
        <button className="button button--primary" disabled={busy} type="submit">
          {busy ? "Loading…" : "Inspect Refund"}
        </button>
      </form>

      {selectedRefund && (
        <div className="ticket-admin-diagnostics">
          <h3>Refund Order {selectedRefund.merchantRefundRef}</h3>
          <p>
            Booking <strong>{selectedRefund.bookingReference}</strong> (PNR: {selectedRefund.pnr}) · Cancellation Ref: <strong>{selectedRefund.cancellationReference}</strong>
          </p>
          <div style={{ margin: "14px 0", background: "#f8fafc", padding: "12px", borderRadius: "8px" }}>
            <div>Refund Status: <strong>{selectedRefund.refundStatus}</strong></div>
            <div>Amount: <strong>{selectedRefund.currency} {selectedRefund.amount.toFixed(2)}</strong></div>
            <div>Provider: {selectedRefund.providerCode} (Intent: {selectedRefund.paymentIntentId})</div>
            <div>Authoritative Ledger Verified: <strong>{selectedRefund.verified ? "YES (Double-entry checked)" : "PENDING / UNVERIFIED"}</strong></div>
            {selectedRefund.failureMessage && <div style={{ color: "#b91c1c" }}>Exception: {selectedRefund.failureMessage}</div>}
          </div>

          {selectedRefund.refundStatus !== "SUCCEEDED" && (
            <button
              className="button button--primary"
              disabled={busy}
              onClick={() => void reconcileRefund(selectedRefund.refundOrderId)}
              type="button"
            >
              Verify with Dhruva Bank &amp; Reconcile
            </button>
          )}
        </div>
      )}
    </section>
  );
}
