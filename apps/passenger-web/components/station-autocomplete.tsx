"use client";

import { useEffect, useId, useState, type KeyboardEvent } from "react";
import { ApiRequestError, apiFetch } from "../lib/api";
import type { StationSuggestion } from "./train-search-types";

type StationAutocompleteProps = {
  id: string;
  label: string;
  value: string;
  selectedStation: StationSuggestion | null;
  otherStationId?: string;
  onValueChange: (value: string) => void;
  onSelect: (station: StationSuggestion) => void;
};

export function StationAutocomplete({
  id,
  label,
  value,
  selectedStation,
  otherStationId,
  onValueChange,
  onSelect,
}: StationAutocompleteProps) {
  const listId = useId();
  const [suggestions, setSuggestions] = useState<StationSuggestion[]>([]);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const query = value.trim();
    if (query.length < 2 || selectedStation?.stationName === query) {
      setSuggestions([]);
      setOpen(false);
      setLoading(false);
      setError("");
      return;
    }

    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setLoading(true);
      setError("");
      try {
        const params = new URLSearchParams({ q: query, limit: "10" });
        const response = await apiFetch<StationSuggestion[]>(
          `/api/v1/public/stations/search?${params}`,
          { signal: controller.signal },
        );
        const unique = new Map(
          (response.data ?? []).map((station) => [station.stationId, station]),
        );
        setSuggestions([...unique.values()]);
        setOpen(true);
        setActiveIndex(-1);
      } catch (caught) {
        if (controller.signal.aborted) return;
        setError(
          caught instanceof ApiRequestError
            ? caught.message
            : "Station suggestions could not be loaded.",
        );
        setSuggestions([]);
        setOpen(false);
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }, 250);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [value, selectedStation?.stationName]);

  function choose(station: StationSuggestion) {
    if (station.stationId === otherStationId) {
      setError("Choose a different station for each end of the journey.");
      return;
    }
    onSelect(station);
    setOpen(false);
    setSuggestions([]);
    setError("");
  }

  const visibleSuggestions = suggestions.filter(
    (station) => station.stationId !== otherStationId,
  );

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "ArrowDown" && visibleSuggestions.length > 0) {
      event.preventDefault();
      setOpen(true);
      setActiveIndex((index) => (index + 1) % visibleSuggestions.length);
    } else if (event.key === "ArrowUp" && visibleSuggestions.length > 0) {
      event.preventDefault();
      setOpen(true);
      setActiveIndex((index) => index <= 0 ? visibleSuggestions.length - 1 : index - 1);
    } else if (event.key === "Enter" && open && activeIndex >= 0) {
      event.preventDefault();
      choose(visibleSuggestions[activeIndex]);
    } else if (event.key === "Escape") {
      setOpen(false);
    }
  }

  const showList = open && value.trim().length >= 2;

  return (
    <div className="station-picker">
      <label htmlFor={id}>{label}</label>
      <input
        aria-activedescendant={
          showList && activeIndex >= 0
            ? `${listId}-option-${activeIndex}`
            : undefined
        }
        aria-autocomplete="list"
        aria-controls={listId}
        aria-expanded={showList}
        aria-haspopup="listbox"
        autoComplete="off"
        id={id}
        onChange={(event) => {
          onValueChange(event.target.value);
          setOpen(true);
        }}
        onFocus={() => {
          if (suggestions.length > 0) setOpen(true);
        }}
        onKeyDown={handleKeyDown}
        role="combobox"
        value={value}
      />
      {selectedStation && (
        <span className="selected-station">
          Selected: {selectedStation.stationName} ({selectedStation.stationCode})
        </span>
      )}
      {loading && <span className="station-picker-hint" role="status">Finding stations…</span>}
      {error && <span className="field-error" role="alert">{error}</span>}
      {showList && (
        <ul className="station-suggestions" id={listId} role="listbox">
          {visibleSuggestions.map((station, index) => (
            <li
              aria-selected={activeIndex === index}
              id={`${listId}-option-${index}`}
              key={station.stationId}
              role="option"
            >
              <button
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => choose(station)}
                type="button"
              >
                <span><strong>{station.stationName}</strong> <b>{station.stationCode}</b></span>
                <small>{station.city}, {station.state}</small>
              </button>
            </li>
          ))}
          {!loading && visibleSuggestions.length === 0 && (
            <li className="station-suggestions-empty" role="presentation">
              No active station matched that search.
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
