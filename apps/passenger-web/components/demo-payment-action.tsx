"use client";

import { useEffect, useState } from "react";
import { ApiRequestError, apiFetch } from "../lib/api";

type PaymentOrder = {
  paymentId: string;
  status: string;
  checkoutUrl: string | null;
  demoPayment: boolean;
  requiresReconciliation: boolean;
};
type PaymentProvider = { providerCode: string; available: boolean; demoPayment: boolean };

export function DemoPaymentAction({
  bookingId,
  device,
}: {
  bookingId: string;
  device: "mobile" | "laptop";
}) {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [available, setAvailable] = useState<boolean | null>(null);

  useEffect(() => {
    let active = true;
    void apiFetch<PaymentProvider[]>("/api/v1/passenger/payments/providers")
      .then((response) => {
        if (active) {
          setAvailable(response.data?.some(
            (provider) => provider.providerCode === "DHRUVA_BANK" && provider.available && provider.demoPayment,
          ) ?? false);
        }
      })
      .catch((caught: unknown) => {
        if (active) {
          setAvailable(false);
          setMessage(caught instanceof ApiRequestError
            ? caught.message
            : "Demo payment availability could not be checked.");
        }
      });
    return () => {
      active = false;
    };
  }, []);

  async function beginPayment() {
    setBusy(true);
    setMessage("");
    try {
      const response = await apiFetch<PaymentOrder>("/api/v1/passenger/payments/orders", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": crypto.randomUUID(),
        },
        body: JSON.stringify({ bookingId, experience: device }),
      });
      const payment = response.data;
      if (!payment) throw new Error("The server did not return a payment order.");
      if (payment.requiresReconciliation) {
        setMessage("Payment status needs reconciliation. Do not retry or create another payment; check your booking status later.");
      } else if (payment.checkoutUrl
        && ["INITIATED", "CHECKOUT_PENDING", "PROCESSING"].includes(payment.status)) {
        window.location.assign(payment.checkoutUrl);
        return;
      }
      setMessage(`Demo payment status: ${payment.status.replaceAll("_", " ")}.`);
    } catch (caught) {
      setMessage(caught instanceof ApiRequestError
        ? caught.message
        : caught instanceof Error ? caught.message : "The demo payment could not be started.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="demo-payment-action">
      <p className="reservation-disclaimer">
        DEMO PAYMENT — hosted by Dhruva Bank. No real money is taken; never enter real banking credentials.
      </p>
      <button
        className="button button--primary"
        disabled={busy || available !== true}
        onClick={() => void beginPayment()}
        type="button"
      >
        {busy ? "Starting demo checkout…" : available === null
          ? "Checking demo payment…"
          : available ? "Continue to demo payment" : "Demo payment unavailable"}
      </button>
      {available === false && !message && (
        <p role="status">Dhruva Bank demo payments are not enabled for this environment.</p>
      )}
      {message && <p role="status">{message}</p>}
    </div>
  );
}
