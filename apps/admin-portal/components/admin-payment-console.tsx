"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiRequestError, apiFetch } from "@railconnect/api";

type PaymentSummary = {
  paymentId: string;
  bookingId: string;
  bookingReference: string;
  providerCode: string;
  providerPaymentIntentId: string | null;
  status: string;
  amount: string | number;
  currency: string;
  expiresAt: string;
  createdAt: string;
};
type PaymentDetails = {
  order: PaymentSummary;
  transactionReference: string | null;
  attemptsJson: string;
  stateHistoryJson: string;
  webhookEventsJson: string;
  reconciliationJson: string;
  financialExceptionsJson: string;
};

function message(error: unknown): string {
  return error instanceof ApiRequestError
    ? error.message
    : error instanceof Error ? error.message : "Payment audit data could not be loaded.";
}

export function AdminPaymentConsole() {
  const [query, setQuery] = useState("");
  const [payments, setPayments] = useState<PaymentSummary[]>([]);
  const [selected, setSelected] = useState<PaymentDetails | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const search = useCallback(async (searchQuery: string) => {
    setBusy(true);
    setError("");
    try {
      const params = new URLSearchParams({ page: "0", size: "50" });
      if (searchQuery.trim()) params.set("query", searchQuery.trim());
      const response = await apiFetch<PaymentSummary[]>(
        `/api/v1/admin/payments/orders?${params.toString()}`,
      );
      setPayments(response.data ?? []);
      setSelected(null);
    } catch (caught) {
      setError(message(caught));
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    void search("");
  }, [search]);

  async function openPayment(paymentId: string) {
    setError("");
    try {
      const response = await apiFetch<PaymentDetails>(
        `/api/v1/admin/payments/orders/${encodeURIComponent(paymentId)}`,
      );
      setSelected(response.data ?? null);
    } catch (caught) {
      setError(message(caught));
    }
  }

  return (
    <section className="admin-panel" aria-labelledby="payments-title">
      <div className="admin-panel-heading">
        <div>
          <p className="eyebrow">Demo-payment audit</p>
          <h1 id="payments-title">RailConnect Pay</h1>
          <p>Read-only payment status and reconciliation evidence. No merchant configuration is exposed here.</p>
        </div>
        <button className="button button--secondary" disabled={busy} onClick={() => void search(query)} type="button">
          Refresh
        </button>
      </div>
      <form className="admin-filters" onSubmit={(event) => {
        event.preventDefault();
        void search(query);
      }}>
        <label>
          Booking reference, payment ID, or provider intent ID
          <input maxLength={80} onChange={(event) => setQuery(event.target.value)} value={query} />
        </label>
        <button className="button button--primary" disabled={busy} type="submit">Search</button>
      </form>
      {error && <p className="admin-alert admin-alert--error" role="alert">{error}</p>}
      {payments.length === 0 && !busy && <p>No payment orders found.</p>}
      {payments.length > 0 && (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr><th>Booking</th><th>Provider</th><th>Status</th><th>Amount</th><th>Created</th><th /></tr>
            </thead>
            <tbody>
              {payments.map((payment) => (
                <tr key={payment.paymentId}>
                  <td>{payment.bookingReference}</td>
                  <td>{payment.providerCode}</td>
                  <td>{payment.status.replaceAll("_", " ")}</td>
                  <td>{new Intl.NumberFormat("en-IN", {
                    style: "currency",
                    currency: payment.currency,
                  }).format(Number(payment.amount))}</td>
                  <td>{new Date(payment.createdAt).toLocaleString()}</td>
                  <td><button className="text-button" onClick={() => void openPayment(payment.paymentId)} type="button">Audit</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {selected && (
        <section className="booking-detail-card" aria-label="Payment audit details">
          <h2>{selected.order.bookingReference}</h2>
          <p>Payment {selected.order.paymentId} · {selected.order.status.replaceAll("_", " ")}</p>
          <p>Provider intent: {selected.order.providerPaymentIntentId ?? "Not returned"}</p>
          <p>Transaction reference: {selected.transactionReference ?? "Not verified"}</p>
          <details><summary>Attempts</summary><pre>{selected.attemptsJson}</pre></details>
          <details><summary>State history</summary><pre>{selected.stateHistoryJson}</pre></details>
          <details><summary>Webhook evidence</summary><pre>{selected.webhookEventsJson}</pre></details>
          <details><summary>Reconciliation</summary><pre>{selected.reconciliationJson}</pre></details>
          <details><summary>Financial exceptions</summary><pre>{selected.financialExceptionsJson}</pre></details>
        </section>
      )}
      <p className="reservation-disclaimer">
        DEMO PAYMENT ONLY. A provider state is not a real-money settlement; escalate open reconciliation cases.
      </p>
    </section>
  );
}
