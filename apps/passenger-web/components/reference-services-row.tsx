"use client";

import Link from "next/link";
import { TrainTrackingModal } from "./train-tracking-modal";

export function ReferenceServicesRow() {
  return (
    <div className="ref-services-row-container">
      {/* 1. Six Service Feature Cards */}
      <div className="ref-feature-cards-grid">
        <Link href="/laptop#journey" className="ref-service-square-card">
          <div className="ref-service-circle-icon">⚡</div>
          <span className="ref-service-name">Tatkal Booking</span>
          <span className="ref-service-arrow">→</span>
        </Link>

        <div className="ref-service-square-card">
          <div className="ref-service-circle-icon">🕒</div>
          <span className="ref-service-name">Train Live Status</span>
          <div className="ref-tracking-trigger">
            <TrainTrackingModal />
          </div>
        </div>

        <Link href="/laptop/train-details" className="ref-service-square-card">
          <div className="ref-service-circle-icon">💺</div>
          <span className="ref-service-name">Coach Position</span>
          <span className="ref-service-arrow">→</span>
        </Link>

        <Link href="/laptop/train-details" className="ref-service-square-card">
          <div className="ref-service-circle-icon">📍</div>
          <span className="ref-service-name">Track Your Train</span>
          <span className="ref-service-arrow">→</span>
        </Link>

        <Link href="/support" className="ref-service-square-card">
          <div className="ref-service-circle-icon">🏷️</div>
          <span className="ref-service-name">Offers & Schemes</span>
          <span className="ref-service-arrow">→</span>
        </Link>

        <Link href="/support" className="ref-service-square-card">
          <div className="ref-service-circle-icon">🎧</div>
          <span className="ref-service-name">24x7 Helpline</span>
          <span className="ref-service-arrow">→</span>
        </Link>
      </div>

      {/* 2. Vande Bharat Express Highlighting Banner (Right Side) */}
      <div className="ref-vande-bharat-banner">
        <div className="ref-vb-content">
          <span className="ref-vb-badge">— Introducing —</span>
          <h3 className="ref-vb-title">Vande Bharat Express</h3>
          <p className="ref-vb-subtitle">Faster. Smarter. Greener.</p>
          <Link href="/laptop/train-details" className="ref-vb-link">
            Know More →
          </Link>
        </div>
        <div className="ref-vb-image-box" aria-hidden="true">
          <div className="ref-vb-train-silhouette">
            <span className="ref-vb-badge-pill">160 km/h</span>
          </div>
        </div>
      </div>
    </div>
  );
}
