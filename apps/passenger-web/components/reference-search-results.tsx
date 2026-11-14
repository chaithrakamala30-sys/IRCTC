"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ApiRequestError, apiFetch } from "../lib/api";
import { GoogleIcon } from "./google-icon";
import type {
  SearchFilters,
  SearchPage,
  SearchRequest,
  StationSuggestion,
  TrainSearchResult,
} from "./train-search-types";

type DeviceExperience = "mobile" | "laptop";
type Filters = Omit<SearchRequest, "fromStationId" | "toStationId" | "journeyDate" | "sortBy">;

const EMPTY_FILTERS: Filters = {
  trainCategory: "",
  coachClass: "",
  departureFrom: "",
  departureTo: "",
  arrivalFrom: "",
  arrivalTo: "",
  maximumDuration: undefined,
  q: "",
};

const PAGE_SIZE = 20;

function displayTime(value: string, timezone: string): string {
  try {
    return new Intl.DateTimeFormat(undefined, {
      hour: "2-digit",
      minute: "2-digit",
      timeZone: timezone,
    }).format(new Date(value));
  } catch {
    return value.slice(11, 16);
  }
}

function durationLabel(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  return hours > 0 ? `${hours}h ${remainder}m` : `${remainder}m`;
}

function dayNames(days: number[]): string {
  const labels = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
  return days.map((day) => labels[day - 1]).filter(Boolean).join(", ") || "Special service";
}

function requestParams(request: SearchRequest, page: number): URLSearchParams {
  const params = new URLSearchParams({
    fromStationId: request.fromStationId,
    toStationId: request.toStationId,
    journeyDate: request.journeyDate,
    sortBy: request.sortBy,
    page: String(page),
    size: String(PAGE_SIZE),
  });
  for (const key of [
    "trainCategory",
    "coachClass",
    "departureFrom",
    "departureTo",
    "arrivalFrom",
    "arrivalTo",
    "maximumDuration",
    "q",
  ] as const) {
    const value = request[key];
    if (value !== undefined && value !== "") params.set(key, String(value));
  }
  return params;
}

