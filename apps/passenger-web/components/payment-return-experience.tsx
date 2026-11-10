"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ApiRequestError, apiFetch } from "../lib/api";

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
  const isConfirmed = payment?.status === "SUCCEEDED" && booking?.status === "CONFIRMED";
  const statusTitle = busy
    ? "Checking your payment"
    : isConfirmed
      ? "Booking confirmed"
      : payment?.requiresReconciliation
        ? "Payment needs review"
        : payment?.status === "FAILED" || payment?.status === "CANCELLED" || payment?.status === "EXPIRED"
          ? "Payment not completed"
          : error
            ? "We couldn't confirm the payment"
            : "You're back from checkout";
  const statusMessage = busy
    ? "We’re checking the latest status securely with RailConnect."
    : isConfirmed
      ? "Your demo payment was verified and your booking is confirmed."
      : payment?.requiresReconciliation
        ? "We couldn’t safely match the provider result. Please wait for support to review it."
        : payment?.status === "FAILED" || payment?.status === "CANCELLED" || payment?.status === "EXPIRED"
          ? "The latest server-side check says this payment did not complete."
          : error
            ? "We couldn’t retrieve a verified status just now. You can try checking again."
            : "A checkout redirect alone does not confirm payment. Check the verified status below.";

  return (
    <main className={`payment-return-page payment-return-page--${device}`}>
      <header className="payment-return__header">
        <Link className="payment-return__brand" href={`/${device}`}>
          <span className="payment-return__brand-mark" aria-hidden="true">R</span>
          <span>RailConnect</span>
        </Link>
        <span className="payment-return__environment">Demo environment</span>
      </header>

      <section className="payment-return__card" aria-live="polite">
        <div className={`payment-return__status payment-return__status--${isConfirmed ? "success" : payment?.requiresReconciliation ? "warning" : error ? "error" : "pending"}`}>
          <span className="payment-return__status-icon" aria-hidden="true">
            {busy ? "…" : isConfirmed ? "✓" : payment?.requiresReconciliation || error ? "!" : "•"}
          </span>
          <p className="eyebrow">Dhruva Bank · Demo checkout</p>
          <h1>{statusTitle}</h1>
          <p className="payment-return__message">{statusMessage}</p>
        </div>

        {error && <p className="identity-alert identity-alert--error" role="alert">{error}</p>}

        {payment && (
          <dl className="payment-return__details">
            <div>
              <dt>Booking reference</dt>
              <dd>{payment.bookingReference}</dd>
            </div>
            <div>
              <dt>Payment status</dt>
              <dd><span className="payment-return__badge">{statusText}</span></dd>
            </div>
            {booking && (
              <div>
                <dt>Booking status</dt>
                <dd>{booking.status.replaceAll("_", " ")}</dd>
              </div>
            )}
          </dl>
        )}

        {busy && (
          <p className="payment-return__loading" role="status">
            <span aria-hidden="true" />
            Contacting the payment service…
          </p>
        )}

        {!busy && (
          <div className="payment-return__actions">
            {paymentId && (
              <button
                className="button button--primary"
                onClick={() => void refresh(paymentId)}
                type="button"
              >
                Check status again
              </button>
            )}
            <Link className="button button--secondary" href={`/${device}/account#bookings`}>
              Return to bookings
            </Link>
          </div>
        )}

        <p className="payment-return__disclaimer">
          Demo only. RailConnect never asks for your card number, UPI PIN, or bank password.
        </p>
      </section>
      <p className="payment-return__help">Need help? Keep your booking reference available when contacting support.</p>
    </main>
  );
}
