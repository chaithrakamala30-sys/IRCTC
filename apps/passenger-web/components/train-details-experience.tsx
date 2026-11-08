"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiRequestError, apiFetch } from "../lib/api";
import { formatLocalTime } from "../lib/time-format";
import type { TrainDetail } from "./train-search-types";
import { SeatInventoryPanel } from "./seat-inventory-panel";

type DeviceExperience = "mobile" | "laptop";
type PassengerCategory = {
  passengerCategoryId: string;
  categoryCode: string;
  categoryName: string;
  minimumAge: number | null;
  maximumAge: number | null;
  active: boolean;
};
type FarePassenger = { categoryCode: string; categoryName: string; items: { code: string; label: string; type: string; amount: string }[]; totalAmount: string };
type FareQuote = {
  fareQuoteId: string;
  currency: string;
  status: string;
  distanceKm: string;
  trainNumber: string;
  trainName: string;
  originName: string;
  destinationName: string;
  journeyDate: string;
  coachClassCode: string;
  coachClassName: string;
  passengers: FarePassenger[];
  totalAmount: string;
  createdAt: string;
  expiresAt: string;
  fareRuleVersionNumber: number;
  quoteOnlyNotice: string;
};

function showError(error: unknown): string {
  return error instanceof ApiRequestError
    ? error.message
    : "Train details could not be loaded. Please try again.";
}

function operatingDays(days: number[]): string {
  const labels = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
  return days.map((day) => labels[day - 1]).filter(Boolean).join(", ") || "Special service";
}

