"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { ApiRequestError, apiFetch } from "@railconnect/api";
import { DemoPaymentAction } from "./demo-payment-action";

type CoachClassOption = { code: string; name: string };
type Page<T> = { items: T[]; page: number; size: number; total: number };
type TrainRun = { trainRunId: string; serviceDate: string; status: string };
type ClassAvailability = {
  coachClassCode: string;
  coachClassName: string;
  totalBerths: number;
  available: number;
  held: number;
  blocked: number;
  unavailable: number;
  status: string;
};
type Availability = {
  trainRunId: string;
  distanceKm: number;
  classes: ClassAvailability[];
};
type Hold = {
  inventoryHoldId: string;
  coachNumber: string;
  coachClassCode: string;
  berthNumber: string;
  status: string;
  expiresAt: string;
};
type PassengerDraft = { displayName: string; preferredBerthType: string };
type Reservation = {
  reservationRequestId: string;
  coachClassCode: string;
  groupSeatingRequested: boolean;
  status: string;
  groupSeatingResult: string;
  expiresAt: string | null;
  passengers: {
    displayName: string;
    preferredBerthType: string | null;
    coachNumber: string | null;
    berthNumber: string | null;
    preferenceResult: string | null;
  }[];
};
type Booking = {
  bookingId: string;
  bookingReference: string;
  status: string;
  trainNumber: string;
  trainName: string;
  journeyDate: string;
  originName: string;
  destinationName: string;
  totalPayable: string;
  currency: string;
  paymentDeadline: string;
  passengers: { displayName: string; coachNumber: string; berthNumber: string }[];
};

const TERMINAL_RESERVATION_STATUSES = new Set(["RELEASED", "FAILED", "EXPIRED"]);

function isHoldExpired(expiresAt: string): boolean {
  const value = new Date(expiresAt).getTime();
  return Number.isFinite(value) && value <= Date.now();
}

function message(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.status === 409) {
      return "This temporary hold is already linked to an active reservation request. Release the reservation request before releasing the hold.";
    }
    if (error.status === 404) {
      return "This temporary hold is no longer available. It may already have expired or been released.";
    }
    return error.message;
  }
  return error instanceof Error
    ? error.message
    : "Seat inventory or reservation actions could not be completed. Please try again.";
}

