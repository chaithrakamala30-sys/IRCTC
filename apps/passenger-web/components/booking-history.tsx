"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { ApiRequestError, apiFetch } from "../lib/api";
import { DemoPaymentAction } from "./demo-payment-action";

type DeviceExperience = "mobile" | "laptop";
type BookingSummary = {
  bookingId: string;
  bookingReference: string;
  status: string;
  trainNumber: string;
  trainName: string;
  journeyDate: string;
  originName: string;
  destinationName: string;
  coachClassCode: string;
  passengerCount: number;
  totalPayable: string;
  currency: string;
  paymentDeadline: string;
  createdAt: string;
};
type Booking = BookingSummary & {
  trainRunId: string;
  fromStationId: string;
  toStationId: string;
  distanceKm: string;
  groupSeatingResult: string;
  fareQuoteId: string;
  fareRuleVersionNumber: number;
  passengers: {
    passengerNumber: number;
    displayName: string;
    coachNumber: string;
    berthNumber: string;
    berthType: string;
    passengerCategory: string;
    fareItems: { code: string; label: string; type: string; amount: string }[];
    totalFare: string;
  }[];
  stateHistory: {
    previousStatus: string | null;
    newStatus: string;
    reasonCode: string;
    occurredAt: string;
  }[];
};
type BookingPage = { items: BookingSummary[]; page: number; size: number; total: number };

function errorText(error: unknown): string {
  return error instanceof ApiRequestError ? error.message : "Bookings could not be loaded.";
}

function money(amount: string, currency: string): string {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency }).format(Number(amount));
}

