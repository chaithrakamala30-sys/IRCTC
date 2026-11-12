"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ApiRequestError, apiFetch } from "../lib/api";
import { ReferenceHeader } from "./reference-header";

type PaymentOrder = {
  paymentId: string;
  bookingId: string;
  bookingReference: string;
  status: string;
  requiresReconciliation: boolean;
};

type Booking = {
  bookingReference: string;
  status: string;
};

export function PaymentReturnExperience({ device }: { device: "mobile" | "laptop" }) {
  const [paymentId, setPaymentId] = useState("");
  const [payment, setPayment] = useState<PaymentOrder | null>(null);
  const [booking, setBooking] = useState<Booking | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");

  const refresh = useCallback(async (id: string) => {
    setBusy(true);
    setError("");
    try {
      const response = await apiFetch<PaymentOrder>(
        `/api/v1/passenger/payments/orders/${encodeURIComponent(id)}/refresh`,
        { method: "POST" },
      );
      if (!response.data) throw new Error("The server did not return payment status.");
      setPayment(response.data);
      const bookingResponse = await apiFetch<Booking>(
        `/api/v1/bookings/${encodeURIComponent(response.data.bookingId)}`,
      );
      setBooking(bookingResponse.data ?? null);
    } catch (caught) {
      setError(caught instanceof ApiRequestError
        ? caught.message
        : caught instanceof Error ? caught.message : "Payment status could not be verified.");
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    const id = new URLSearchParams(window.location.search).get("paymentId") ?? "";
    setPaymentId(id);
    if (!id) {
      setError("This return link does not include a payment reference.");
      setBusy(false);
      return;
    }
    void refresh(id);
  }, [refresh]);

  const statusText = payment?.requiresReconciliation
    ? "Needs reconciliation"
    : payment?.status.replaceAll("_", " ") ?? "Checking";

  return (
    <div className={`page-shell bg-[#040914] text-[#eaf3ff] min-h-screen`}>
      <ReferenceHeader experience={device} />
      <main className={`payment-return payment-return--${device} max-w-3xl mx-auto px-4 py-8`}>
      <p className="eyebrow">Dhruva Bank · DEMO PAYMENT</p>
      <h1>Payment status</h1>
      {busy && <p role="status">Verifying the payment with the server…</p>}
      {error && <p className="identity-alert identity-alert--error" role="alert">{error}</p>}
      {payment && (
        <section className="booking-detail-card" aria-live="polite">
          <p>RailConnect booking reference: <strong>{payment.bookingReference}</strong></p>
          <p>Payment status: <strong>{statusText}</strong></p>
          {booking && <p>Booking status: <strong>{booking.status.replaceAll("_", " ")}</strong></p>}
          {payment.requiresReconciliation && (
            <p role="status">
              We could not safely match the provider result. Do not submit another payment. RailConnect support must review it.
            </p>
          )}
          {payment.status === "SUCCEEDED" && booking?.status === "CONFIRMED" && (
            <p role="status">Demo payment verified and booking confirmed. This is not a real-money ticket.</p>
          )}
          {!payment.requiresReconciliation && payment.status !== "SUCCEEDED" && (
            <p role="status">
              A return from the checkout page does not prove payment. This status came from a server-side provider check.
            </p>
          )}
        </section>
      )}
      {!busy && paymentId && (
        <button className="button button--secondary" onClick={() => void refresh(paymentId)} type="button">
          Check status again
        </button>
      )}
      <Link href={`/${device}/account#bookings`}>Return to passenger account</Link>
      <p className="reservation-disclaimer">
        DEMO ONLY — do not enter card, UPI, PIN, or bank credentials on RailConnect.
      </p>
    </main>
  </div>
  );
}
