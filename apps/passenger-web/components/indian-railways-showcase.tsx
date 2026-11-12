"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { apiFetch } from "../lib/api";

type SystemStats = {
  activeTrainsCount?: number;
  totalStationsCount?: number;
  dailyPassengersEstimated?: string;
  systemStatus?: string;
};

const INDIAN_EXPRESS_TRAINS = [
  {
    name: "Vande Bharat Express",
    category: "Semi-High Speed",
    speed: "160 km/h",
    description: "State-of-the-art aerodynamic trainset with European comfort and Indian engineering.",
    icon: "⚡",
  },
  {
    name: "Rajdhani Express",
    category: "Superfast Premium",
    speed: "130 km/h",
    description: "Fully air-conditioned long-distance express connecting national & state capitals.",
    icon: "👑",
  },
  {
    name: "Tejas Express",
    category: "Smart Luxury",
    speed: "140 km/h",
    description: "Modern automated doors, onboard infotainment, and premium dining service.",
    icon: "✨",
  },
  {
    name: "Shatabdi Express",
    category: "Day Intercity",
    speed: "130 km/h",
    description: "High-speed daytime chair-car connecting major commercial metropolitan corridors.",
    icon: "🚄",
  },
];

export function IndianRailwaysShowcase() {
  const [stats, setStats] = useState<SystemStats | null>(null);

  useEffect(() => {
    let active = true;
    void apiFetch<SystemStats>("/api/v1/system/info")
      .then((res) => {
        if (active && res.data) {
          setStats(res.data);
        }
      })
      .catch(() => {
        // Fallback gracefully without breaking display
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <section aria-labelledby="showcase-heading" className="cinematic-showcase-section">
      <div className="cinematic-section-header">
        <span className="cinematic-tag">INDIAN RAILWAYS HERITAGE & MODERNITY</span>
        <h2 id="showcase-heading" className="cinematic-title">
          Next-Generation Rail Travel in India
        </h2>
        <p className="cinematic-subtitle">
          Experience seamless, high-speed connectivity across thousands of stations on India’s authoritative national railway grid.
        </p>
      </div>

      <div className="train-showcase-grid">
        {INDIAN_EXPRESS_TRAINS.map((train) => (
          <div className="train-showcase-card" key={train.name}>
            <div className="train-showcase-header">
              <span className="train-showcase-icon">{train.icon}</span>
              <span className="train-showcase-speed">{train.speed}</span>
            </div>
            <h3 className="train-showcase-name">{train.name}</h3>
            <span className="train-showcase-cat">{train.category}</span>
            <p className="train-showcase-desc">{train.description}</p>
          </div>
        ))}
      </div>

      {stats && (
        <div className="network-live-stats-bar">
          <div className="stat-pill">
            <span className="stat-label">Platform Core</span>
            <span className="stat-value">{stats.systemStatus || "ONLINE"}</span>
          </div>
          <div className="stat-pill">
            <span className="stat-label">Real-Time Search</span>
            <span className="stat-value">Authoritative DB</span>
          </div>
          <div className="stat-pill">
            <span className="stat-label">Payment Security</span>
            <span className="stat-value">Dhruva Bank Demo</span>
          </div>
        </div>
      )}
    </section>
  );
}

