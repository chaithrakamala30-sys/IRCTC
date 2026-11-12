"use client";

import { useState } from "react";
import Link from "next/link";
import { StationAutocomplete } from "./station-autocomplete";
import type { StationSuggestion } from "./train-search-types";

type BookingEngineProps = {
  isMobile?: boolean;
  onSearch: (from: StationSuggestion | null, to: StationSuggestion | null, date: string, coachClass?: string, quota?: string) => void;
  loading?: boolean;
};

export function ReferenceBookingEngine({ isMobile = false, onSearch, loading = false }: BookingEngineProps) {
  const [activeTab, setActiveTab] = useState<"book" | "pnr" | "schedule" | "seat" | "live">("book");
  const [fromStation, setFromStation] = useState<StationSuggestion | null>(null);
  const [toStation, setToStation] = useState<StationSuggestion | null>(null);
  const [fromText, setFromText] = useState("");
  const [toText, setToText] = useState("");
  
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());
  const [journeyDate, setJourneyDate] = useState(today);
  const [coachClass, setCoachClass] = useState("ALL");
  const [quota, setQuota] = useState("GENERAL");

  const [pnrInput, setPnrInput] = useState("");
  const [trainNoInput, setTrainNoInput] = useState("");

  const handleSwap = () => {
    const tempStation = fromStation;
    const tempText = fromText;
    setFromStation(toStation);
    setFromText(toText);
    setToStation(tempStation);
    setToText(tempText);
  };

  const handleFormSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (activeTab === "book") {
      onSearch(fromStation, toStation, journeyDate, coachClass, quota);
    } else if (activeTab === "pnr") {
      window.location.href = `/verify-ticket`;
    } else if (activeTab === "schedule" || activeTab === "live") {
      window.location.href = `/laptop/train-details`;
    }
  };

  return (
    <div className="ref-booking-widget-wrapper">
      {/* 1. Top Sub-Navigation Tabs matching Image 1 & 2 */}
      <div className="ref-widget-tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === "book"}
          className={`ref-widget-tab ${activeTab === "book" ? "is-active" : ""}`}
          onClick={() => setActiveTab("book")}
        >
          <span className="ref-tab-icon">🚆</span>
          <span>Book Ticket</span>
        </button>

        <button
          type="button"
          role="tab"
          aria-selected={activeTab === "pnr"}
          className={`ref-widget-tab ${activeTab === "pnr" ? "is-active" : ""}`}
          onClick={() => setActiveTab("pnr")}
        >
          <span className="ref-tab-icon">🔍</span>
          <span>PNR Status</span>
        </button>

        <button
          type="button"
          role="tab"
          aria-selected={activeTab === "schedule"}
          className={`ref-widget-tab ${activeTab === "schedule" ? "is-active" : ""}`}
          onClick={() => setActiveTab("schedule")}
        >
          <span className="ref-tab-icon">📅</span>
          <span>Train Schedule</span>
        </button>

        <button
          type="button"
          role="tab"
          aria-selected={activeTab === "seat"}
          className={`ref-widget-tab ${activeTab === "seat" ? "is-active" : ""}`}
          onClick={() => setActiveTab("seat")}
        >
          <span className="ref-tab-icon">💺</span>
          <span>Seat Availability</span>
        </button>

        <button
          type="button"
          role="tab"
          aria-selected={activeTab === "live"}
          className={`ref-widget-tab ${activeTab === "live" ? "is-active" : ""}`}
          onClick={() => setActiveTab("live")}
        >
          <span className="ref-tab-icon">📊</span>
          <span>Live Train Status</span>
        </button>
      </div>

      {/* 2. Glassmorphic Booking Form Fields */}
      <form onSubmit={handleFormSubmit} className="ref-booking-form-box">
        {activeTab === "book" ? (
          <div className="ref-form-row">
            {/* From Station */}
            <div className="ref-field-unit ref-from-field">
              <span className="ref-field-caption">From</span>
              <div className="ref-input-inner">
                <span className="ref-input-pin">📍</span>
                <StationAutocomplete
                  id="ref-from-input"
                  label=""
                  onSelect={(st) => {
                    setFromStation(st);
                    setFromText(st.stationName);
                  }}
                  onValueChange={(val) => {
                    setFromText(val);
                    setFromStation(null);
                  }}
                  otherStationId={toStation?.stationId}
                  selectedStation={fromStation}
                  value={fromText}
                />
              </div>
            </div>

            {/* Interchange Button */}
            <button
              type="button"
              className="ref-swap-btn"
              onClick={handleSwap}
              aria-label="Swap departure and arrival stations"
            >
              ⇄
            </button>

            {/* To Station */}
            <div className="ref-field-unit ref-to-field">
              <span className="ref-field-caption">To</span>
              <div className="ref-input-inner">
                <span className="ref-input-pin">📍</span>
                <StationAutocomplete
                  id="ref-to-input"
                  label=""
                  onSelect={(st) => {
                    setToStation(st);
                    setToText(st.stationName);
                  }}
                  onValueChange={(val) => {
                    setToText(val);
                    setToStation(null);
                  }}
                  otherStationId={fromStation?.stationId}
                  selectedStation={toStation}
                  value={toText}
                />
              </div>
            </div>

            {/* Date of Journey */}
            <div className="ref-field-unit ref-date-field">
              <span className="ref-field-caption">Date of Journey</span>
              <div className="ref-input-inner">
                <span className="ref-input-pin">📅</span>
                <input
                  type="date"
                  required
                  min={today}
                  value={journeyDate}
                  onChange={(e) => setJourneyDate(e.target.value)}
                  className="ref-date-picker"
                />
              </div>
            </div>

            {/* Travel Class */}
            <div className="ref-field-unit ref-class-field">
              <span className="ref-field-caption">Class</span>
              <div className="ref-input-inner">
                <span className="ref-input-pin">💺</span>
                <select
                  value={coachClass}
                  onChange={(e) => setCoachClass(e.target.value)}
                  className="ref-select-picker"
                >
                  <option value="ALL">All Classes</option>
                  <option value="1A">Anubhuti / 1A</option>
                  <option value="2A">AC 2 Tier (2A)</option>
                  <option value="3A">AC 3 Tier (3A)</option>
                  <option value="CC">AC Chair Car (CC)</option>
                  <option value="SL">Sleeper (SL)</option>
                </select>
              </div>
            </div>

            {/* Quota */}
            <div className="ref-field-unit ref-quota-field">
              <span className="ref-field-caption">Quota</span>
              <div className="ref-input-inner">
                <span className="ref-input-pin">🏷️</span>
                <select
                  value={quota}
                  onChange={(e) => setQuota(e.target.value)}
                  className="ref-select-picker"
                >
                  <option value="GENERAL">General</option>
                  <option value="TATKAL">Tatkal</option>
                  <option value="LADIES">Ladies</option>
                  <option value="LOWER_BERTH">Lower Berth / Sr. Citizen</option>
                  <option value="PREMIUM_TATKAL">Premium Tatkal</option>
                </select>
              </div>
            </div>

            {/* Search Trains CTA Button */}
            <div className="ref-cta-unit">
              <button
                type="submit"
                disabled={loading}
                className="ref-search-btn"
              >
                {loading ? "Searching…" : "Search Trains →"}
              </button>
            </div>
          </div>
        ) : (
          /* Alternate Quick Tab View (PNR, Schedule, etc.) */
          <div className="ref-alt-tab-row">
            <div className="ref-alt-input-box">
              <label className="ref-field-caption">
                {activeTab === "pnr"
                  ? "Enter 10-Digit PNR Number"
                  : "Enter Train Number or Name"}
              </label>
              <input
                type="text"
                placeholder={activeTab === "pnr" ? "e.g. 2765418793" : "e.g. 12951 - Rajdhani Express"}
                value={activeTab === "pnr" ? pnrInput : trainNoInput}
                onChange={(e) => activeTab === "pnr" ? setPnrInput(e.target.value) : setTrainNoInput(e.target.value)}
                className="ref-alt-text-input"
                required
              />
            </div>
            <button type="submit" className="ref-search-btn">
              {activeTab === "pnr" ? "Check Status →" : "Find Train →"}
            </button>
          </div>
        )}
      </form>
    </div>
  );
}