export function SeatInventoryPanel({
  device,
  trainId,
  journeyDate,
  fromStationId,
  toStationId,
  classes,
  fareQuoteId,
}: {
  device: "mobile" | "laptop";
  trainId: string;
  journeyDate: string;
  fromStationId: string;
  toStationId: string;
  classes: CoachClassOption[];
  fareQuoteId?: string;
}) {
  const [run, setRun] = useState<TrainRun | null>(null);
  const [selectedClass, setSelectedClass] = useState(classes[0]?.code ?? "");
  const [availability, setAvailability] = useState<ClassAvailability | null>(null);
  const [holds, setHolds] = useState<Hold[]>([]);
  const [passengers, setPassengers] = useState<PassengerDraft[]>([
    { displayName: "", preferredBerthType: "" },
  ]);
  const [groupSeatingRequested, setGroupSeatingRequested] = useState(false);
  const [reservation, setReservation] = useState<Reservation | null>(null);
  const [booking, setBooking] = useState<Booking | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState("");
  const [bookingIdempotencyKey, setBookingIdempotencyKey] = useState("");
  const [loading, setLoading] = useState(true);
  const [availabilityLoading, setAvailabilityLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [authStatus, setAuthStatus] = useState<"checking" | "signed-in" | "signed-out">("checking");
  const [signInBusy, setSignInBusy] = useState(false);
  const [signInError, setSignInError] = useState("");
  const availabilityRequest = useRef(0);
  const reservationStorageKey = run
    ? `railconnect:reservation-request:${run.trainRunId}:${journeyDate}:${fromStationId}:${toStationId}`
    : null;

  function resetReservationForm() {
    setReservation(null);
    setBooking(null);
    setIdempotencyKey("");
    setBookingIdempotencyKey("");
    setGroupSeatingRequested(false);
    setPassengers([{ displayName: "", preferredBerthType: "" }]);
  }

  const loadHolds = useCallback(async () => {
    try {
      const response = await apiFetch<Hold[]>("/api/v1/passenger/inventory/holds");
      const refreshed = (response.data ?? []).filter(
        (hold) => hold.status === "ACTIVE" && !isHoldExpired(hold.expiresAt),
      );
      setHolds(refreshed);
      if (response.data && response.data.length !== refreshed.length) {
        setNotice("One or more temporary holds have expired or been released and were removed from your active list.");
      }
    } catch (caught: unknown) {
      if (caught instanceof ApiRequestError && caught.status === 401) {
        setAuthStatus("signed-out");
        setHolds([]);
        return;
      }
      setError(message(caught));
    }
  }, []);

  useEffect(() => {
    if (authStatus !== "signed-in") return;
    const timer = window.setInterval(() => {
      setHolds((current) => current.filter((hold) => hold.status === "ACTIVE" && !isHoldExpired(hold.expiresAt)));
      void loadHolds();
    }, 15000);
    return () => window.clearInterval(timer);
  }, [authStatus, loadHolds]);

  useEffect(() => {
    let active = true;
    void apiFetch<unknown>("/api/v1/auth/me")
      .then(() => {
        if (active) {
          setAuthStatus("signed-in");
          void loadHolds();
        }
      })
      .catch((caught: unknown) => {
        if (active) {
          setAuthStatus("signed-out");
          setHolds([]);
          if (!(caught instanceof ApiRequestError && caught.status === 401)) {
            setSignInError(message(caught));
          }
        }
      });
    return () => {
      active = false;
    };
  }, [loadHolds]);

  useEffect(() => {
    let active = true;
    const query = new URLSearchParams({
      serviceDate: journeyDate,
      trainId,
      page: "0",
      size: "1",
    });
    setLoading(true);
    setError("");
    setRun(null);
    setAvailability(null);
    setReservation(null);
    setBooking(null);
    availabilityRequest.current += 1;
    void apiFetch<Page<TrainRun>>(`/api/v1/public/inventory/runs?${query}`)
      .then((response) => {
        if (active) setRun(response.data?.items[0] ?? null);
      })
      .catch((caught: unknown) => {
        if (active) setError(message(caught));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [fromStationId, journeyDate, toStationId, trainId]);

  useEffect(() => {
    if (authStatus !== "signed-in" || !reservationStorageKey || reservation) return;

    let reservationId: string | null;
    try {
      reservationId = window.localStorage.getItem(reservationStorageKey);
    } catch {
      setError("Browser storage is unavailable, so the saved reservation request could not be restored.");
      return;
    }
    if (!reservationId) return;

    let active = true;
    void apiFetch<Reservation>(
      `/api/v1/passenger/reservations/requests/${encodeURIComponent(reservationId)}`,
    )
      .then((response) => {
        if (!response.data) throw new Error("The server did not return the saved reservation request.");
        if (!active) return;
        if (TERMINAL_RESERVATION_STATUSES.has(response.data.status)) {
          try {
            window.localStorage.removeItem(reservationStorageKey);
          } catch {
            setError("The saved reservation request is no longer active, but its browser entry could not be cleared.");
            return;
          }
          resetReservationForm();
          setNotice("");
          void loadHolds();
          return;
        }
        setReservation(response.data);
        setSelectedClass(response.data.coachClassCode);
        setGroupSeatingRequested(response.data.groupSeatingRequested);
        setPassengers(response.data.passengers.map((passenger) => ({
          displayName: passenger.displayName,
          preferredBerthType: passenger.preferredBerthType ?? "",
        })));
        setNotice("Your reservation request has been restored.");
      })
      .catch((caught: unknown) => {
        if (!active) return;
        if (caught instanceof ApiRequestError && caught.status === 404) {
          try {
            window.localStorage.removeItem(reservationStorageKey);
          } catch {
            setError("The saved reservation request could not be found, and its browser entry could not be cleared.");
            return;
          }
          setNotice("The saved reservation request is no longer available. You can create a new request.");
          return;
        }
        setError(message(caught));
      });

    return () => {
      active = false;
    };
  }, [authStatus, reservation, reservationStorageKey]);

  const loadAvailability = useCallback(async () => {
    if (!run || !selectedClass) return;
    setError("");
    setAvailability(null);
    setAvailabilityLoading(true);
    const requestId = ++availabilityRequest.current;
    const query = new URLSearchParams({
      trainRunId: run.trainRunId,
      fromStationId,
      toStationId,
      coachClassCode: selectedClass,
    });
    try {
      const response = await apiFetch<Availability>(`/api/v1/public/inventory/availability?${query}`);
      if (requestId === availabilityRequest.current) {
        setAvailability(response.data?.classes[0] ?? null);
      }
    } catch (caught) {
      if (requestId === availabilityRequest.current) setError(message(caught));
    } finally {
      if (requestId === availabilityRequest.current) setAvailabilityLoading(false);
    }
  }, [fromStationId, run, selectedClass, toStationId]);

  useEffect(() => {
    void loadAvailability();
  }, [loadAvailability]);

  async function signIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSignInBusy(true);
    setSignInError("");
    try {
      await apiFetch<unknown>("/api/v1/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          identifier: form.get("email"),
          password: form.get("password"),
        }),
      });
      setAuthStatus("signed-in");
      setNotice("Signed in. You can now hold an available berth.");
      await loadHolds();
      await loadAvailability();
    } catch (caught) {
      setSignInError(message(caught));
    } finally {
      setSignInBusy(false);
    }
  }

  async function createHold() {
    if (!run || !selectedClass) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const response = await apiFetch<Hold>("/api/v1/passenger/inventory/holds", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": crypto.randomUUID(),
        },
        body: JSON.stringify({
          trainRunId: run.trainRunId,
          fromStationId,
          toStationId,
          coachClassCode: selectedClass,
        }),
      });
      const createdHold = response.data;
      if (!createdHold) throw new Error("The server did not return the created hold.");
      setHolds((current) => [...current.filter((hold) => hold.inventoryHoldId !== createdHold.inventoryHoldId), createdHold]);
      setNotice("Temporary seat hold created. It is not a reservation or ticket.");
      await loadHolds();
      await loadAvailability();
    } catch (caught) {
      setError(message(caught));
    } finally {
      setSaving(false);
    }
  }

  async function releaseHold() {
    setSaving(true);
    setError("");
    setNotice("");
    try {
      for (const hold of holds) {
        await apiFetch<Hold>(
          `/api/v1/passenger/inventory/holds/${encodeURIComponent(hold.inventoryHoldId)}`,
          { method: "DELETE" },
        );
      }
      setHolds([]);
      setNotice("Temporary holds released.");
      await loadHolds();
      await loadAvailability();
    } catch (caught) {
      setError(message(caught));
    } finally {
      setSaving(false);
    }
  }

  async function releaseSingleHold(holdId: string) {
    setSaving(true);
    setError("");
    setNotice("");
    try {
      await apiFetch<Hold>(`/api/v1/passenger/inventory/holds/${encodeURIComponent(holdId)}`, {
        method: "DELETE",
      });
      setHolds((current) => current.filter((hold) => hold.inventoryHoldId !== holdId));
      setNotice("Temporary hold released.");
      await loadHolds();
      await loadAvailability();
    } catch (caught) {
      setError(message(caught));
    } finally {
      setSaving(false);
    }
  }

  async function createReservation() {
    if (!run || holds.length !== passengers.length) return;
    setSaving(true);
    setError("");
    setNotice("");
    const requestKey = idempotencyKey || crypto.randomUUID();
    setIdempotencyKey(requestKey);
    try {
      const response = await apiFetch<Reservation>("/api/v1/passenger/reservations/requests", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": requestKey,
        },
        body: JSON.stringify({
          trainRunId: run.trainRunId,
          journeyDate,
          fromStationId,
          toStationId,
          coachClassCode: selectedClass,
          groupSeatingRequested,
          passengers: passengers.map(({ displayName, preferredBerthType }) => ({
            displayName,
            ...(preferredBerthType ? { preferredBerthType } : {}),
          })),
          inventoryHoldIds: holds.map((hold) => hold.inventoryHoldId),
        }),
      });
      if (!response.data) throw new Error("The server did not return the reservation request.");
      if (!reservationStorageKey) throw new Error("The train run is not ready for a reservation request.");
      try {
        window.localStorage.setItem(reservationStorageKey, response.data.reservationRequestId);
      } catch {
        setError("Reservation request created, but this browser could not save it for restoration after refresh.");
      }
      setReservation(response.data);
      setIdempotencyKey("");
      setNotice("Reservation request created. No berth is allocated until you continue.");
    } catch (caught) {
      setError(message(caught));
    } finally {
      setSaving(false);
    }
  }

  async function allocateReservation() {
    if (!reservation) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const response = await apiFetch<Reservation>(
        `/api/v1/passenger/reservations/requests/${encodeURIComponent(reservation.reservationRequestId)}/allocate`,
        { method: "POST" },
      );
      if (!response.data) throw new Error("The server did not return the allocation result.");
      setReservation(response.data);
      setNotice(response.data.status === "ALLOCATED_PENDING_BOOKING"
        ? "Provisional berths allocated. Complete booking before the displayed expiry; this is not a ticket."
        : "The request was processed. Review its current status below.");
      await loadAvailability();
    } catch (caught) {
      setError(message(caught));
    } finally {
      setSaving(false);
    }
  }

  async function createBooking() {
    if (!reservation || !fareQuoteId || reservation.status !== "ALLOCATED_PENDING_BOOKING") return;
    setSaving(true);
    setError("");
    setNotice("");
    const requestKey = bookingIdempotencyKey || crypto.randomUUID();
    setBookingIdempotencyKey(requestKey);
    try {
      const response = await apiFetch<Booking>("/api/v1/bookings", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": requestKey,
        },
        body: JSON.stringify({
          fareQuoteId,
          reservationRequestId: reservation.reservationRequestId,
        }),
      });
      if (!response.data) throw new Error("The server did not return the booking.");
      setBooking(response.data);
      setBookingIdempotencyKey("");
      setNotice("Booking created and awaiting payment. No payment has been taken; this is not a paid ticket.");
    } catch (caught) {
      setError(message(caught));
    } finally {
      setSaving(false);
    }
  }

  async function releaseReservation() {
    if (!reservation) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const response = await apiFetch<Reservation>(
        `/api/v1/passenger/reservations/requests/${encodeURIComponent(reservation.reservationRequestId)}/release`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ reasonCode: "PASSENGER_CANCELLED" }),
        },
      );
      if (!response.data) throw new Error("The server did not return the release result.");
      try {
        if (reservationStorageKey) {
          window.localStorage.removeItem(reservationStorageKey);
        }
      } catch {
        setError("Reservation request released, but its browser entry could not be cleared. Refresh after clearing site storage.");
        return;
      }
      resetReservationForm();
      setHolds([]);
      setNotice("");
      await loadHolds();
      await loadAvailability();
    } catch (caught) {
      setError(message(caught));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section aria-labelledby="seat-inventory-title" className="passenger-inventory-section">
      <div className="passenger-inventory-heading">
        <div>
          <p className="eyebrow">Live run inventory</p>
          <h2 id="seat-inventory-title">Seat availability</h2>
        </div>
        <span>Segment-based · temporary holds only</span>
      </div>
      <p className="passenger-inventory-intro">
        Availability is for this exact train run and journey segment. A temporary hold expires automatically and is not a booking.
      </p>
      {authStatus === "checking" && (
        <p className="passenger-inventory-auth-status" role="status">Checking passenger sign-in…</p>
      )}
      {authStatus === "signed-out" && (
        <section className="passenger-inventory-signin" aria-labelledby="inventory-signin-title">
          <div>
            <h3 id="inventory-signin-title">Sign in to hold a berth</h3>
            <p>You can view availability without an account. Sign in to hold a berth or create a reservation request.</p>
          </div>
          <form className="passenger-inventory-signin-form" onSubmit={signIn}>
            <label>
              Email address
              <input autoComplete="email" name="email" required type="email" />
            </label>
            <label>
              Password
              <input autoComplete="current-password" name="password" required type="password" />
            </label>
            <button className="button button--primary" disabled={signInBusy} type="submit">
              {signInBusy ? "Signing in…" : "Sign in"}
            </button>
          </form>
          {signInError && <p className="search-error" role="alert">{signInError}</p>}
          <p className="passenger-inventory-signin-register">
            New to RailConnect? <Link href={`/${device}/register`}>Create an account</Link>
          </p>
        </section>
      )}
      {loading && <p role="status">Checking inventory for this service date…</p>}
      {!loading && !run && !error && (
        <p className="passenger-inventory-empty" role="status">
          Seat inventory has not been initialized for this train and date. No availability is being assumed.
        </p>
      )}
      {error && <p className="search-error" role="alert">{error}</p>}
      {run && (
        <>
          <div className="passenger-inventory-controls">
            <label>
              Coach class
              <select
                disabled={holds.length > 0 || reservation !== null}
                onChange={(event) => setSelectedClass(event.target.value)}
                value={selectedClass}
              >
                {classes.map((coachClass) => (
                  <option key={coachClass.code} value={coachClass.code}>
                    {coachClass.code} — {coachClass.name}
                  </option>
                ))}
              </select>
            </label>
            <button className="button button--secondary" disabled={!selectedClass || availabilityLoading} onClick={() => void loadAvailability()} type="button">
              Refresh availability
            </button>
          </div>
          {availabilityLoading && <p role="status">Refreshing segment availability…</p>}
          {!availabilityLoading && !availability && !error && (
            <p className="passenger-inventory-empty" role="status">No inventory is configured for this coach class on the selected run.</p>
          )}
          {availability && (
            <div className="passenger-inventory-result" aria-live="polite">
              <div><span>Total berths</span><strong>{availability.totalBerths}</strong></div>
              <div><span>Available for this segment</span><strong>{availability.available}</strong></div>
              <div><span>Temporarily held</span><strong>{availability.held}</strong></div>
              <div><span>Operationally blocked</span><strong>{availability.blocked}</strong></div>
              <div><span>Unavailable</span><strong>{availability.unavailable}</strong></div>
              <span className={`inventory-status inventory-status--${availability.status.toLowerCase()}`}>
                {availability.status.replaceAll("_", " ")}
              </span>
            </div>
          )}
          {authStatus === "signed-in" && (
            <>
          {notice && <p className="passenger-inventory-notice" role="status">{notice}</p>}
          <div className="reservation-passengers">
            <h3>Passengers</h3>
            {passengers.map((passenger, index) => (
              <div className="reservation-passenger-row" key={`reservation-passenger-${index}`}>
                <label>
                  Passenger {index + 1} name
                  <input
                    autoComplete="name"
                    maxLength={120}
                    onChange={(event) => setPassengers((current) => current.map((item, row) =>
                      row === index ? { ...item, displayName: event.target.value } : item))}
                    value={passenger.displayName}
                  />
                </label>
                <label>
                  Berth preference (optional)
                  <select
                    onChange={(event) => setPassengers((current) => current.map((item, row) =>
                      row === index ? { ...item, preferredBerthType: event.target.value } : item))}
                    value={passenger.preferredBerthType}
                  >
                    <option value="">No preference</option>
                    <option value="LOWER_BERTH">Lower</option>
                    <option value="MIDDLE_BERTH">Middle</option>
                    <option value="UPPER_BERTH">Upper</option>
                    <option value="SIDE_LOWER_BERTH">Side lower</option>
                    <option value="SIDE_UPPER_BERTH">Side upper</option>
                  </select>
                </label>
                {passengers.length > 1 && !reservation && (
                  <button
                    className="button button--secondary"
                    disabled={saving || holds.length >= passengers.length}
                    onClick={() => setPassengers((current) => current.filter((_, row) => row !== index))}
                    type="button"
                  >
                    Remove
                  </button>
                )}
              </div>
            ))}
            {!reservation && passengers.length < 4 && (
              <button
                className="button button--secondary"
                disabled={saving || holds.length >= passengers.length}
                onClick={() => setPassengers((current) => [...current, { displayName: "", preferredBerthType: "" }])}
                type="button"
              >
                Add passenger
              </button>
            )}
            {passengers.length > 1 && !reservation && (
              <label className="reservation-group-choice">
                <input
                  checked={groupSeatingRequested}
                  onChange={(event) => setGroupSeatingRequested(event.target.checked)}
                  type="checkbox"
                />
                Request berths in the same coach (not guaranteed)
              </label>
            )}
          </div>
          <div className="reservation-holds-list" aria-live="polite">
            {holds.map((hold) => (
              <div className="passenger-inventory-hold" key={hold.inventoryHoldId}>
                <p>
                  Temporary hold: <strong>{hold.coachNumber} · {hold.berthNumber}</strong>
                  {" "}({hold.coachClassCode}) until {new Date(hold.expiresAt).toLocaleTimeString()}.
                </p>
                {!reservation && (
                  <button className="button button--secondary" disabled={saving}
                    onClick={() => void releaseSingleHold(hold.inventoryHoldId)} type="button">
                    Release hold
                  </button>
                )}
              </div>
            ))}
          </div>
          {!reservation && (
            <div className="reservation-actions">
              <button
                className="button button--secondary"
                disabled={saving || !availability || availability.available === 0 || holds.length >= passengers.length}
                onClick={() => void createHold()}
                type="button"
              >
                {saving ? "Working…" : `Hold berth (${holds.length}/${passengers.length})`}
              </button>
              <button
                className="button button--primary"
                disabled={saving || holds.length !== passengers.length
                  || passengers.some((passenger) => passenger.displayName.trim().length === 0)}
                onClick={() => void createReservation()}
                type="button"
              >
                Create reservation request
              </button>
              {holds.length > 0 && (
                <button className="button button--secondary" disabled={saving}
                  onClick={() => void releaseHold()} type="button">
                  Release all holds
                </button>
              )}
            </div>
          )}
          {reservation && (
            <section className="reservation-result" aria-live="polite" aria-labelledby="reservation-result-title">
              <h3 id="reservation-result-title">Reservation request</h3>
              <p>Status: <strong>{reservation.status.replaceAll("_", " ")}</strong></p>
              {reservation.expiresAt && (
                <p>Allocation expiry: <strong>{new Date(reservation.expiresAt).toLocaleString()}</strong></p>
              )}
              {reservation.groupSeatingResult !== "NOT_REQUESTED" && (
                <p>Group seating: {reservation.groupSeatingResult.replaceAll("_", " ")}</p>
              )}
              <ul>
                {reservation.passengers.map((passenger, index) => (
                  <li key={`${reservation.reservationRequestId}-${index}`}>
                    {passenger.displayName}
                    {passenger.coachNumber && passenger.berthNumber
                      ? ` — ${passenger.coachNumber}, berth ${passenger.berthNumber}`
                      : " — no berth allocated yet"}
                    {passenger.preferenceResult
                      ? ` · preference ${passenger.preferenceResult.toLowerCase()}`
                      : ""}
                  </li>
                ))}
              </ul>
              {reservation.status === "REQUESTED" && (
                <button className="button button--primary" disabled={saving}
                  onClick={() => void allocateReservation()} type="button">
                  {saving ? "Working…" : "Allocate provisional berths"}
                </button>
              )}
              {!booking && ["REQUESTED", "ALLOCATED_PENDING_BOOKING"].includes(reservation.status) && (
                <button className="button button--secondary" disabled={saving}
                  onClick={() => void releaseReservation()} type="button">
                  Release request
                </button>
              )}
              {reservation.status === "ALLOCATED_PENDING_BOOKING" && !booking && (
                <div className="booking-create-panel">
                  <p>
                    Create a booking from this provisional allocation and an active fare quote for the same
                    journey and passenger count. The backend verifies both before saving the payable amount.
                  </p>
                  <button
                    className="button button--primary"
                    disabled={saving || !fareQuoteId}
                    onClick={() => void createBooking()}
                    type="button"
                  >
                    {saving ? "Creating booking…" : "Create booking"}
                  </button>
                  {!fareQuoteId && <small>Get an itemized fare quote below before creating the booking.</small>}
                </div>
              )}
              {booking && (
                <div className="booking-confirmation" role="status">
                  <p className="eyebrow">RailConnect booking reference</p>
                  <strong className="booking-reference">{booking.bookingReference}</strong>
                  <p>Status: <strong>{booking.status.replaceAll("_", " ")}</strong></p>
                  <p>
                    {booking.trainNumber} · {booking.trainName} · {booking.journeyDate}
                  </p>
                  <p>{booking.originName} to {booking.destinationName}</p>
                  <ul>
                    {booking.passengers.map((passenger, index) => (
                      <li key={`${booking.bookingId}-${index}`}>
                        {passenger.displayName} — {passenger.coachNumber}, berth {passenger.berthNumber}
                      </li>
                    ))}
                  </ul>
                  <p>
                    Total payable: <strong>
                      {new Intl.NumberFormat("en-IN", {
                        style: "currency",
                        currency: booking.currency,
                      }).format(Number(booking.totalPayable))}
                    </strong>
                  </p>
                  <p>Payment deadline: <strong>{new Date(booking.paymentDeadline).toLocaleString()}</strong></p>
                  {booking.status === "AWAITING_PAYMENT" && (
                    <DemoPaymentAction bookingId={booking.bookingId} device={device} />
                  )}
                  {booking.status !== "CONFIRMED" && (
                    <p className="reservation-disclaimer">
                      Awaiting payment. This is not a confirmed or paid ticket.
                    </p>
                  )}
                  {booking.status === "CONFIRMED" && (
                    <p className="reservation-disclaimer">Demo payment verified. This is not a real-money ticket.</p>
                  )}
                  <Link href={`/${device}/account#bookings`}>View booking history</Link>
                </div>
              )}
              <p className="reservation-disclaimer">
                {!booking && "A provisional allocation is not a confirmed booking or ticket."}
              </p>
            </section>
          )}
            </>
          )}
        </>
      )}
    </section>
  );
}
