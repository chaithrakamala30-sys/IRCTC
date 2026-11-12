"use client";

import Link from "next/link";
import { GoogleIcon } from "./google-icon";

export function ReferenceServicesRow() {
  return (
    <div className="ref-services-row-container">
      {/* 6 Square Feature Action Buttons */}
      <div className="ref-feature-cards-grid">
        <Link href="/laptop#journey" className="ref-service-square-card">
          <div className="ref-service-circle-icon">
            <GoogleIcon name="bolt" size={24} color="#73dfff" />
          </div>
          <span className="ref-service-name">Tatkal Booking</span>
          <span className="ref-service-arrow">
            <GoogleIcon name="arrow_forward" size={16} />
          </span>
        </Link>

        <Link href="/laptop/train-details" className="ref-service-square-card">
          <div className="ref-service-circle-icon">
            <GoogleIcon name="schedule" size={24} color="#73dfff" />
          </div>
          <span className="ref-service-name">Train Live Status</span>
          <span className="ref-service-arrow">
            <GoogleIcon name="arrow_forward" size={16} />
          </span>
        </Link>

        <Link href="/laptop/train-details" className="ref-service-square-card">
          <div className="ref-service-circle-icon">
            <GoogleIcon name="airline_seat_recline_extra" size={24} color="#73dfff" />
          </div>
          <span className="ref-service-name">Coach Position</span>
          <span className="ref-service-arrow">
            <GoogleIcon name="arrow_forward" size={16} />
          </span>
        </Link>

        <Link href="/laptop/train-details" className="ref-service-square-card">
          <div className="ref-service-circle-icon">
            <GoogleIcon name="my_location" size={24} color="#73dfff" />
          </div>
          <span className="ref-service-name">Track Your Train</span>
          <span className="ref-service-arrow">
            <GoogleIcon name="arrow_forward" size={16} />
          </span>
        </Link>

        <Link href="/support" className="ref-service-square-card">
          <div className="ref-service-circle-icon">
            <GoogleIcon name="sell" size={24} color="#73dfff" />
          </div>
          <span className="ref-service-name">Offers & Schemes</span>
          <span className="ref-service-arrow">
            <GoogleIcon name="arrow_forward" size={16} />
          </span>
        </Link>

        <Link href="/support" className="ref-service-square-card">
          <div className="ref-service-circle-icon">
            <GoogleIcon name="support_agent" size={24} color="#73dfff" />
          </div>
          <span className="ref-service-name">24x7 Helpline</span>
          <span className="ref-service-arrow">
            <GoogleIcon name="arrow_forward" size={16} />
          </span>
        </Link>
      </div>

      {/* Promotional Vande Bharat Showcase Card */}
      <div className="ref-vb-promo-banner">
        <div className="ref-vb-glow-badge">
          <GoogleIcon name="verified" size={15} color="#38bdf8" />
          <span>NextGen Train 18</span>
        </div>
        <h3 className="ref-vb-title">Vande Bharat Express</h3>
        <p className="ref-vb-tagline">
          Experience ultra-fast semi-high speed transit with world-class executive interiors & Wi-Fi infotainment.
        </p>
        <Link href="/laptop#journey" className="ref-vb-cta">
          <span>Book Vande Bharat</span>
          <GoogleIcon name="arrow_forward" size={16} />
        </Link>
      </div>
    </div>
  );
}
