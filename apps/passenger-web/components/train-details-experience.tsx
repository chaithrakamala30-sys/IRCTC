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

type PublicTrainTrackingView = {
  trainNumber: string;
  trainName: string;
  serviceDate: string;
  operationalStatus: string;
  currentDelayMinutes: number;
  routeProgressPercent: number;
  currentStationCode: string | null;
  currentStationName: string | null;
  nextStationCode: string | null;
  nextStationName: string | null;
  lastReportedAt: string | null;
  dataReliabilityStatus: string;
  stops: {
    stopIndex: number;
    stationCode: string;
    stationName: string;
    scheduledArrival: string;
    scheduledDeparture: string;
    actualArrival: string;
    actualDeparture: string;
    delayMinutes: number;
    platformNumber: string;
    stopStatus: string;
  }[];
  relevantAnnouncements: {
    announcementId: string;
    title: string;
    content: string;
    announcementType: string;
    priority: string;
  }[];
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
  const [trackingData, setTrackingData] = useState<PublicTrainTrackingView | null>(null);
  const [trackingLoading, setTrackingLoading] = useState(false);
  const [searchTrainNo, setSearchTrainNo] = useState("");
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

  async function fetchTrainTracking(trainNum: string) {
    if (!trainNum.trim()) return;
    setTrackingLoading(true);
    try {
      const res = await apiFetch<PublicTrainTrackingView>(`/api/v1/tracking/trains/${encodeURIComponent(trainNum.trim())}`);
      setTrackingData(res.data ?? null);
      if (res.data) {
        setError("");
      }
    } catch {
      // fallback
    } finally {
      setTrackingLoading(false);
    }
  }

  useEffect(() => {
    let active = true;
    const params = new URLSearchParams(window.location.search);
    const trainId = params.get("trainId");
    const fromStationId = params.get("fromStationId");
    const toStationId = params.get("toStationId");
    const journeyDate = params.get("journeyDate");
    const trainNo = params.get("trainNo");

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
    } else if (trainNo) {
      setSearchTrainNo(trainNo);
      setLoading(false);
      void fetchTrainTracking(trainNo);
    } else {
      // No search parameters or trainNo provided
      setError("");
      setLoading(false);
      // Auto-load a popular train (e.g. 12952 Mumbai Tejas Rajdhani or 12650 Karnataka Express)
      void fetchTrainTracking("12952");
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

      {/* Train Lookup & Live Timetable Search Bar */}
      <div
        style={{
          background: "rgba(10, 24, 52, 0.75)",
          backdropFilter: "blur(12px)",
          border: "1px solid rgba(56, 189, 248, 0.25)",
          borderRadius: "14px",
          padding: "1.25rem",
          margin: "1rem 0 1.5rem 0",
        }}
      >
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "0.75rem", marginBottom: "0.75rem" }}>
          <div>
            <h3 style={{ margin: 0, fontSize: "1.05rem", color: "#f8fafc", fontWeight: 700 }}>
              Live Train Schedule &amp; Timetable Lookup
            </h3>
            <p style={{ margin: "2px 0 0 0", fontSize: "0.8rem", color: "#94a3b8" }}>
              Lookup route stops, platform numbers, and live operational running status for any scheduled train
            </p>
          </div>
          <div style={{ display: "flex", gap: "0.5rem" }}>
            {["12952", "12951", "20608", "22436", "12301", "12724", "12626"].map((tNo) => (
              <button
                key={tNo}
                type="button"
                onClick={() => {
                  setSearchTrainNo(tNo);
                  void fetchTrainTracking(tNo);
                }}
                style={{
                  padding: "0.25rem 0.55rem",
                  borderRadius: "6px",
                  border: "1px solid rgba(56, 189, 248, 0.3)",
                  background: searchTrainNo === tNo ? "#0284c7" : "rgba(15, 23, 42, 0.8)",
                  color: "#f8fafc",
                  fontSize: "0.75rem",
                  cursor: "pointer",
                }}
              >
                {tNo}
              </button>
            ))}
          </div>
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (searchTrainNo.trim()) void fetchTrainTracking(searchTrainNo.trim());
          }}
          style={{ display: "flex", gap: "0.5rem" }}
        >
          <input
            type="text"
            placeholder="Enter Train Number or Name (e.g. 12952, 12650, 20608)..."
            value={searchTrainNo}
            onChange={(e) => setSearchTrainNo(e.target.value)}
            style={{
              flex: 1,
              padding: "0.6rem 0.85rem",
              borderRadius: "8px",
              border: "1px solid rgba(56, 189, 248, 0.35)",
              background: "#081226",
              color: "#f8fafc",
              fontSize: "0.85rem",
              outline: "none",
            }}
          />
          <button
            type="submit"
            disabled={trackingLoading}
            style={{
              padding: "0.6rem 1.25rem",
              borderRadius: "8px",
              background: "linear-gradient(135deg, #0284c7, #2563eb)",
              color: "#ffffff",
              border: "none",
              fontWeight: 700,
              fontSize: "0.85rem",
              cursor: "pointer",
            }}
          >
            {trackingLoading ? "Finding Train…" : "View Train Details"}
          </button>
        </form>
      </div>

      {/* Standalone / Direct Train Tracking & Route View */}
      {trackingData && !detail && (
        <section
          style={{
            background: "rgba(10, 24, 52, 0.85)",
            backdropFilter: "blur(16px)",
            border: "1px solid rgba(56, 189, 248, 0.2)",
            borderRadius: "16px",
            padding: "1.5rem",
            marginBottom: "2rem",
          }}
        >
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap", gap: "1rem", marginBottom: "1.25rem" }}>
            <div>
              <span style={{ fontSize: "0.75rem", textTransform: "uppercase", letterSpacing: "1px", color: "#38bdf8", fontWeight: 700 }}>
                Official Railway Master Timetable
              </span>
              <h2 style={{ margin: "0.25rem 0", fontSize: "1.6rem", color: "#f8fafc", fontWeight: 800 }}>
                {trackingData.trainNumber} · {trackingData.trainName}
              </h2>
              <p style={{ margin: 0, fontSize: "0.85rem", color: "#94a3b8" }}>
                Service Date: <strong>{trackingData.serviceDate}</strong> &middot; Status:{" "}
                <span style={{
                  padding: "2px 8px",
                  borderRadius: "4px",
                  fontWeight: 700,
                  fontSize: "0.75rem",
                  background: trackingData.operationalStatus === "RUNNING" ? "#065f46" : trackingData.operationalStatus === "DELAYED" ? "#854d0e" : "#1e293b",
                  color: trackingData.operationalStatus === "RUNNING" ? "#34d399" : trackingData.operationalStatus === "DELAYED" ? "#fef08a" : "#94a3b8",
                }}>
                  {trackingData.operationalStatus}
                </span>
              </p>
            </div>

            <div style={{ display: "flex", gap: "1rem", alignItems: "center" }}>
              <div style={{ textAlign: "right" }}>
                <div style={{ fontSize: "0.75rem", color: "#94a3b8" }}>Current Running Delay</div>
                <div style={{ fontSize: "1.1rem", fontWeight: 700, color: trackingData.currentDelayMinutes > 0 ? "#f87171" : "#4ade80" }}>
                  {trackingData.currentDelayMinutes > 0 ? `+${trackingData.currentDelayMinutes} min delay` : "Right Time"}
                </div>
              </div>
              <Link
                href={`/${device}?trainNo=${encodeURIComponent(trackingData.trainNumber)}`}
                style={{
                  padding: "0.55rem 1rem",
                  borderRadius: "8px",
                  background: "rgba(56, 189, 248, 0.15)",
                  border: "1px solid rgba(56, 189, 248, 0.4)",
                  color: "#38bdf8",
                  fontSize: "0.85rem",
                  fontWeight: 600,
                  textDecoration: "none",
                }}
              >
                Book Tickets on this Train →
              </Link>
            </div>
          </div>

          {/* Quick Metrics Bar */}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: "0.75rem", marginBottom: "1.5rem" }}>
            <div style={{ background: "rgba(15, 23, 42, 0.7)", padding: "0.75rem 1rem", borderRadius: "10px", border: "1px solid rgba(255,255,255,0.06)" }}>
              <div style={{ fontSize: "0.75rem", color: "#94a3b8" }}>Current Station</div>
              <div style={{ fontWeight: 700, color: "#f8fafc", fontSize: "0.95rem" }}>{trackingData.currentStationName ?? "Origin Yard"}</div>
            </div>
            <div style={{ background: "rgba(15, 23, 42, 0.7)", padding: "0.75rem 1rem", borderRadius: "10px", border: "1px solid rgba(255,255,255,0.06)" }}>
              <div style={{ fontSize: "0.75rem", color: "#94a3b8" }}>Next Station</div>
              <div style={{ fontWeight: 700, color: "#f8fafc", fontSize: "0.95rem" }}>{trackingData.nextStationName ?? "Terminal Yard"}</div>
            </div>
            <div style={{ background: "rgba(15, 23, 42, 0.7)", padding: "0.75rem 1rem", borderRadius: "10px", border: "1px solid rgba(255,255,255,0.06)" }}>
              <div style={{ fontSize: "0.75rem", color: "#94a3b8" }}>Route Progress</div>
              <div style={{ fontWeight: 700, color: "#38bdf8", fontSize: "0.95rem" }}>{trackingData.routeProgressPercent.toFixed(0)}%</div>
            </div>
            <div style={{ background: "rgba(15, 23, 42, 0.7)", padding: "0.75rem 1rem", borderRadius: "10px", border: "1px solid rgba(255,255,255,0.06)" }}>
              <div style={{ fontSize: "0.75rem", color: "#94a3b8" }}>Telemetry Source</div>
              <div style={{ fontWeight: 600, color: "#94a3b8", fontSize: "0.8rem" }}>{trackingData.dataReliabilityStatus}</div>
            </div>
          </div>

          {/* Ordered Route Stops Table */}
          <h3 style={{ fontSize: "1.05rem", color: "#f8fafc", margin: "0 0 0.75rem 0", fontWeight: 700 }}>
            Scheduled Route &amp; Stop Details ({trackingData.stops?.length ?? 0} Stops)
          </h3>
          <div style={{ overflowX: "auto", border: "1px solid rgba(255,255,255,0.08)", borderRadius: "10px" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.85rem" }}>
              <thead>
                <tr style={{ background: "rgba(15, 23, 42, 0.8)", textAlign: "left", borderBottom: "1px solid rgba(255,255,255,0.08)", color: "#94a3b8" }}>
                  <th style={{ padding: "0.65rem 0.85rem" }}>#</th>
                  <th style={{ padding: "0.65rem 0.85rem" }}>Station</th>
                  <th style={{ padding: "0.65rem 0.85rem" }}>Sched Arr / Dep</th>
                  <th style={{ padding: "0.65rem 0.85rem" }}>Actual / Rep</th>
                  <th style={{ padding: "0.65rem 0.85rem" }}>Status / Delay</th>
                  <th style={{ padding: "0.65rem 0.85rem" }}>Platform</th>
                </tr>
              </thead>
              <tbody>
                {trackingData.stops.map((stop) => (
                  <tr key={stop.stopIndex} style={{ borderBottom: "1px solid rgba(255,255,255,0.04)" }}>
                    <td style={{ padding: "0.65rem 0.85rem", color: "#64748b" }}>{stop.stopIndex + 1}</td>
                    <td style={{ padding: "0.65rem 0.85rem" }}>
                      <strong style={{ color: "#38bdf8", marginRight: "0.4rem" }}>{stop.stationCode}</strong>
                      <span style={{ color: "#f8fafc" }}>{stop.stationName}</span>
                    </td>
                    <td style={{ padding: "0.65rem 0.85rem", color: "#cbd5e1" }}>
                      {stop.scheduledArrival} / {stop.scheduledDeparture}
                    </td>
                    <td style={{ padding: "0.65rem 0.85rem", color: "#94a3b8" }}>
                      {stop.actualArrival} / {stop.actualDeparture}
                    </td>
                    <td style={{ padding: "0.65rem 0.85rem" }}>
                      <span style={{
                        color: stop.delayMinutes > 0 ? "#f87171" : "#4ade80",
                        fontWeight: 600,
                      }}>
                        {stop.delayMinutes > 0 ? `+${stop.delayMinutes}m` : "On time"}
                      </span>
                    </td>
                    <td style={{ padding: "0.65rem 0.85rem", color: "#f8fafc" }}>
                      PF {stop.platformNumber || "1"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
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
            fareQuoteId={fareQuote?.fareQuoteId}
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
