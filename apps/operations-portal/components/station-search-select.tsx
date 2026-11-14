"use client";

import { useEffect, useState, useRef } from "react";
import { apiFetch } from "@railconnect/api";

export type StationSuggestion = {
  stationId: string;
  stationCode: string;
  stationName: string;
  city?: string;
  state?: string;
};

type Props = {
  selectedStationId: string;
  onSelectStation: (station: StationSuggestion | null) => void;
  label?: string;
  placeholder?: string;
  required?: boolean;
};

export function StationSearchSelect({
  selectedStationId,
  onSelectStation,
  label = "Station",
  placeholder = "Search station by name or code (e.g. NDLS, New Delhi)...",
  required = false
}: Props) {
  const [query, setQuery] = useState("");
  const [suggestions, setSuggestions] = useState<StationSuggestion[]>([]);
  const [selectedStation, setSelectedStation] = useState<StationSuggestion | null>(null);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  useEffect(() => {
    if (!selectedStationId) {
      setSelectedStation(null);
      setQuery("");
    }
  }, [selectedStationId]);

  useEffect(() => {
    const trimmed = query.trim();
    if (trimmed.length < 2 || (selectedStation && `${selectedStation.stationCode} - ${selectedStation.stationName}` === query)) {
      setSuggestions([]);
      return;
    }

    const timer = setTimeout(async () => {
      setLoading(true);
      try {
        const res = await apiFetch<StationSuggestion[]>(`/api/v1/public/stations/search?q=${encodeURIComponent(trimmed)}&limit=10`);
        setSuggestions(res.data ?? []);
        setOpen(true);
      } catch {
        setSuggestions([]);
      } finally {
        setLoading(false);
      }
    }, 250);

    return () => clearTimeout(timer);
  }, [query, selectedStation]);

  function handleSelect(station: StationSuggestion) {
    setSelectedStation(station);
    setQuery(`${station.stationCode} - ${station.stationName}`);
    setOpen(false);
    onSelectStation(station);
  }

  function handleClear() {
    setSelectedStation(null);
    setQuery("");
    setSuggestions([]);
    setOpen(false);
    onSelectStation(null);
  }

  return (
    <div ref={containerRef} style={{ position: "relative", width: "100%" }}>
      {label && (
        <label style={{ fontSize: "0.8rem", color: "#94a3b8", display: "block", marginBottom: "0.25rem" }}>
          {label} {required && <span style={{ color: "#ef4444" }}>*</span>}
        </label>
      )}
      <div style={{ position: "relative", display: "flex", alignItems: "center" }}>
        <input
          type="text"
          value={query}
          placeholder={placeholder}
          onChange={(e) => {
            setQuery(e.target.value);
            if (!open) setOpen(true);
            if (selectedStation) {
              setSelectedStation(null);
              onSelectStation(null);
            }
          }}
          onFocus={() => {
            if (suggestions.length > 0) setOpen(true);
          }}
          style={{
            width: "100%",
            padding: "0.55rem 2rem 0.55rem 0.65rem",
            borderRadius: "6px",
            border: "1px solid #475569",
            background: "#0f172a",
            color: "#f8fafc",
            fontSize: "0.85rem",
            outline: "none"
          }}
          required={required && !selectedStation}
        />
        {selectedStation && (
          <button
            type="button"
            onClick={handleClear}
            style={{
              position: "absolute",
              right: "0.5rem",
              background: "transparent",
              border: "none",
              color: "#94a3b8",
              cursor: "pointer",
              fontSize: "0.9rem",
              lineHeight: 1
            }}
            title="Clear station"
          >
            ✕
          </button>
        )}
      </div>

      {loading && (
        <div style={{ position: "absolute", right: "2rem", top: label ? "2rem" : "0.5rem", fontSize: "0.75rem", color: "#38bdf8" }}>
          Searching...
        </div>
      )}

      {open && suggestions.length > 0 && (
        <div
          style={{
            position: "absolute",
            top: "100%",
            left: 0,
            right: 0,
            zIndex: 50,
            marginTop: "4px",
            background: "#1e293b",
            border: "1px solid #475569",
            borderRadius: "6px",
            boxShadow: "0 10px 25px -5px rgba(0, 0, 0, 0.5)",
            maxHeight: "220px",
            overflowY: "auto"
          }}
        >
          {suggestions.map((st) => (
            <div
              key={st.stationId}
              onClick={() => handleSelect(st)}
              style={{
                padding: "0.6rem 0.75rem",
                cursor: "pointer",
                borderBottom: "1px solid #334155",
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center"
              }}
              onMouseEnter={(e) => (e.currentTarget.style.background = "#334155")}
              onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
            >
              <div>
                <strong style={{ color: "#38bdf8", marginRight: "0.5rem" }}>{st.stationCode}</strong>
                <span style={{ color: "#f8fafc" }}>{st.stationName}</span>
              </div>
              {st.city && <span style={{ fontSize: "0.75rem", color: "#94a3b8" }}>{st.city}</span>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
