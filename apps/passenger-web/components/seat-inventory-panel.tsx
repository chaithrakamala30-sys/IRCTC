"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiRequestError, apiFetch } from "@railconnect/api";

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

function message(error: unknown): string {
  return error instanceof ApiRequestError
    ? error.message
    : "Seat inventory could not be loaded. Please try again.";
}

export function SeatInventoryPanel({
  trainId,
  journeyDate,
  fromStationId,
  toStationId,
  classes,
}: {
  trainId: string;
  journeyDate: string;
  fromStationId: string;
  toStationId: string;
  classes: CoachClassOption[];
}) {
  const [run, setRun] = useState<TrainRun | null>(null);
  const [selectedClass, setSelectedClass] = useState(classes[0]?.code ?? "");
  const [availability, setAvailability] = useState<ClassAvailability | null>(null);
  const [hold, setHold] = useState<Hold | null>(null);
  const [loading, setLoading] = useState(true);
  const [availabilityLoading, setAvailabilityLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const availabilityRequest = useRef(0);

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
  }, [trainId, journeyDate]);

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
      setHold(response.data);
      setNotice("Temporary seat hold created. This is not a reservation or ticket.");
      await loadAvailability();
    } catch (caught) {
      setError(message(caught));
    } finally {
      setSaving(false);
    }
  }

  async function releaseHold() {
    if (!hold) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      await apiFetch<Hold>(`/api/v1/passenger/inventory/holds/${encodeURIComponent(hold.inventoryHoldId)}`, {
        method: "DELETE",
      });
      setHold(null);
      setNotice("Temporary hold released.");
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
              <select onChange={(event) => setSelectedClass(event.target.value)} value={selectedClass}>
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
          {notice && <p className="passenger-inventory-notice" role="status">{notice}</p>}
          {hold && (
            <div className="passenger-inventory-hold">
              <p>
                Temporary hold: <strong>{hold.coachNumber} · {hold.berthNumber}</strong>
                {" "}({hold.coachClassCode}) until {new Date(hold.expiresAt).toLocaleTimeString()}.
              </p>
              <button className="button button--secondary" disabled={saving} onClick={() => void releaseHold()} type="button">
                Release hold
              </button>
            </div>
          )}
          {!hold && (
            <button className="button button--primary" disabled={saving || !availability || availability.available === 0} onClick={() => void createHold()} type="button">
              {saving ? "Working…" : "Hold one available berth"}
            </button>
          )}
        </>
      )}
    </section>
  );
}