export function ReferenceSearchResults({ device }: { device: DeviceExperience }) {
  const [fromStation, setFromStation] = useState<StationSuggestion | null>(null);
  const [toStation, setToStation] = useState<StationSuggestion | null>(null);
  const [journeyDate, setJourneyDate] = useState("");
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);
  const [sortBy, setSortBy] = useState("DEPARTURE_EARLIEST");
  const [filterOptions, setFilterOptions] = useState<SearchFilters>({
    trainCategories: [],
    coachClasses: [],
  });
  const [results, setResults] = useState<SearchPage<TrainSearchResult> | null>(null);
  const [activeRequest, setActiveRequest] = useState<SearchRequest | null>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [hasSearchParams, setHasSearchParams] = useState(false);

  const executeSearch = useCallback(async (request: SearchRequest, page = 0) => {
    setLoading(true);
    setError("");
    setActiveRequest(request);
    setCurrentPage(page);
    const params = requestParams(request, page);
    const path = `/api/v1/public/trains/search?${params.toString()}`;
    try {
      const response = await apiFetch<SearchPage<TrainSearchResult>>(path);
      setResults(response.data);
      if (response.data) setCurrentPage(response.data.page);
    } catch (caught: unknown) {
      setError(
        caught instanceof ApiRequestError
          ? caught.message
          : "Train search could not be completed. Please try again."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void apiFetch<SearchFilters>("/api/v1/public/trains/filters")
      .then((res) => {
        if (res.data) setFilterOptions(res.data);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    const parseUrlAndSearch = () => {
      const params = new URLSearchParams(window.location.search);
      const fromId = params.get("fromStationId");
      const toId = params.get("toStationId");
      const date = params.get("journeyDate");

      if (!fromId || !toId || !date) {
        setHasSearchParams(false);
        setResults(null);
        return;
      }

      setHasSearchParams(true);
      setJourneyDate(date);

      void Promise.all([
        apiFetch<StationSuggestion>(`/api/v1/public/stations/${encodeURIComponent(fromId)}`),
        apiFetch<StationSuggestion>(`/api/v1/public/stations/${encodeURIComponent(toId)}`),
      ]).then(([fromRes, toRes]) => {
        if (!fromRes.data || !toRes.data) return;
        setFromStation(fromRes.data);
        setToStation(toRes.data);

        const request: SearchRequest = {
          fromStationId: fromRes.data.stationId,
          toStationId: toRes.data.stationId,
          journeyDate: date,
          trainCategory: params.get("trainCategory") ?? "",
          coachClass: params.get("coachClass") ?? "",
          departureFrom: params.get("departureFrom") ?? "",
          departureTo: params.get("departureTo") ?? "",
          arrivalFrom: params.get("arrivalFrom") ?? "",
          arrivalTo: params.get("arrivalTo") ?? "",
          maximumDuration: Number(params.get("maximumDuration")) || undefined,
          q: params.get("q") ?? "",
          sortBy: params.get("sortBy") ?? "DEPARTURE_EARLIEST",
        };
        void executeSearch(request, 0);

        // Smooth scroll to results
        setTimeout(() => {
          const el = document.getElementById("results");
          if (el) {
            el.scrollIntoView({ behavior: "smooth", block: "start" });
          }
        }, 150);
      });
    };

    parseUrlAndSearch();
    window.addEventListener("popstate", parseUrlAndSearch);
    return () => window.removeEventListener("popstate", parseUrlAndSearch);
  }, [executeSearch]);

  if (!hasSearchParams && !loading && !results) {
    return <div id="results" />;
  }

  const trains = results?.items ?? [];

  return (
    <section id="results" className="ref-results-container">
      <div className="ref-results-header">
        <div className="ref-results-title-group">
          <div className="ref-results-route-badge">
            <span className="dot dot-success animate-pulse" />
            <span>AVAILABLE TRAINS</span>
          </div>
          <h2>
            {fromStation && toStation
              ? `${fromStation.stationName} (${fromStation.stationCode}) → ${toStation.stationName} (${toStation.stationCode})`
              : "Search Results"}
          </h2>
          <p className="ref-results-sub">
            {journeyDate} · {trains.length} Direct Train{trains.length === 1 ? "" : "s"} Found
          </p>
        </div>

        {activeRequest && (
          <div className="ref-results-sort">
            <label>Sort By:</label>
            <select
              value={sortBy}
              onChange={(e) => {
                setSortBy(e.target.value);
                void executeSearch({ ...activeRequest, sortBy: e.target.value }, 0);
              }}
              className="ref-select-sort"
            >
              <option value="DEPARTURE_EARLIEST">Earliest Departure</option>
              <option value="DEPARTURE_LATEST">Latest Departure</option>
              <option value="DURATION_SHORTEST">Fastest Journey</option>
              <option value="ARRIVAL_EARLIEST">Earliest Arrival</option>
            </select>
          </div>
        )}
      </div>

      {loading && (
        <div className="ref-results-loading">
          <div className="exec-dash-spinner" />
          <p>Finding available express & superfast services…</p>
        </div>
      )}

      {error && (
        <div className="ref-results-error">
          <p>{error}</p>
        </div>
      )}

      {!loading && !error && trains.length === 0 && (
        <div className="ref-no-trains">
          <GoogleIcon name="train" size={48} color="#94a3b8" />
          <h3>No direct trains scheduled for this date</h3>
          <p>Try searching for a different date or checking connected routes.</p>
        </div>
      )}

      {!loading && trains.length > 0 && (
        <div className="ref-trains-list">
          {trains.map((train) => (
            <article className="ref-train-card" key={`${train.trainId}-${train.departureAt}`}>
              <div className="ref-train-card-top">
                <div className="ref-train-identity">
                  <span className="ref-train-number">{train.trainNumber}</span>
                  <h3 className="ref-train-name">{train.trainName}</h3>
                </div>
                <div className="ref-train-badges">
                  <span className="ref-badge-category">{train.categoryName}</span>
                  <span className="ref-badge-days">Runs: {dayNames(train.operatingDays)}</span>
                </div>
              </div>

              <div className="ref-train-timeline">
                <div className="ref-timeline-station">
                  <span className="ref-timeline-time">
                    {displayTime(train.departureAt, train.origin.timezone)}
                  </span>
                  <span className="ref-timeline-code">{train.origin.stationCode}</span>
                  <span className="ref-timeline-name">{train.origin.stationName}</span>
                </div>

                <div className="ref-timeline-track">
                  <span className="ref-track-duration">{durationLabel(train.durationMinutes)}</span>
                  <div className="ref-track-line">
                    <span className="ref-track-bullet" />
                    <span className="ref-track-arrow">→</span>
                    <span className="ref-track-bullet" />
                  </div>
                  <span className="ref-track-dist">{train.distanceKm.toLocaleString()} km</span>
                </div>

                <div className="ref-timeline-station text-right">
                  <span className="ref-timeline-time">
                    {displayTime(train.arrivalAt, train.destination.timezone)}
                  </span>
                  <span className="ref-timeline-code">{train.destination.stationCode}</span>
                  <span className="ref-timeline-name">{train.destination.stationName}</span>
                </div>
              </div>

              <div className="ref-train-card-footer">
                <div className="ref-classes-list">
                  {train.coachClasses.map((cls) => (
                    <span className="ref-class-chip" key={cls.code}>
                      <strong>{cls.code}</strong>
                      <small>{cls.name}</small>
                    </span>
                  ))}
                </div>

                <Link
                  href={`/${device}/train-details?trainId=${encodeURIComponent(train.trainId)}&${activeRequest ? requestParams(activeRequest, currentPage).toString() : ""}`}
                  className="ref-book-now-btn"
                >
                  <span>Check Availability & Book</span>
                  <GoogleIcon name="arrow_forward" size={16} />
                </Link>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
