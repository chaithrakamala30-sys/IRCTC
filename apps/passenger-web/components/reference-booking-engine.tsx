"use client";

import { useState } from "react";
import { StationAutocomplete } from "./station-autocomplete";
import type { StationSuggestion } from "./train-search-types";
import { GoogleIcon } from "./google-icon";

type BookingEngineProps = {
  isMobile?: boolean;
  onSearch: (
    from: StationSuggestion | null,
    to: StationSuggestion | null,
    date: string,
    coachClass?: string,
    quota?: string
  ) => void;
  loading?: boolean;
};

export function ReferenceBookingEngine({
  isMobile = false,
  onSearch,
  loading = false,
}: BookingEngineProps) {
  const [activeTab, setActiveTab] = useState<"book" | "pnr" | "schedule" | "seat" | "live">("book");
  const [fromStation, setFromStation] = useState<StationSuggestion | null>(null);
  const [toStation, setToStation] = useState<StationSuggestion | null>(null);
  const [fromText, setFromText] = useState("");
  const [toText, setToText] = useState("");

  const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());
  const [journeyDate, setJourneyDate] = useState(today);
  const [coachClass, setCoachClass] = useState("ALL");
  const [quota, setQuota] = useState("GENERAL");

  // Sub-features state
  const [pnrInput, setPnrInput] = useState("");
  const [scheduleTrainInput, setScheduleTrainInput] = useState("");
  const [seatTrainInput, setSeatTrainInput] = useState("");
  const [seatDate, setSeatDate] = useState(today);
  const [seatClass, setSeatClass] = useState("ALL");
  const [seatQuota, setSeatQuota] = useState("GENERAL");

  const [liveTrainInput, setLiveTrainInput] = useState("");
  const [liveDay, setLiveDay] = useState<"yesterday" | "today" | "tomorrow">("today");

  const handleSwap = () => {
    const tempStation = fromStation;
    const tempText = fromText;
    setFromStation(toStation);
    setFromText(toText);
    setToStation(tempStation);
    setToText(tempText);
  };

  const handleBookSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (fromStation && toStation) {
      try {
        const stored = JSON.parse(window.localStorage.getItem("railconnect.recent-searches") ?? "[]");
        const next = [
          { from: fromStation, to: toStation, journeyDate },
          ...(Array.isArray(stored) ? stored : []).filter(
            (r: { from?: { stationId?: string }; to?: { stationId?: string }; journeyDate?: string }) =>
              r?.from?.stationId !== fromStation.stationId ||
              r?.to?.stationId !== toStation.stationId ||
              r?.journeyDate !== journeyDate
          ),
        ].slice(0, 5);
        window.localStorage.setItem("railconnect.recent-searches", JSON.stringify(next));
      } catch {
        // Ignore storage errors
      }
    }
    onSearch(fromStation, toStation, journeyDate, coachClass, quota);
  };

  const handlePnrSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const clean = pnrInput.replace(/\D/g, "");
    if (clean) {
      window.location.href = `/verify-ticket?pnr=${encodeURIComponent(clean)}`;
    } else {
      window.location.href = `/verify-ticket`;
    }
  };

  const handleScheduleSubmit = (e: React.FormEvent, trainCode?: string) => {
    if (e) e.preventDefault();
    const query = (trainCode || scheduleTrainInput).trim();
    if (query) {
      try {
        const stored = JSON.parse(window.localStorage.getItem("railconnect.recent-train-queries") ?? "[]");
        const next = [query, ...(Array.isArray(stored) ? stored : []).filter((q: string) => q !== query)].slice(0, 5);
        window.localStorage.setItem("railconnect.recent-train-queries", JSON.stringify(next));
      } catch {}
      window.location.href = `/laptop/train-details?trainNo=${encodeURIComponent(query)}`;
    } else {
      window.location.href = `/laptop/train-details`;
    }
  };

  const handleSeatSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const query = seatTrainInput.trim();
    if (query) {
      try {
        const stored = JSON.parse(window.localStorage.getItem("railconnect.recent-train-queries") ?? "[]");
        const next = [query, ...(Array.isArray(stored) ? stored : []).filter((q: string) => q !== query)].slice(0, 5);
        window.localStorage.setItem("railconnect.recent-train-queries", JSON.stringify(next));
      } catch {}
    }
    const params = new URLSearchParams();
    if (query) params.set("query", query);
    if (seatDate) params.set("journeyDate", seatDate);
    if (seatClass && seatClass !== "ALL") params.set("coachClass", seatClass);
    if (seatQuota && seatQuota !== "GENERAL") params.set("quota", seatQuota);
    window.location.href = `/laptop?${params.toString()}#results`;
  };

  const handleLiveSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const query = liveTrainInput.trim();
    if (query) {
      try {
        const stored = JSON.parse(window.localStorage.getItem("railconnect.recent-train-queries") ?? "[]");
        const next = [query, ...(Array.isArray(stored) ? stored : []).filter((q: string) => q !== query)].slice(0, 5);
        window.localStorage.setItem("railconnect.recent-train-queries", JSON.stringify(next));
      } catch {}
      window.location.href = `/laptop/train-details?trainNo=${encodeURIComponent(query)}&day=${liveDay}&tab=live`;
    } else {
      window.location.href = `/laptop/train-details`;
    }
  };

  const quickPopularTrains = [
    { number: "22436", name: "Vande Bharat Exp" },
    { number: "12002", name: "Bhopal Shatabdi" },
    { number: "12951", name: "Mumbai Rajdhani" },
    { number: "12301", name: "Howrah Rajdhani" },
  ];

  return (
    <div className={`ref-booking-widget-wrapper ${isMobile ? "is-mobile-wrapper" : ""}`}>
      {/* 1. Integrated Tabs Navigation Bar */}
      <div className="ref-widget-tabs-container">
        <div className="ref-widget-tabs" role="tablist" aria-label="Booking Options">
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "book"}
            className={`ref-widget-tab ${activeTab === "book" ? "is-active" : ""}`}
            onClick={() => setActiveTab("book")}
          >
            <GoogleIcon name="train" size={19} className="ref-tab-icon" />
            <span className="ref-tab-text">Book Ticket</span>
          </button>

          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "pnr"}
            className={`ref-widget-tab ${activeTab === "pnr" ? "is-active" : ""}`}
            onClick={() => setActiveTab("pnr")}
          >
            <GoogleIcon name="confirmation_number" size={18} className="ref-tab-icon" />
            <span className="ref-tab-text">PNR Status</span>
          </button>

          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "schedule"}
            className={`ref-widget-tab ${activeTab === "schedule" ? "is-active" : ""}`}
            onClick={() => setActiveTab("schedule")}
          >
            <GoogleIcon name="calendar_month" size={18} className="ref-tab-icon" />
            <span className="ref-tab-text">Train Schedule</span>
          </button>

          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "seat"}
            className={`ref-widget-tab ${activeTab === "seat" ? "is-active" : ""}`}
            onClick={() => setActiveTab("seat")}
          >
            <GoogleIcon name="airline_seat_recline_extra" size={19} className="ref-tab-icon" />
            <span className="ref-tab-text">Seat Availability</span>
          </button>

          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "live"}
            className={`ref-widget-tab ${activeTab === "live" ? "is-active" : ""}`}
            onClick={() => setActiveTab("live")}
          >
            <GoogleIcon name="radar" size={19} className="ref-tab-icon" />
            <span className="ref-tab-text">Live Train Status</span>
          </button>
        </div>
      </div>

      {/* 2. Glassmorphic Booking Form Card */}
      <div className="ref-booking-form-box">
        {/* Tab 1: BOOK TICKET */}
        {activeTab === "book" && (
          <form onSubmit={handleBookSubmit} className="ref-tab-panel animate-fadeIn">
            <div className="ref-form-row">
              {/* From Station */}
              <div className="ref-field-unit ref-from-field">
                <span className="ref-field-caption">From</span>
                <div className="ref-input-inner">
                  <span className="ref-input-pin" aria-hidden="true">
                    <GoogleIcon name="location_on" size={19} color="#38bdf8" />
                  </span>
                  <StationAutocomplete
                    id="ref-from-input"
                    label=""
                    placeholder="Enter departure city / code"
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
                title="Swap stations"
              >
                <GoogleIcon name="sync_alt" size={20} color="#73dfff" />
              </button>

              {/* To Station */}
              <div className="ref-field-unit ref-to-field">
                <span className="ref-field-caption">To</span>
                <div className="ref-input-inner">
                  <span className="ref-input-pin" aria-hidden="true">
                    <GoogleIcon name="near_me" size={19} color="#38bdf8" />
                  </span>
                  <StationAutocomplete
                    id="ref-to-input"
                    label=""
                    placeholder="Enter destination city / code"
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
                  <span className="ref-input-pin" aria-hidden="true">
                    <GoogleIcon name="event" size={19} color="#38bdf8" />
                  </span>
                  <input
                    type="date"
                    required
                    min={today}
                    value={journeyDate}
                    onChange={(e) => setJourneyDate(e.target.value)}
                    className="ref-date-picker"
                    aria-label="Journey Date"
                  />
                </div>
              </div>

              {/* Travel Class */}
              <div className="ref-field-unit ref-class-field">
                <span className="ref-field-caption">Class</span>
                <div className="ref-input-inner">
                  <span className="ref-input-pin" aria-hidden="true">
                    <GoogleIcon name="airline_seat_recline_normal" size={19} color="#38bdf8" />
                  </span>
                  <select
                    value={coachClass}
                    onChange={(e) => setCoachClass(e.target.value)}
                    className="ref-select-picker"
                    aria-label="Class selection"
                  >
                    <option value="ALL">All Classes</option>
                    <option value="1A">Anubhuti / 1A</option>
                    <option value="2A">AC 2 Tier (2A)</option>
                    <option value="3A">AC 3 Tier (3A)</option>
                    <option value="CC">AC Chair Car (CC)</option>
                    <option value="SL">Sleeper (SL)</option>
                    <option value="EC">Executive Chair (EC)</option>
                    <option value="3E">AC 3 Economy (3E)</option>
                  </select>
                </div>
              </div>

              {/* Quota */}
              <div className="ref-field-unit ref-quota-field">
                <span className="ref-field-caption">Quota</span>
                <div className="ref-input-inner">
                  <span className="ref-input-pin" aria-hidden="true">
                    <GoogleIcon name="sell" size={18} color="#38bdf8" />
                  </span>
                  <select
                    value={quota}
                    onChange={(e) => setQuota(e.target.value)}
                    className="ref-select-picker"
                    aria-label="Quota selection"
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
                  {loading ? (
                    <span className="ref-btn-flex">
                      <span className="ref-spinner" aria-hidden="true"></span>
                      <span>Searching…</span>
                    </span>
                  ) : (
                    <span className="ref-btn-flex">
                      <span>Search Trains</span>
                      <GoogleIcon name="arrow_forward" size={18} />
                    </span>
                  )}
                </button>
              </div>
            </div>

            {/* Quick Filter Badges */}
            <div className="ref-perks-row">
              <span className="ref-perk-item">
                <GoogleIcon name="bolt" size={16} color="#38bdf8" />
                <span>0 Payment Gateway Fee on UPI</span>
              </span>
              <span className="ref-perk-item">
                <GoogleIcon name="shield" size={16} color="#38bdf8" />
                <span>Free Cancellation Option</span>
              </span>
              <span className="ref-perk-item">
                <GoogleIcon name="verified" size={16} color="#4ade80" />
                <span>Official IRCTC Partner</span>
              </span>
            </div>
          </form>
        )}

        {/* Tab 2: PNR STATUS */}
        {activeTab === "pnr" && (
          <form onSubmit={handlePnrSubmit} className="ref-tab-panel animate-fadeIn">
            <div className="ref-feature-panel">
              <div className="ref-feature-header">
                <div className="ref-feature-icon">
                  <GoogleIcon name="confirmation_number" size={24} color="#73dfff" />
                </div>
                <div>
                  <h3 className="ref-feature-title">Check 10-Digit PNR Status</h3>
                  <p className="ref-feature-desc">
                    Get real-time confirmation probability, coach, seat & berth allocation instantly.
                  </p>
                </div>
              </div>

              <div className="ref-feature-inline-form">
                <div className="ref-field-unit flex-1">
                  <label htmlFor="pnr-input" className="ref-field-caption">
                    Enter 10-Digit PNR Number
                  </label>
                  <div className="ref-input-inner">
                    <span className="ref-input-pin">
                      <GoogleIcon name="search" size={20} color="#38bdf8" />
                    </span>
                    <input
                      id="pnr-input"
                      type="text"
                      inputMode="numeric"
                      pattern="[0-9]*"
                      maxLength={10}
                      placeholder="e.g. 2765418793"
                      value={pnrInput}
                      onChange={(e) => setPnrInput(e.target.value.replace(/\D/g, "").slice(0, 10))}
                      className="ref-standalone-input ref-mono-font"
                      required
                      autoFocus
                    />
                    {pnrInput && (
                      <button
                        type="button"
                        onClick={() => setPnrInput("")}
                        className="ref-input-clear-btn"
                        aria-label="Clear PNR input"
                      >
                        <GoogleIcon name="close" size={16} />
                      </button>
                    )}
                  </div>
                </div>

                <div className="ref-cta-unit ref-cta-inline">
                  <button type="submit" className="ref-search-btn">
                    <span>Check Status</span>
                    <GoogleIcon name="arrow_forward" size={18} />
                  </button>
                </div>
              </div>

              <div className="ref-feature-footer-hints">
                <span className="ref-hint-chip">
                  <GoogleIcon name="check_circle" size={15} color="#4ade80" />
                  <span>Live Berth & Coach Details</span>
                </span>
                <span className="ref-hint-chip">
                  <GoogleIcon name="bolt" size={15} color="#38bdf8" />
                  <span>Chart Prepared Alerts</span>
                </span>
                <span className="ref-hint-chip">
                  <GoogleIcon name="download" size={15} color="#73dfff" />
                  <span>Instant e-Ticket Download</span>
                </span>
              </div>
            </div>
          </form>
        )}

        {/* Tab 3: TRAIN SCHEDULE */}
        {activeTab === "schedule" && (
          <form onSubmit={handleScheduleSubmit} className="ref-tab-panel animate-fadeIn">
            <div className="ref-feature-panel">
              <div className="ref-feature-header">
                <div className="ref-feature-icon">
                  <GoogleIcon name="calendar_month" size={24} color="#73dfff" />
                </div>
                <div>
                  <h3 className="ref-feature-title">Train Schedule & Timetable</h3>
                  <p className="ref-feature-desc">
                    View complete route, intermediate halt durations, distance and platform info.
                  </p>
                </div>
              </div>

              <div className="ref-feature-inline-form">
                <div className="ref-field-unit flex-1">
                  <label htmlFor="schedule-train-input" className="ref-field-caption">
                    Enter Train Number or Name
                  </label>
                  <div className="ref-input-inner">
                    <span className="ref-input-pin">
                      <GoogleIcon name="train" size={20} color="#38bdf8" />
                    </span>
                    <input
                      id="schedule-train-input"
                      type="text"
                      placeholder="e.g. 12951 or Mumbai Rajdhani"
                      value={scheduleTrainInput}
                      onChange={(e) => setScheduleTrainInput(e.target.value)}
                      className="ref-standalone-input"
                      required
                      autoFocus
                    />
                    {scheduleTrainInput && (
                      <button
                        type="button"
                        onClick={() => setScheduleTrainInput("")}
                        className="ref-input-clear-btn"
                        aria-label="Clear input"
                      >
                        <GoogleIcon name="close" size={16} />
                      </button>
                    )}
                  </div>
                </div>

                <div className="ref-cta-unit ref-cta-inline">
                  <button type="submit" className="ref-search-btn">
                    <span>Find Train</span>
                    <GoogleIcon name="arrow_forward" size={18} />
                  </button>
                </div>
              </div>

              <div className="ref-quick-chips-group">
                <span className="ref-quick-chips-label">Popular Trains:</span>
                {quickPopularTrains.map((tr) => (
                  <button
                    key={tr.number}
                    type="button"
                    className="ref-quick-chip-btn"
                    onClick={() => {
                      setScheduleTrainInput(`${tr.number} - ${tr.name}`);
                      handleScheduleSubmit(undefined as unknown as React.FormEvent, tr.number);
                    }}
                  >
                    <span className="ref-chip-code">{tr.number}</span>
                    <span className="ref-chip-name">{tr.name}</span>
                  </button>
                ))}
              </div>
            </div>
          </form>
        )}

        {/* Tab 4: SEAT AVAILABILITY */}
        {activeTab === "seat" && (
          <form onSubmit={handleSeatSubmit} className="ref-tab-panel animate-fadeIn">
            <div className="ref-feature-panel">
              <div className="ref-feature-header">
                <div className="ref-feature-icon">
                  <GoogleIcon name="airline_seat_recline_extra" size={24} color="#73dfff" />
                </div>
                <div>
                  <h3 className="ref-feature-title">Check Seat Availability</h3>
                  <p className="ref-feature-desc">
                    Find available berths across AC 1st, AC 2 Tier, AC 3 Tier, Chair Car & Sleeper.
                  </p>
                </div>
              </div>

              <div className="ref-form-row">
                <div className="ref-field-unit flex-[1.6]">
                  <label htmlFor="seat-train-input" className="ref-field-caption">
                    Train Number or Name
                  </label>
                  <div className="ref-input-inner">
                    <span className="ref-input-pin">
                      <GoogleIcon name="train" size={20} color="#38bdf8" />
                    </span>
                    <input
                      id="seat-train-input"
                      type="text"
                      placeholder="e.g. 12951 or Vande Bharat"
                      value={seatTrainInput}
                      onChange={(e) => setSeatTrainInput(e.target.value)}
                      className="ref-standalone-input"
                      required
                      autoFocus
                    />
                  </div>
                </div>

                <div className="ref-field-unit flex-1">
                  <span className="ref-field-caption">Date of Journey</span>
                  <div className="ref-input-inner">
                    <span className="ref-input-pin">
                      <GoogleIcon name="event" size={19} color="#38bdf8" />
                    </span>
                    <input
                      type="date"
                      required
                      min={today}
                      value={seatDate}
                      onChange={(e) => setSeatDate(e.target.value)}
                      className="ref-date-picker"
                    />
                  </div>
                </div>

                <div className="ref-field-unit flex-1">
                  <span className="ref-field-caption">Class</span>
                  <div className="ref-input-inner">
                    <span className="ref-input-pin">
                      <GoogleIcon name="airline_seat_recline_normal" size={19} color="#38bdf8" />
                    </span>
                    <select
                      value={seatClass}
                      onChange={(e) => setSeatClass(e.target.value)}
                      className="ref-select-picker"
                    >
                      <option value="ALL">All Classes</option>
                      <option value="1A">AC 1st Class (1A)</option>
                      <option value="2A">AC 2 Tier (2A)</option>
                      <option value="3A">AC 3 Tier (3A)</option>
                      <option value="CC">AC Chair Car (CC)</option>
                      <option value="SL">Sleeper (SL)</option>
                    </select>
                  </div>
                </div>

                <div className="ref-field-unit flex-1">
                  <span className="ref-field-caption">Quota</span>
                  <div className="ref-input-inner">
                    <span className="ref-input-pin">
                      <GoogleIcon name="sell" size={18} color="#38bdf8" />
                    </span>
                    <select
                      value={seatQuota}
                      onChange={(e) => setSeatQuota(e.target.value)}
                      className="ref-select-picker"
                    >
                      <option value="GENERAL">General</option>
                      <option value="TATKAL">Tatkal</option>
                      <option value="LADIES">Ladies</option>
                      <option value="PREMIUM_TATKAL">Premium Tatkal</option>
                    </select>
                  </div>
                </div>

                <div className="ref-cta-unit">
                  <button type="submit" className="ref-search-btn">
                    <span>Check Seats</span>
                    <GoogleIcon name="arrow_forward" size={18} />
                  </button>
                </div>
              </div>
            </div>
          </form>
        )}

        {/* Tab 5: LIVE TRAIN STATUS */}
        {activeTab === "live" && (
          <form onSubmit={handleLiveSubmit} className="ref-tab-panel animate-fadeIn">
            <div className="ref-feature-panel">
              <div className="ref-feature-header">
                <div className="ref-feature-icon">
                  <GoogleIcon name="radar" size={24} color="#73dfff" />
                </div>
                <div>
                  <h3 className="ref-feature-title">Live Train Running Status</h3>
                  <p className="ref-feature-desc">
                    Real-time GPS satellite tracking, upcoming station ETA, delays & platform numbers.
                  </p>
                </div>
              </div>

              <div className="ref-form-row">
                <div className="ref-field-unit flex-[2]">
                  <label htmlFor="live-train-input" className="ref-field-caption">
                    Train Number or Name
                  </label>
                  <div className="ref-input-inner">
                    <span className="ref-input-pin">
                      <GoogleIcon name="train" size={20} color="#38bdf8" />
                    </span>
                    <input
                      id="live-train-input"
                      type="text"
                      placeholder="e.g. 12951 or Rajdhani Express"
                      value={liveTrainInput}
                      onChange={(e) => setLiveTrainInput(e.target.value)}
                      className="ref-standalone-input"
                      required
                      autoFocus
                    />
                  </div>
                </div>

                {/* Day selector: Yesterday / Today / Tomorrow */}
                <div className="ref-field-unit flex-[1.4]">
                  <span className="ref-field-caption">Journey Starting Day</span>
                  <div className="ref-day-toggle-group">
                    <button
                      type="button"
                      className={`ref-day-btn ${liveDay === "yesterday" ? "is-selected" : ""}`}
                      onClick={() => setLiveDay("yesterday")}
                    >
                      Yesterday
                    </button>
                    <button
                      type="button"
                      className={`ref-day-btn ${liveDay === "today" ? "is-selected" : ""}`}
                      onClick={() => setLiveDay("today")}
                    >
                      Today
                    </button>
                    <button
                      type="button"
                      className={`ref-day-btn ${liveDay === "tomorrow" ? "is-selected" : ""}`}
                      onClick={() => setLiveDay("tomorrow")}
                    >
                      Tomorrow
                    </button>
                  </div>
                </div>

                <div className="ref-cta-unit">
                  <button type="submit" className="ref-search-btn">
                    <span>Spot Train</span>
                    <GoogleIcon name="arrow_forward" size={18} />
                  </button>
                </div>
              </div>

              <div className="ref-feature-footer-hints">
                <span className="ref-live-pulse-badge">
                  <span className="ref-pulse-dot"></span>
                  Live GPS Tracking Active
                </span>
                <span className="ref-hint-chip">
                  <GoogleIcon name="speed" size={15} color="#38bdf8" />
                  <span>Automated Delay Predictions</span>
                </span>
                <span className="ref-hint-chip">
                  <GoogleIcon name="alt_route" size={15} color="#73dfff" />
                  <span>Platform Confirmation via NTES</span>
                </span>
              </div>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
