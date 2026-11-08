"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiRequestError, apiFetch } from "../lib/api";
import type { TrainDetail } from "./train-search-types";

type DeviceExperience = "mobile" | "laptop";

function localTime(value: string | null, timezone: string): string {
  if (!value) return "—";
  return new Intl.DateTimeFormat(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    timeZone: timezone,
  }).format(new Date(value));
}

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
          if (active) setDetail(response.data);
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
              <strong>{localTime(detail.journey.departureAt, detail.journey.origin.timezone)}</strong>
              <span aria-hidden="true">to</span>
              <span>{detail.journey.destination.stationName} ({detail.journey.destination.stationCode})</span>
              <strong>{localTime(detail.journey.arrivalAt, detail.journey.destination.timezone)}</strong>
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
                    <span>Arrive <strong>{localTime(stop.arrivalTime, stop.timezone)}</strong></span>
                    <small>{stop.arrivalDate ?? "—"}{stop.arrivalDayOffset > 0 ? ` · day +${stop.arrivalDayOffset}` : ""}</small>
                    <span>Depart <strong>{localTime(stop.departureTime, stop.timezone)}</strong></span>
                    <small>{stop.departureDate ?? "—"}{stop.departureDayOffset > 0 ? ` · day +${stop.departureDayOffset}` : ""}</small>
                  </div>
                  <span className="route-stop-distance">{stop.distanceFromSourceKm.toLocaleString()} km</span>
                </li>
              ))}
            </ol>
          </section>

          <p className="no-booking-note">
            This page displays published timetable and configured coach-class information only.
            It does not show fares, seat availability, reservations, or payment.
          </p>
        </>
      )}
    </main>
  );
}
