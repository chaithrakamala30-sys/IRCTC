"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiFetch } from "../lib/api";
import { GoogleIcon } from "./google-icon";

type TrackingStopItem = {
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
  stops: TrackingStopItem[];
};

type RecentSearchItem = {
  from: { stationId: string; stationName: string; stationCode: string };
  to: { stationId: string; stationName: string; stationCode: string };
  journeyDate: string;
};

export function ReferencePopularSection() {
  const [liveData, setLiveData] = useState<PublicTrainTrackingView | null>(null);
  const [recentSearch, setRecentSearch] = useState<RecentSearchItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTrainNumber, setActiveTrainNumber] = useState("12650");

  const popularTrains = [
    { code: "22436", name: "Vande Bharat Express", speed: "160 km/h", icon: "train" },
    { code: "12951", name: "Rajdhani Express", speed: "130 km/h", icon: "hotel_class" },
    { code: "12002", name: "Shatabdi Express", speed: "130 km/h", icon: "bolt" },
    { code: "12260", name: "Duronto Express", speed: "120 km/h", icon: "rocket_launch" },
  ];

  useEffect(() => {
    // 1. Check user's recent searches from localStorage
    try {
      const storedSearches = JSON.parse(window.localStorage.getItem("railconnect.recent-searches") ?? "[]");
      if (Array.isArray(storedSearches) && storedSearches.length > 0 && storedSearches[0]?.from && storedSearches[0]?.to) {
        setRecentSearch(storedSearches[0]);
      }

      const storedTrainQueries = JSON.parse(window.localStorage.getItem("railconnect.recent-train-queries") ?? "[]");
      if (Array.isArray(storedTrainQueries) && storedTrainQueries.length > 0) {
        const first = String(storedTrainQueries[0]).trim();
        const extractedNum = first.match(/\b\d{5}\b/)?.[0];
        if (extractedNum) {
          setActiveTrainNumber(extractedNum);
        }
      }
    } catch {
      // Storage access gracefully handled
    }
  }, []);

  // 2. Fetch live train tracking data from database API (/api/v1/tracking/trains/{trainNumber})
  useEffect(() => {
    let active = true;
    setLoading(true);

    apiFetch<PublicTrainTrackingView>(`/api/v1/tracking/trains/${encodeURIComponent(activeTrainNumber)}`)
      .then((res) => {
        if (active && res.data) {
          setLiveData(res.data);
        }
      })
      .catch(() => {
        // Fallback to default operational express 12650 if custom query not found
        if (activeTrainNumber !== "12650") {
          apiFetch<PublicTrainTrackingView>(`/api/v1/tracking/trains/12650`)
            .then((fallbackRes) => {
              if (active && fallbackRes.data) {
                setLiveData(fallbackRes.data);
              }
            })
            .catch(() => {
              // Ignore network fallback error
            });
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [activeTrainNumber]);

  // Derive origin, mid and destination from actual stops in database
  const stops = liveData?.stops ?? [];
  const originStop = stops[0] ?? null;
  const destStop = stops.length > 1 ? stops[stops.length - 1] : null;
  const midStop = stops.length > 2 ? stops[Math.floor(stops.length / 2)] : null;

  const currentStationDisplay = liveData?.currentStationName || liveData?.currentStationCode || originStop?.stationName || "NDLS";
  const nextStationDisplay = liveData?.nextStationName || liveData?.nextStationCode || destStop?.stationName || "MAS";

  const isDelayed = (liveData?.currentDelayMinutes ?? 0) > 0;
  const progressPercent = Math.min(100, Math.max(8, liveData?.routeProgressPercent ?? 35));

  return (
    <div className="ref-bottom-panels-grid">
      {/* Left Card: Live Train Running Status preview from DB & Recent Search */}
      <div className="ref-panel-card ref-live-status-card">
        <div className="ref-panel-header">
          <div className="flex items-center gap-2">
            <span className="ref-panel-title">Live Train Status</span>
            <span className="ref-live-indicator">
              <span className="ref-pulse-dot" style={{ display: "inline-block", marginRight: "4px" }}></span>
              {liveData?.operationalStatus || "Live"}
            </span>
          </div>
          <Link
            href={`/laptop/train-details?trainNo=${encodeURIComponent(liveData?.trainNumber || activeTrainNumber)}`}
            className="ref-view-link"
          >
            <span>View Full Route</span>
            <GoogleIcon name="arrow_forward" size={14} />
          </Link>
        </div>

        {/* Live Train Card Banner */}
        <div className="ref-live-preview-box">
          <div className="ref-live-train-info">
            <div className="flex items-center gap-2">
              <GoogleIcon name="train" size={18} color="#73dfff" />
              <strong>
                {liveData ? `${liveData.trainNumber} ${liveData.trainName}` : `${activeTrainNumber} Chennai Express`}
              </strong>
            </div>
            <div className="flex items-center gap-1.5 mt-0.5 text-xs text-[#8fa3c7]">
              <span>{originStop?.stationCode || "NDLS"}</span>
              <GoogleIcon name="arrow_right_alt" size={15} color="#38bdf8" />
              <span>{destStop?.stationCode || "MAS"}</span>
              {liveData?.serviceDate && (
                <>
                  <span className="mx-1">•</span>
                  <span>{liveData.serviceDate}</span>
                </>
              )}
            </div>
          </div>

          <span className={`ref-ontime-badge ${isDelayed ? "is-delayed" : ""}`}>
            {loading ? "Tracking…" : isDelayed ? `Delayed ${liveData?.currentDelayMinutes}m` : "On Time"}
          </span>
        </div>

        {/* Real Dynamic Timeline Bar based on database route */}
        <div className="ref-timeline-container">
          <div className="ref-timeline-bar">
            {/* Origin */}
            <div className="ref-timeline-station">
              <strong>{originStop?.stationCode || "NDLS"}</strong>
              <small>{originStop?.stationName || "New Delhi"}</small>
            </div>

            {/* Intermediate Line with Animated GPS Train Marker positioned by routeProgressPercent */}
            <div className="ref-timeline-line">
              <div
                className="ref-timeline-fill"
                style={{ width: `${progressPercent}%` }}
              />
              <span
                className="ref-train-marker"
                style={{ left: `calc(${progressPercent}% - 10px)` }}
                title={`Currently near: ${currentStationDisplay}`}
              >
                <GoogleIcon name="train" size={19} color="#73dfff" />
              </span>
            </div>

            {/* Mid Station */}
            {midStop && (
              <div className="ref-timeline-station text-center">
                <strong>{midStop.stationCode}</strong>
                <small>{midStop.stationName}</small>
              </div>
            )}

            {/* Destination */}
            <div className="ref-timeline-station text-right">
              <strong>{destStop?.stationCode || "MAS"}</strong>
              <small>{destStop?.stationName || "Chennai"}</small>
            </div>
          </div>

          {/* Current Location Note from DB */}
          <div className="ref-timeline-telemetry">
            <div className="flex items-center gap-1">
              <GoogleIcon name="my_location" size={13} color="#38bdf8" />
              <span>
                <strong>At / Passed:</strong> {currentStationDisplay}
              </span>
            </div>
            <div className="flex items-center gap-1">
              <GoogleIcon name="near_me" size={13} color="#8fa3c7" />
              <span>
                <strong>Next:</strong> {nextStationDisplay}
              </span>
            </div>
          </div>
        </div>

        {/* User's Recent Search Quick Jump Bar */}
        {recentSearch && (
          <div className="ref-recent-search-pill">
            <div className="flex items-center gap-1.5 min-w-0">
              <GoogleIcon name="history" size={15} color="#38bdf8" />
              <span className="text-xs text-[#8fa3c7] font-semibold flex-shrink-0">Recent Search:</span>
              <span className="text-xs text-white font-bold truncate">
                {recentSearch.from.stationCode} → {recentSearch.to.stationCode}
              </span>
            </div>
            <Link
              href={`/laptop?fromStationId=${encodeURIComponent(recentSearch.from.stationId)}&toStationId=${encodeURIComponent(recentSearch.to.stationId)}&journeyDate=${encodeURIComponent(recentSearch.journeyDate)}#results`}
              className="ref-recent-search-action"
            >
              <span>Search Again</span>
              <GoogleIcon name="arrow_forward" size={13} />
            </Link>
          </div>
        )}
      </div>

      {/* Center Card: Popular Trains Carousel */}
      <div className="ref-panel-card ref-popular-trains-card">
        <div className="ref-panel-header">
          <span className="ref-panel-title">Popular Trains</span>
          <Link href="/laptop#journey" className="ref-view-link">
            <span>View All</span>
            <GoogleIcon name="arrow_forward" size={14} />
          </Link>
        </div>

        <div className="ref-popular-slider">
          {popularTrains.map((train) => (
            <button
              key={train.code}
              type="button"
              onClick={() => setActiveTrainNumber(train.code)}
              className={`ref-popular-item text-left cursor-pointer ${activeTrainNumber === train.code ? "is-selected-popular" : ""}`}
            >
              <div className="flex items-center justify-between w-full mb-1">
                <span className="ref-popular-icon">
                  <GoogleIcon name={train.icon} size={22} color={activeTrainNumber === train.code ? "#38bdf8" : "#73dfff"} />
                </span>
                <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-blue-500/20 text-[#73dfff]">
                  {train.code}
                </span>
              </div>
              <strong className="ref-popular-name">{train.name}</strong>
              <small className="ref-popular-speed">{train.speed}</small>
            </button>
          ))}
        </div>
      </div>

      {/* Right Card: Why RailConnect? */}
      <div className="ref-panel-card ref-why-card">
        <div className="ref-panel-header">
          <span className="ref-panel-title">Why RailConnect?</span>
        </div>
        <ul className="ref-why-list">
          <li>
            <span className="ref-check-icon">
              <GoogleIcon name="check" size={16} color="#73dfff" />
            </span>
            <span>Secure & Reliable Booking</span>
          </li>
          <li>
            <span className="ref-check-icon">
              <GoogleIcon name="check" size={16} color="#73dfff" />
            </span>
            <span>Real-Time PNR & Chart Updates</span>
          </li>
          <li>
            <span className="ref-check-icon">
              <GoogleIcon name="check" size={16} color="#73dfff" />
            </span>
            <span>Fast & Easy Digital Refunds</span>
          </li>
          <li>
            <span className="ref-check-icon">
              <GoogleIcon name="check" size={16} color="#73dfff" />
            </span>
            <span>Authorized by Indian Railway System</span>
          </li>
        </ul>
      </div>
    </div>
  );
}
