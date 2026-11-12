"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { ApiRequestError, apiFetch } from "../lib/api";
import { StationAutocomplete } from "./station-autocomplete";
import { RailwayGlossaryTooltip } from "./railway-glossary-tooltip";
import { LocomotiveHeroCanvas } from "./locomotive-hero-canvas";
import { QuickServicesDock } from "./quick-services-dock";
import { IndianRailwaysShowcase } from "./indian-railways-showcase";
import type {
  SearchFilters,
  SearchPage,
  SearchRequest,
  StationSuggestion,
  TrainSearchResult,
} from "./train-search-types";

type DeviceExperience = "mobile" | "laptop";
type Filters = Omit<SearchRequest, "fromStationId" | "toStationId" | "journeyDate" | "sortBy">;
type RecentSearch = {
  from: StationSuggestion;
  to: StationSuggestion;
  journeyDate: string;
};

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

function todayInIndia(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());
}

function addDays(date: string, days: number): string {
  const [year, month, day] = date.split("-").map(Number);
  const result = new Date(Date.UTC(year, month - 1, day + days));
  return [
    result.getUTCFullYear(),
    String(result.getUTCMonth() + 1).padStart(2, "0"),
    String(result.getUTCDate()).padStart(2, "0"),
  ].join("-");
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

function displayTime(value: string, timezone: string): string {
  return new Intl.DateTimeFormat(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    timeZone: timezone,
  }).format(new Date(value));
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

function searchError(error: unknown): string {
  return error instanceof ApiRequestError
    ? error.message
    : "Train search could not be completed. Please try again.";
}

export function TrainSearchExperience({ device }: { device: DeviceExperience }) {
  const today = useMemo(todayInIndia, []);
  const [maximumDays, setMaximumDays] = useState(365);
  const [fromStation, setFromStation] = useState<StationSuggestion | null>(null);
  const [toStation, setToStation] = useState<StationSuggestion | null>(null);
  const [fromText, setFromText] = useState("");
  const [toText, setToText] = useState("");
  const [journeyDate, setJourneyDate] = useState(today);
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
  const [formError, setFormError] = useState("");
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [recentSearches, setRecentSearches] = useState<RecentSearch[]>([]);

  const executeSearch = useCallback(async (request: SearchRequest, page = 0) => {
    setLoading(true);
    setError("");
    setFormError("");
    setResults(null);
    setActiveRequest(request);
    setCurrentPage(page);
    const params = requestParams(request, page);
    const path = `/api/v1/public/trains/search?${params}`;
    window.history.replaceState(null, "", `/${device}?${params}`);
    try {
      const response = await apiFetch<SearchPage<TrainSearchResult>>(path);
      setResults(response.data);
      if (response.data) setCurrentPage(response.data.page);
    } catch (caught) {
      setError(searchError(caught));
    } finally {
      setLoading(false);
    }
  }, [device]);

  useEffect(() => {
    let active = true;
    void Promise.all([
      apiFetch<{ maximumDays: number }>("/api/v1/public/search-config"),
      apiFetch<SearchFilters>("/api/v1/public/trains/filters"),
    ]).then(([configResponse, filtersResponse]) => {
      if (!active) return;
      if (configResponse.data) setMaximumDays(configResponse.data.maximumDays);
      if (filtersResponse.data) setFilterOptions(filtersResponse.data);
    }).catch((caught: unknown) => {
      if (active) setError(searchError(caught));
    });

    try {
      const stored: unknown = JSON.parse(window.localStorage.getItem("railconnect.recent-searches") ?? "[]");
      if (Array.isArray(stored)) {
        setRecentSearches(stored.slice(0, 3) as RecentSearch[]);
      }
    } catch {
      setRecentSearches([]);
    }

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    const params = new URLSearchParams(window.location.search);
    const fromId = params.get("fromStationId");
    const toId = params.get("toStationId");
    const date = params.get("journeyDate");
    if (!fromId || !toId || !date) return;

    void Promise.all([
      apiFetch<StationSuggestion>(`/api/v1/public/stations/${encodeURIComponent(fromId)}`),
      apiFetch<StationSuggestion>(`/api/v1/public/stations/${encodeURIComponent(toId)}`),
    ]).then(([fromResponse, toResponse]) => {
      if (cancelled || !fromResponse.data || !toResponse.data) return;
      const from = fromResponse.data;
      const to = toResponse.data;
      setFromStation(from);
      setToStation(to);
      setFromText(from.stationName);
      setToText(to.stationName);
      setJourneyDate(date);
      const request: SearchRequest = {
        fromStationId: from.stationId,
        toStationId: to.stationId,
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
      setFilters({
        trainCategory: request.trainCategory,
        coachClass: request.coachClass,
        departureFrom: request.departureFrom,
        departureTo: request.departureTo,
        arrivalFrom: request.arrivalFrom,
        arrivalTo: request.arrivalTo,
        maximumDuration: request.maximumDuration,
        q: request.q,
      });
      setSortBy(request.sortBy);
      void executeSearch(request, Number(params.get("page")) || 0);
    }).catch((caught: unknown) => {
      if (!cancelled) setError(searchError(caught));
    });

    return () => {
      cancelled = true;
    };
  }, [executeSearch]);

  function rememberSearch(request: SearchRequest) {
    if (!fromStation || !toStation) return;
    const next = [
      { from: fromStation, to: toStation, journeyDate: request.journeyDate },
      ...recentSearches.filter((recent) =>
        recent.from.stationId !== fromStation.stationId
        || recent.to.stationId !== toStation.stationId
        || recent.journeyDate !== request.journeyDate),
    ].slice(0, 3);
    setRecentSearches(next);
    try {
      window.localStorage.setItem("railconnect.recent-searches", JSON.stringify(next));
    } catch {
      setError("This browser could not save recent searches. Your search still works.");
    }
  }

  function selectedRequest(sort = sortBy): SearchRequest | null {
    if (!fromStation || !toStation) {
      setFormError("Choose both stations from the suggestion list.");
      return null;
    }
    if (fromStation.stationId === toStation.stationId) {
      setFormError("Origin and destination must be different stations.");
      return null;
    }
    if (journeyDate < today) {
      setFormError("Choose today or a future journey date.");
      return null;
    }
    if (journeyDate > addDays(today, maximumDays)) {
      setFormError(`Choose a date within ${maximumDays} days.`);
      return null;
    }
    return {
      fromStationId: fromStation.stationId,
      toStationId: toStation.stationId,
      journeyDate,
      ...filters,
      sortBy: sort,
    };
  }

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const request = selectedRequest();
    if (request) {
      rememberSearch(request);
      void executeSearch(request);
    }
  }

  function applyFilters(nextFilters: Filters) {
    setFilters(nextFilters);
    if (!activeRequest) return;
    void executeSearch({ ...activeRequest, ...nextFilters, sortBy }, 0);
  }

  function removeFilter(key: keyof Filters) {
    const next = { ...filters, [key]: key === "maximumDuration" ? undefined : "" };
    applyFilters(next);
  }

  function swapStations() {
    setFromStation(toStation);
    setToStation(fromStation);
    setFromText(toStation?.stationName ?? "");
    setToText(fromStation?.stationName ?? "");
    setResults(null);
  }

  const dateMaximum = addDays(today, maximumDays);
  const totalPages = results ? Math.ceil(results.totalItems / PAGE_SIZE) : 0;
  const filterChips = useMemo(() => {
    if (!activeRequest) return [];
    return [
      ["trainCategory", activeRequest.trainCategory],
      ["coachClass", activeRequest.coachClass],
      ["departureFrom", activeRequest.departureFrom],
      ["departureTo", activeRequest.departureTo],
      ["arrivalFrom", activeRequest.arrivalFrom],
      ["arrivalTo", activeRequest.arrivalTo],
      ["maximumDuration", activeRequest.maximumDuration ? `${activeRequest.maximumDuration} min max` : ""],
      ["q", activeRequest.q],
    ].filter((entry): entry is [keyof Filters, string] => Boolean(entry[1]));
  }, [activeRequest]);

  return (
    <div className={`train-search-page train-search-page--${device}`}>
      <section aria-labelledby="train-search-title" className="train-search-hero">
        <LocomotiveHeroCanvas isMobile={device === "mobile"} />
        <div className="train-search-hero-copy">
          <p className="eyebrow">National Railway Portal</p>
          <h1 id="train-search-title">Book Indian Railways Express & Superfast Trains</h1>
          <p>Authoritative reservation, real-time seat inventory, and dynamic fare engine across the national rail network.</p>
        </div>
      </section>

      <QuickServicesDock />

      <form className="journey-search-form" onSubmit={submitSearch}>
        <div className="station-fields">
          <StationAutocomplete
            id={`${device}-from-station`}
            label="From station"
            onSelect={(station) => {
              setFromStation(station);
              setFromText(station.stationName);
              setFormError("");
            }}
            onValueChange={(value) => {
              setFromText(value);
              setFromStation(null);
              setResults(null);
              setActiveRequest(null);
            }}
            otherStationId={toStation?.stationId}
            selectedStation={fromStation}
            value={fromText}
          />
          <button
            aria-label="Swap origin and destination"
            className="station-swap"
            onClick={swapStations}
            type="button"
          >
            ⇄
          </button>
          <StationAutocomplete
            id={`${device}-to-station`}
            label="To station"
            onSelect={(station) => {
              setToStation(station);
              setToText(station.stationName);
              setFormError("");
            }}
            onValueChange={(value) => {
              setToText(value);
              setToStation(null);
              setResults(null);
              setActiveRequest(null);
            }}
            otherStationId={fromStation?.stationId}
            selectedStation={toStation}
            value={toText}
          />
        </div>
        <div className="journey-search-actions">
          <label className="journey-date-field" htmlFor={`${device}-journey-date`}>
            Journey date
            <input
              id={`${device}-journey-date`}
              max={dateMaximum}
              min={today}
              onChange={(event) => {
                setJourneyDate(event.target.value);
                setResults(null);
                setActiveRequest(null);
              }}
              required
              type="date"
              value={journeyDate}
            />
          </label>
          <button className="button button--primary journey-search-button" disabled={loading} type="submit">
            {loading ? "Searching…" : "Search trains"}
          </button>
          <button
            className="button button--secondary"
            onClick={() => {
              setFromStation(null);
              setToStation(null);
              setFromText("");
              setToText("");
              setJourneyDate(today);
              setFilters(EMPTY_FILTERS);
              setActiveRequest(null);
              setResults(null);
              setError("");
              setFormError("");
              window.history.replaceState(null, "", `/${device}`);
            }}
            type="button"
          >
            Clear search
          </button>
        </div>
        <p className="search-horizon-note">Search up to {maximumDays} days ahead. Times use each station&apos;s local timezone.</p>
        <RailwayGlossaryTooltip />
        {formError && <p className="search-error" role="alert">{formError}</p>}
      </form>

      {recentSearches.length > 0 && (
        <section aria-label="Recent searches" className="recent-searches">
          <h2>Recent searches</h2>
          <div className="recent-search-list">
            {recentSearches.map((recent) => (
              <button
                key={`${recent.from.stationId}-${recent.to.stationId}-${recent.journeyDate}`}
                onClick={() => {
                  setFromStation(recent.from);
                  setToStation(recent.to);
                  setFromText(recent.from.stationName);
                  setToText(recent.to.stationName);
                  setJourneyDate(recent.journeyDate);
                  void executeSearch({
                    fromStationId: recent.from.stationId,
                    toStationId: recent.to.stationId,
                    journeyDate: recent.journeyDate,
                    sortBy,
                  });
                }}
                type="button"
              >
                {recent.from.stationCode} → {recent.to.stationCode} · {recent.journeyDate}
              </button>
            ))}
          </div>
        </section>
      )}

      {(activeRequest || loading || error || results) && (
        <section aria-labelledby="search-results-heading" className="search-results-section">
          <div className="search-results-heading">
            <div>
              <p className="eyebrow">Published services</p>
              <h2 id="search-results-heading">
                {activeRequest && fromStation && toStation
                  ? `${fromStation.stationName} to ${toStation.stationName}`
                  : "Train search results"}
              </h2>
              {activeRequest && <p>{activeRequest.journeyDate} · Direct trains only</p>}
            </div>
            {activeRequest && (
              <label className="sort-control">
                Sort by
                <select
                  onChange={(event) => {
                    setSortBy(event.target.value);
                    void executeSearch({ ...activeRequest, sortBy: event.target.value }, 0);
                  }}
                  value={sortBy}
                >
                  <option value="DEPARTURE_EARLIEST">Earliest departure</option>
                  <option value="DEPARTURE_LATEST">Latest departure</option>
                  <option value="ARRIVAL_EARLIEST">Earliest arrival</option>
                  <option value="DURATION_SHORTEST">Shortest journey</option>
                  <option value="DURATION_LONGEST">Longest journey</option>
                  <option value="TRAIN_NUMBER">Train number</option>
                </select>
              </label>
            )}
          </div>
          {error && <p className="search-error" role="alert">{error}</p>}
          {activeRequest && (
            <div className="results-layout">
              <aside aria-label="Train search filters" className={`train-filter-panel${filtersOpen ? " is-open" : ""}`}>
                <div className="filter-panel-heading">
                  <h3>Filters</h3>
                  <button
                    aria-label="Close filters"
                    className="mobile-filter-close"
                    onClick={() => setFiltersOpen(false)}
                    type="button"
                  >
                    Close
                  </button>
                </div>
                <label>
                  Train category
                  <select
                    onChange={(event) => setFilters({ ...filters, trainCategory: event.target.value })}
                    value={filters.trainCategory ?? ""}
                  >
                    <option value="">All categories</option>
                    {filterOptions.trainCategories.map((category) => (
                      <option key={category.code} value={category.code}>{category.name}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Coach class
                  <select
                    onChange={(event) => setFilters({ ...filters, coachClass: event.target.value })}
                    value={filters.coachClass ?? ""}
                  >
                    <option value="">All classes</option>
                    {filterOptions.coachClasses.map((coachClass) => (
                      <option key={coachClass.code} value={coachClass.code}>
                        {coachClass.name} ({coachClass.coachCount})
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Train number or name
                  <input
                    maxLength={80}
                    onChange={(event) => setFilters({ ...filters, q: event.target.value })}
                    placeholder="Search train"
                    value={filters.q ?? ""}
                  />
                </label>
                <fieldset>
                  <legend>Departure time</legend>
                  <div className="time-filter-fields">
                    <label>From <input onChange={(event) => setFilters({ ...filters, departureFrom: event.target.value })} type="time" value={filters.departureFrom ?? ""} /></label>
                    <label>To <input onChange={(event) => setFilters({ ...filters, departureTo: event.target.value })} type="time" value={filters.departureTo ?? ""} /></label>
                  </div>
                </fieldset>
                <fieldset>
                  <legend>Arrival time</legend>
                  <div className="time-filter-fields">
                    <label>From <input onChange={(event) => setFilters({ ...filters, arrivalFrom: event.target.value })} type="time" value={filters.arrivalFrom ?? ""} /></label>
                    <label>To <input onChange={(event) => setFilters({ ...filters, arrivalTo: event.target.value })} type="time" value={filters.arrivalTo ?? ""} /></label>
                  </div>
                </fieldset>
                <label>
                  Maximum journey duration (minutes)
                  <input
                    max={10080}
                    min={1}
                    onChange={(event) => setFilters({
                      ...filters,
                      maximumDuration: event.target.value ? Number(event.target.value) : undefined,
                    })}
                    placeholder="Any duration"
                    type="number"
                    value={filters.maximumDuration ?? ""}
                  />
                </label>
                <div className="filter-panel-actions">
                  <button
                    className="button button--primary"
                    onClick={() => {
                      applyFilters(filters);
                      setFiltersOpen(false);
                    }}
                    type="button"
                  >
                    Apply filters
                  </button>
                  <button
                    className="button button--secondary"
                    onClick={() => {
                      applyFilters(EMPTY_FILTERS);
                      setFiltersOpen(false);
                    }}
                    type="button"
                  >
                    Clear all
                  </button>
                </div>
              </aside>
              <div aria-live="polite" className="train-results" aria-busy={loading}>
                <div className="results-toolbar">
                  <p>{loading ? "Updating results…" : `${results?.totalItems ?? 0} trains found`}</p>
                  <button
                    aria-expanded={filtersOpen}
                    className="button button--secondary mobile-filter-button"
                    onClick={() => setFiltersOpen(true)}
                    type="button"
                  >
                    Filters
                  </button>
                </div>
                {filterChips.length > 0 && (
                  <div aria-label="Active filters" className="active-filter-chips">
                    {filterChips.map(([key, value]) => (
                      <button key={key} onClick={() => removeFilter(key)} type="button">
                        {key}: {value} <span aria-hidden="true">×</span>
                      </button>
                    ))}
                  </div>
                )}
                {loading && <div className="search-loading" role="status">Searching published railway schedules…</div>}
                {!loading && results?.totalItems === 0 && (
                  <div className="no-trains-card">
                    <h3>No direct trains found</h3>
                    <p>Try another date or remove one or more filters. Only active trains with published schedules are shown.</p>
                  </div>
                )}
                {!loading && (results?.items ?? []).map((train) => (
                  <article className="train-result-card" key={`${train.trainId}-${train.departureAt}`}>
                    <div className="train-result-title">
                      <div><span className="train-number">{train.trainNumber}</span><h3>{train.trainName}</h3></div>
                      <span className="category-badge">{train.categoryName}</span>
                    </div>
                    <div className="train-journey-timeline">
                      <div className="journey-point">
                        <time dateTime={train.departureAt}>{displayTime(train.departureAt, train.origin.timezone)}</time>
                        <strong>{train.origin.stationCode}</strong>
                        <span>{train.origin.stationName}</span>
                        <small>{train.journeyDate}</small>
                      </div>
                      <div className="journey-duration">
                        <span>{durationLabel(train.durationMinutes)}</span>
                        <span aria-hidden="true" className="journey-line" />
                        <small>{train.distanceKm.toLocaleString()} km</small>
                      </div>
                      <div className="journey-point journey-point--arrival">
                        <time dateTime={train.arrivalAt}>{displayTime(train.arrivalAt, train.destination.timezone)}</time>
                        <strong>{train.destination.stationCode}</strong>
                        <span>{train.destination.stationName}</span>
                        <small>{train.arrivalDate}{train.arrivalDate !== train.journeyDate ? " · next day or later" : ""}</small>
                      </div>
                    </div>
                    <div className="train-result-meta">
                      <span>Runs: {dayNames(train.operatingDays)}</span>
                      <span>{train.intermediateStops} intermediate stops</span>
                      <span>
                        Classes: {train.coachClasses.length
                          ? train.coachClasses.map((coachClass) => `${coachClass.code} (${coachClass.coachCount})`).join(", ")
                          : "Not configured"}
                      </span>
                    </div>
                    <div className="train-result-actions">
                      <Link
                        className="button button--secondary"
                        href={`/${device}/train-details?trainId=${encodeURIComponent(train.trainId)}&${requestParams(activeRequest, currentPage)}`}
                      >
                        View full route
                      </Link>
                      <span className="no-booking-note">Schedule information only · no fares or seat availability</span>
                    </div>
                  </article>
                ))}
                {totalPages > 1 && (
                  <nav aria-label="Search result pages" className="search-pagination">
                    <button
                      disabled={loading || currentPage <= 0}
                      onClick={() => void executeSearch(activeRequest, currentPage - 1)}
                      type="button"
                    >
                      Previous
                    </button>
                    <span>Page {currentPage + 1} of {totalPages}</span>
                    <button
                      disabled={loading || currentPage + 1 >= totalPages}
                      onClick={() => void executeSearch(activeRequest, currentPage + 1)}
                      type="button"
                    >
                      Next
                    </button>
                  </nav>
                )}
              </div>
            </div>
          )}
        </section>
      )}

      <IndianRailwaysShowcase />
    </div>
  );
}