export function BookingHistoryPanel({ device }: { device: DeviceExperience }) {
  const [bookings, setBookings] = useState<BookingSummary[]>([]);
  const [selected, setSelected] = useState<Booking | null>(null);
  const [status, setStatus] = useState("");
  const [reference, setReference] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState("");

  const loadBookings = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const params = new URLSearchParams({ page: String(page), size: "10" });
      if (status) params.set("status", status);
      if (query.trim()) params.set("reference", query.trim());
      const response = await apiFetch<BookingPage>(`/api/v1/bookings?${params}`);
      setBookings(response.data?.items ?? []);
      setTotal(response.data?.total ?? 0);
    } catch (caught) {
      setError(errorText(caught));
    } finally {
      setLoading(false);
    }
  }, [page, query, status]);

  useEffect(() => {
    void loadBookings();
  }, [loadBookings]);

  async function openBooking(bookingId: string) {
    setSelected(null);
    setDetailLoading(true);
    setError("");
    try {
      const response = await apiFetch<Booking>(`/api/v1/bookings/${encodeURIComponent(bookingId)}`);
      setSelected(response.data ?? null);
    } catch (caught) {
      setError(errorText(caught));
    } finally {
      setDetailLoading(false);
    }
  }

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPage(0);
    setQuery(reference.trim());
    setSelected(null);
  }

  return (
    <section className={`account-panel booking-history booking-history--${device}`} id="bookings">
      <div className="booking-history-heading">
        <div>
          <p className="eyebrow">Passenger booking management</p>
          <h2>My bookings</h2>
        </div>
        <button className="button button--secondary" onClick={() => void loadBookings()} type="button">
          Refresh
        </button>
      </div>
      <form className="booking-history-filters" onSubmit={submitSearch}>
        <label>
          Booking reference
          <input
            maxLength={24}
            onChange={(event) => setReference(event.target.value)}
            placeholder="Search reference"
            value={reference}
          />
        </label>
        <label>
          Status
          <select onChange={(event) => { setPage(0); setStatus(event.target.value); }} value={status}>
            <option value="">All statuses</option>
            <option value="AWAITING_PAYMENT">Awaiting payment</option>
            <option value="CONFIRMED">Confirmed</option>
            <option value="EXPIRED">Expired</option>
          </select>
        </label>
        <button className="button button--primary" type="submit">Search</button>
      </form>
      {error && <p className="identity-alert identity-alert--error" role="alert">{error}</p>}
      {loading && <p role="status">Loading bookings…</p>}
      {!loading && bookings.length === 0 && <p>No bookings match this search.</p>}
      {device === "laptop" && bookings.length > 0 && (
        <div className="booking-history-table-wrap">
          <table className="booking-history-table">
            <thead>
              <tr><th>Reference</th><th>Journey</th><th>Date</th><th>Status</th><th>Payable</th><th /></tr>
            </thead>
            <tbody>
              {bookings.map((booking) => (
                <tr key={booking.bookingId}>
                  <td><strong>{booking.bookingReference}</strong></td>
                  <td>{booking.trainNumber} · {booking.originName} to {booking.destinationName}</td>
                  <td>{booking.journeyDate}</td>
                  <td>{booking.status.replaceAll("_", " ")}</td>
                  <td>{money(booking.totalPayable, booking.currency)}</td>
                  <td><button className="text-button" onClick={() => void openBooking(booking.bookingId)} type="button">Details</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {device === "mobile" && bookings.length > 0 && (
        <ul className="booking-history-cards">
          {bookings.map((booking) => (
            <li key={booking.bookingId}>
              <strong>{booking.bookingReference}</strong>
              <span>{booking.trainNumber} · {booking.trainName}</span>
              <span>{booking.originName} to {booking.destinationName} · {booking.journeyDate}</span>
              <span>{booking.status.replaceAll("_", " ")} · {money(booking.totalPayable, booking.currency)}</span>
              <button className="button button--secondary" onClick={() => void openBooking(booking.bookingId)} type="button">View details</button>
            </li>
          ))}
        </ul>
      )}
      <div className="booking-history-pagination">
        <span>{total} booking{total === 1 ? "" : "s"}</span>
        <div>
          <button className="button button--secondary" disabled={page === 0 || loading} onClick={() => setPage((value) => value - 1)} type="button">Previous</button>
          <button className="button button--secondary" disabled={(page + 1) * 10 >= total || loading} onClick={() => setPage((value) => value + 1)} type="button">Next</button>
        </div>
      </div>
      {detailLoading && <p role="status">Loading booking details…</p>}
      {selected && (
        <section className="booking-detail-card" aria-label={`Booking ${selected.bookingReference} details`}>
          <div className="booking-history-heading">
            <h3>{selected.bookingReference}</h3>
            <button className="text-button" onClick={() => setSelected(null)} type="button">Close details</button>
          </div>
          <p><strong>{selected.trainNumber} · {selected.trainName}</strong></p>
          <p>{selected.originName} to {selected.destinationName} · {selected.journeyDate} · {selected.coachClassCode}</p>
          <p>Status: <strong>{selected.status.replaceAll("_", " ")}</strong></p>
          {selected.status === "AWAITING_PAYMENT" && (
            <DemoPaymentAction bookingId={selected.bookingId} device={device} />
          )}
          <p>Payment deadline: {new Date(selected.paymentDeadline).toLocaleString()}</p>
          <div className="booking-detail-passengers">
            {selected.passengers.map((passenger) => (
              <article key={passenger.passengerNumber}>
                <h4>Passenger {passenger.passengerNumber}: {passenger.displayName}</h4>
                <p>{passenger.coachNumber} · berth {passenger.berthNumber} ({passenger.berthType.replaceAll("_", " ")})</p>
                <ul>
                  {passenger.fareItems.map((item, index) => (
                    <li key={`${item.code}-${index}`}><span>{item.label}</span><strong>{money(item.amount, selected.currency)}</strong></li>
                  ))}
                </ul>
                <p>Passenger total: <strong>{money(passenger.totalFare, selected.currency)}</strong></p>
              </article>
            ))}
          </div>
          <p className="booking-total">Total payable: <strong>{money(selected.totalPayable, selected.currency)}</strong></p>
          <ol className="booking-state-history">
            {selected.stateHistory.map((entry, index) => (
              <li key={`${entry.newStatus}-${entry.occurredAt}-${index}`}>
                <strong>{entry.newStatus.replaceAll("_", " ")}</strong>
                <span>{entry.reasonCode.replaceAll("_", " ")}</span>
                <time dateTime={entry.occurredAt}>{new Date(entry.occurredAt).toLocaleString()}</time>
              </li>
            ))}
          </ol>
          {selected.status !== "CONFIRMED" && (
            <p className="identity-note">Awaiting payment does not mean paid or confirmed.</p>
          )}
          {selected.status === "CONFIRMED" && (
            <p className="identity-note">Demo payment verified. This is not a real-money ticket.</p>
          )}
        </section>
      )}
    </section>
  );
}