export function TrainDetailsExperience({ device }: { device: DeviceExperience }) {
  const [detail, setDetail] = useState<TrainDetail | null>(null);
  const [backLink, setBackLink] = useState(`/${device}`);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [fareError, setFareError] = useState("");
  const [fareLoading, setFareLoading] = useState(false);
  const [fareClasses, setFareClasses] = useState<PassengerCategory[]>([]);
  const [selectedClass, setSelectedClass] = useState("");
  const [passengers, setPassengers] = useState([{ categoryCode: "ADULT", age: "" }]);
  const [fareQuote, setFareQuote] = useState<FareQuote | null>(null);
  const [currentTime, setCurrentTime] = useState(Date.now());

  useEffect(() => {
    let active = true;
    void apiFetch<PassengerCategory[]>("/api/v1/public/fares/passenger-categories")
      .then((response) => {
        if (active) setFareClasses(response.data ?? []);
      })
      .catch((caught: unknown) => {
        if (active) setFareError(showError(caught));
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!fareQuote) return;
    const timer = window.setInterval(() => setCurrentTime(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [fareQuote]);

  useEffect(() => {
    let active = true;
    const params = new URLSearchParams(window.location.search);
    const trainId = params.get("trainId");
    const fromStationId = params.get("fromStationId");
    const toStationId = params.get("toStationId");
    const journeyDate = params.get("journeyDate");
    params.delete("trainId");
    if (params.has("page")) params.set("page", "0");
    if (trainId && fromStationId && toStationId && journeyDate) {
      setBackLink(`/${device}?${params}`);
      const request = new URLSearchParams({ fromStationId, toStationId, journeyDate });
      void apiFetch<TrainDetail>(`/api/v1/public/trains/${encodeURIComponent(trainId)}?${request}`)
        .then((response) => {
          if (active) {
            setDetail(response.data);
            setSelectedClass(response.data?.journey.coachClasses[0]?.code ?? "");
          }
        })
        .catch((caught: unknown) => {
          if (active) setError(showError(caught));
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    } else {
      setError("Open train details from a search result so the selected journey is preserved.");
      setLoading(false);
    }
    return () => {
      active = false;
    };
  }, [device]);

  async function createFareQuote() {
    if (!detail) return;
    setFareError("");
    setFareLoading(true);
    setFareQuote(null);
    try {
      const response = await apiFetch<FareQuote>("/api/v1/public/fares/quotes", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          trainId: detail.journey.trainId,
          fromStationId: detail.route.find((stop) => stop.selectedOrigin)?.stationId,
          toStationId: detail.route.find((stop) => stop.selectedDestination)?.stationId,
          journeyDate: detail.journey.journeyDate,
          coachClassCode: selectedClass,
          passengers: passengers.map((passenger) => ({
            categoryCode: passenger.categoryCode,
            age: passenger.age === "" ? null : Number(passenger.age),
          })),
        }),
      });
      setFareQuote(response.data ?? null);
      setCurrentTime(Date.now());
    } catch (caught) {
      setFareError(showError(caught));
    } finally {
      setFareLoading(false);
    }
  }

  const quoteExpired = fareQuote ? new Date(fareQuote.expiresAt).getTime() <= currentTime : false;
  const quoteMoney = (value: string) =>
    new Intl.NumberFormat(undefined, { style: "currency", currency: fareQuote?.currency ?? "INR" }).format(Number(value));

  return (
    <main className={`train-details-page train-details-page--${device}`} id="main-content">
      <Link className="back-to-results" href={backLink}>← Back to search results</Link>
      {loading && <p className="search-loading" role="status">Loading published route details…</p>}
      {error && <div className="search-error" role="alert">{error}</div>}
      {detail && (
        <>
          <section aria-labelledby="train-detail-title" className="train-detail-hero">
            <div>
              <p className="eyebrow">Published train service · {detail.journey.categoryName}</p>
              <h1 id="train-detail-title">{detail.journey.trainName}</h1>
              <p className="train-detail-number">Train {detail.journey.trainNumber}</p>
            </div>
            <div className="train-detail-summary">
              <span>{detail.journey.origin.stationName} ({detail.journey.origin.stationCode})</span>
              <strong>{formatLocalTime(detail.journey.departureAt, detail.journey.origin.timezone)}</strong>
              <span aria-hidden="true">to</span>
              <span>{detail.journey.destination.stationName} ({detail.journey.destination.stationCode})</span>
              <strong>{formatLocalTime(detail.journey.arrivalAt, detail.journey.destination.timezone)}</strong>
              <small>{detail.journey.journeyDate} · arrives {detail.journey.arrivalDate}</small>
            </div>
          </section>

          <section aria-label="Journey details" className="train-detail-facts">
            <div><span>Duration</span><strong>{Math.floor(detail.journey.durationMinutes / 60)}h {detail.journey.durationMinutes % 60}m</strong></div>
            <div><span>Journey distance</span><strong>{detail.journey.distanceKm.toLocaleString()} km</strong></div>
            <div><span>Operating days</span><strong>{operatingDays(detail.journey.operatingDays)}</strong></div>
            <div>
              <span>Configured classes and coach types</span>
              <strong>
                {detail.journey.coachClasses.map((coachClass) =>
                  `${coachClass.code} (${coachClass.coachCount}) · ${coachClass.coachTypes.join(", ") || "type not configured"}`,
                ).join("; ") || "Not configured"}
              </strong>
            </div>
          </section>

          <section aria-labelledby="complete-route-title" className="complete-route">
            <div>
              <p className="eyebrow">Published route version</p>
              <h2 id="complete-route-title">Complete route</h2>
            </div>
            <ol>
              {detail.route.map((stop) => (
                <li
                  className={[
                    "complete-route-stop",
                    stop.selectedOrigin ? "is-selected-origin" : "",
                    stop.selectedDestination ? "is-selected-destination" : "",
                  ].filter(Boolean).join(" ")}
                  key={`${stop.stationId}-${stop.sequence}`}
                >
                  <span aria-hidden="true" className="route-stop-marker" />
                  <div className="route-stop-main">
                    <p className="route-stop-station">
                      <strong>{stop.stationName}</strong> <span>{stop.stationCode}</span>
                      {stop.selectedOrigin && <em>Boarding</em>}
                      {stop.selectedDestination && <em>Destination</em>}
                    </p>
                    <small>{stop.city} · stop {stop.sequence} · {stop.timezone}</small>
                    {stop.haltMinutes > 0 && <small>Halt: {stop.haltMinutes} minutes</small>}
                  </div>
                  <div className="route-stop-times">
                    <span>Arrive <strong>{formatLocalTime(stop.arrivalTime, stop.timezone)}</strong></span>
                    <small>{stop.arrivalDate ?? "—"}{stop.arrivalDayOffset > 0 ? ` · day +${stop.arrivalDayOffset}` : ""}</small>
                    <span>Depart <strong>{formatLocalTime(stop.departureTime, stop.timezone)}</strong></span>
                    <small>{stop.departureDate ?? "—"}{stop.departureDayOffset > 0 ? ` · day +${stop.departureDayOffset}` : ""}</small>
                  </div>
                  <span className="route-stop-distance">{stop.distanceFromSourceKm.toLocaleString()} km</span>
                </li>
              ))}
            </ol>
          </section>

          <SeatInventoryPanel
            classes={detail.journey.coachClasses.map((coachClass) => ({
              code: coachClass.code,
              name: coachClass.name,
            }))}
            device={device}
            fromStationId={detail.route.find((stop) => stop.selectedOrigin)?.stationId ?? detail.journey.origin.stationId}
            journeyDate={detail.journey.journeyDate}
            toStationId={detail.route.find((stop) => stop.selectedDestination)?.stationId ?? detail.journey.destination.stationId}
            trainId={detail.journey.trainId}
          />

          <section aria-labelledby="fare-quote-title" className="passenger-fare-section">
            <div className="passenger-fare-heading">
              <div>
                <p className="eyebrow">Indicative fare</p>
                <h2 id="fare-quote-title">Get an itemized fare quote</h2>
              </div>
              <span>Quote only · no booking or payment</span>
            </div>
            <p className="passenger-fare-intro">
              The backend calculates this from currently published fare rules. If no approved rule covers this journey and class, a quote is not available.
            </p>
            {fareError && <p className="search-error" role="alert">{fareError}</p>}
            <div className="passenger-fare-controls">
              <label>
                Coach class
                <select onChange={(event) => setSelectedClass(event.target.value)} value={selectedClass}>
                  {detail.journey.coachClasses.map((coachClass) => (
                    <option key={coachClass.code} value={coachClass.code}>{coachClass.code} — {coachClass.name}</option>
                  ))}
                </select>
              </label>
              <div className="fare-passenger-list">
                {passengers.map((passenger, index) => {
                  const category = fareClasses.find((item) => item.categoryCode === passenger.categoryCode);
                  const ageRequired = category != null
                    && (category.minimumAge !== null || category.maximumAge !== null);
                  return (
                    <div className="fare-passenger-row" key={`passenger-${index}`}>
                      <label>
                        Passenger {index + 1} category
                        <select
                          onChange={(event) => setPassengers((items) => items.map((item, row) =>
                            row === index ? { ...item, categoryCode: event.target.value, age: "" } : item))}
                          value={passenger.categoryCode}
                        >
                          {fareClasses.map((item) => <option key={item.passengerCategoryId} value={item.categoryCode}>{item.categoryName}</option>)}
                        </select>
                      </label>
                      {ageRequired && (
                        <label>
                          Age
                          <input
                            max="120"
                            min="0"
                            onChange={(event) => setPassengers((items) => items.map((item, row) =>
                              row === index ? { ...item, age: event.target.value } : item))}
                            required
                            type="number"
                            value={passenger.age}
                          />
                        </label>
                      )}
                      {passengers.length > 1 && <button className="fare-remove-passenger" onClick={() => setPassengers((items) => items.filter((_, row) => row !== index))} type="button">Remove</button>}
                    </div>
                  );
                })}
              </div>
              <div className="passenger-fare-actions">
                <button className="button button--secondary" disabled={passengers.length >= 20 || fareClasses.length === 0} onClick={() => setPassengers((items) => [...items, { categoryCode: fareClasses[0]?.categoryCode ?? "ADULT", age: "" }])} type="button">Add passenger</button>
                <button className="button button--primary" disabled={fareLoading || !selectedClass || fareClasses.length === 0} onClick={() => void createFareQuote()} type="button">
                  {fareLoading ? "Calculating…" : fareQuote ? "Refresh quote" : "Get fare quote"}
                </button>
              </div>
            </div>
            {fareQuote && (
              <div aria-live="polite" className="passenger-fare-result" role="status">
                <div className="passenger-fare-result-heading">
                  <div><h3>{fareQuote.coachClassCode} · {fareQuote.distanceKm} km</h3><p>Fare rule version {fareQuote.fareRuleVersionNumber}</p></div>
                  <span className={quoteExpired ? "fare-expiry is-expired" : "fare-expiry"}>
                    {quoteExpired ? "Quote expired" : `Expires ${new Date(fareQuote.expiresAt).toLocaleTimeString()}`}
                  </span>
                </div>
                {fareQuote.passengers.map((passenger, index) => (
                  <article className="passenger-fare-person" key={`${passenger.categoryCode}-${index}`}>
                    <h4>Passenger {index + 1} · {passenger.categoryName}</h4>
                    <ul>{passenger.items.map((item, itemIndex) => (
                      <li key={`${item.code}-${itemIndex}`}><span>{item.label}</span><strong>{quoteMoney(item.amount)}</strong></li>
                    ))}</ul>
                    <p className="passenger-fare-subtotal"><span>Passenger total</span><strong>{quoteMoney(passenger.totalAmount)}</strong></p>
                  </article>
                ))}
                <p className="passenger-fare-total"><span>Total indicative fare</span><strong>{quoteMoney(fareQuote.totalAmount)}</strong></p>
                <p className="passenger-fare-disclaimer">{fareQuote.quoteOnlyNotice}</p>
              </div>
            )}
          </section>

          <p className="no-booking-note">
            An indicative fare quote and temporary seat hold are not a reservation or ticket. Reservations, payments, and ticketing are not provided.
          </p>
        </>
      )}
    </main>
  );
}
