"use client";

import Link from "next/link";
import { GoogleIcon } from "./google-icon";

export function ReferencePopularSection() {
  const popularTrains = [
    { code: "22436", name: "Vande Bharat Express", speed: "160 km/h", icon: "train" },
    { code: "12951", name: "Rajdhani Express", speed: "130 km/h", icon: "hotel_class" },
    { code: "12002", name: "Shatabdi Express", speed: "130 km/h", icon: "bolt" },
    { code: "12260", name: "Duronto Express", speed: "120 km/h", icon: "rocket_launch" },
  ];

  return (
    <div className="ref-bottom-panels-grid">
      {/* Left Card: Live Train Running Status preview */}
      <div className="ref-panel-card ref-live-status-card">
        <div className="ref-panel-header">
          <div className="flex items-center gap-2">
            <span className="ref-panel-title">Live Train Status</span>
            <span className="ref-live-indicator">
              <span className="ref-pulse-dot" style={{ display: "inline-block", marginRight: "4px" }}></span>
              Live
            </span>
          </div>
          <Link href="/laptop/train-details" className="ref-view-link">
            <span>View Details</span>
            <GoogleIcon name="arrow_forward" size={14} />
          </Link>
        </div>

        <div className="ref-live-preview-box">
          <div className="ref-live-train-info">
            <strong>12650 Chennai Express</strong>
            <span>NDLS → MAS</span>
          </div>
          <span className="ref-ontime-badge">On Time</span>
        </div>

        <div className="ref-timeline-bar">
          <div className="ref-timeline-station">
            <strong>NDLS</strong>
            <small>New Delhi</small>
          </div>
          <div className="ref-timeline-line">
            <span className="ref-train-marker">
              <GoogleIcon name="train" size={20} color="#73dfff" />
            </span>
          </div>
          <div className="ref-timeline-station">
            <strong>BPL</strong>
            <small>Bhopal</small>
          </div>
          <div className="ref-timeline-line" />
          <div className="ref-timeline-station">
            <strong>MAS</strong>
            <small>Chennai</small>
          </div>
        </div>
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
            <Link
              key={train.code}
              href="/laptop/train-details"
              className="ref-popular-item"
            >
              <span className="ref-popular-icon">
                <GoogleIcon name={train.icon} size={22} color="#73dfff" />
              </span>
              <strong className="ref-popular-name">{train.name}</strong>
              <small className="ref-popular-speed">{train.speed}</small>
            </Link>
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
