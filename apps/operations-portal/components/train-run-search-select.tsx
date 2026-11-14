"use client";

import { useEffect, useState, useRef } from "react";
import { apiFetch } from "@railconnect/api";

export type TrainRunOption = {
  trainRunId: string;
  trainNumber: string;
  trainName: string;
  serviceDate: string;
  operationalStatus?: string;
  currentStationCode?: string | null;
  currentStationName?: string | null;
  nextStationCode?: string | null;
  nextStationName?: string | null;
};

type Props = {
  selectedTrainRunId: string;
  onSelectRun: (run: TrainRunOption | null) => void;
  label?: string;
  placeholder?: string;
  initialRuns?: TrainRunOption[];
  required?: boolean;
};

export function TrainRunSearchSelect({
  selectedTrainRunId,
  onSelectRun,
  label = "Train Run",
  placeholder = "Search train by number or name (e.g. 12650, Karnataka Express)...",
  initialRuns = [],
  required = false
}: Props) {
  const [query, setQuery] = useState("");
  const [allRuns, setAllRuns] = useState<TrainRunOption[]>(initialRuns);
  const [selectedRun, setSelectedRun] = useState<TrainRunOption | null>(null);
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
    if (initialRuns.length > 0) {
      setAllRuns(initialRuns);
    } else {
      // Fetch active runs if not provided
      void fetchRuns();
    }
  }, [initialRuns]);

  async function fetchRuns() {
    setLoading(true);
    try {
      const res = await apiFetch<TrainRunOption[]>("/api/v1/operations/train-runs?limit=100");
      setAllRuns(res.data ?? []);
    } catch {
      // fallback
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (!selectedTrainRunId) {
      setSelectedRun(null);
      setQuery("");
    } else {
      const match = allRuns.find((r) => r.trainRunId === selectedTrainRunId);
      if (match) {
        setSelectedRun(match);
        setQuery(`${match.trainNumber} - ${match.trainName} (${match.serviceDate})`);
      }
    }
  }, [selectedTrainRunId, allRuns]);

  const filtered = query.trim()
    ? allRuns.filter((r) => {
        const q = query.toLowerCase().trim();
        return (
          r.trainNumber.toLowerCase().includes(q) ||
          r.trainName.toLowerCase().includes(q) ||
          r.serviceDate.includes(q) ||
          (r.currentStationCode && r.currentStationCode.toLowerCase().includes(q))
        );
      })
    : allRuns;

  function handleSelect(run: TrainRunOption) {
    setSelectedRun(run);
    setQuery(`${run.trainNumber} - ${run.trainName} (${run.serviceDate})`);
    setOpen(false);
    onSelectRun(run);
  }

  function handleClear() {
    setSelectedRun(null);
    setQuery("");
    setOpen(false);
    onSelectRun(null);
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
            if (selectedRun) {
              setSelectedRun(null);
              onSelectRun(null);
            }
          }}
          onFocus={() => {
            if (allRuns.length === 0) void fetchRuns();
            setOpen(true);
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
          required={required && !selectedRun}
        />
        {selectedRun && (
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
            title="Clear selection"
          >
            ✕
          </button>
        )}
      </div>

      {loading && (
        <div style={{ position: "absolute", right: "2rem", top: label ? "2rem" : "0.5rem", fontSize: "0.75rem", color: "#38bdf8" }}>
          Loading...
        </div>
      )}

      {open && (
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
            maxHeight: "240px",
            overflowY: "auto"
          }}
        >
          {filtered.length === 0 ? (
            <div style={{ padding: "0.75rem", fontSize: "0.8rem", color: "#94a3b8", textAlign: "center" }}>
              No matching train runs found
            </div>
          ) : (
            filtered.map((run) => (
              <div
                key={run.trainRunId}
                onClick={() => handleSelect(run)}
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
                  <strong style={{ color: "#38bdf8", marginRight: "0.5rem" }}>{run.trainNumber}</strong>
                  <span style={{ color: "#f8fafc", fontWeight: 500 }}>{run.trainName}</span>
                  <div style={{ fontSize: "0.75rem", color: "#94a3b8", marginTop: "2px" }}>
                    Service Date: {run.serviceDate}
                    {run.currentStationCode && ` · Current: ${run.currentStationCode}`}
                  </div>
                </div>
                {run.operationalStatus && (
                  <span
                    style={{
                      fontSize: "0.7rem",
                      padding: "2px 6px",
                      borderRadius: "4px",
                      fontWeight: 700,
                      background:
                        run.operationalStatus === "RUNNING"
                          ? "#065f46"
                          : run.operationalStatus === "DELAYED"
                          ? "#991b1b"
                          : "#334155",
                      color:
                        run.operationalStatus === "RUNNING"
                          ? "#34d399"
                          : run.operationalStatus === "DELAYED"
                          ? "#fca5a5"
                          : "#94a3b8"
                    }}
                  >
                    {run.operationalStatus}
                  </span>
                )}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
