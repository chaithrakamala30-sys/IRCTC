"use client";

import { useCallback, useState } from "react";
import Link from "next/link";
import { ReferenceHeader } from "../../components/reference-header";
import { ReferenceLocomotiveScene } from "../../components/reference-locomotive-scene";
import { ReferenceBookingEngine } from "../../components/reference-booking-engine";
import { ReferenceServicesRow } from "../../components/reference-services-row";
import { ReferencePopularSection } from "../../components/reference-popular-section";
import { ReferenceFooter } from "../../components/reference-footer";
import type { StationSuggestion } from "../../components/train-search-types";

export default function PassengerMobilePage() {
  const [loading, setLoading] = useState(false);

  const handleSearch = useCallback(
    (from: StationSuggestion | null, to: StationSuggestion | null, date: string, coachClass?: string, quota?: string) => {
      setLoading(true);
      const params = new URLSearchParams();
      if (from) params.set("fromStationId", from.stationId);
      if (to) params.set("toStationId", to.stationId);
      if (date) params.set("journeyDate", date);
      if (coachClass && coachClass !== "ALL") params.set("coachClass", coachClass);
      if (quota && quota !== "GENERAL") params.set("quota", quota);

      window.location.href = `/mobile?${params.toString()}#results`;
    },
    []
  );

  return (
    <div className="page-shell bg-[#040914] text-[#eaf3ff] pb-24">
      {/* 1. Mobile Header with Hamburger Drawer + Branding */}
      <ReferenceHeader experience="mobile" activeTab="home" />

      {/* 2. Portrait-Optimized Hero Banner */}
      <section className="ref-hero-banner ref-hero-banner--mobile" id="journey">
        <ReferenceLocomotiveScene isMobile={true} />

        <div className="ref-hero-text-block">
          <h1 className="ref-hero-h1">
            Your Journey. <br />
            <span>Your Destination.</span>
          </h1>
          <p className="ref-hero-p">
            Discover India, one journey at a time. Book smarter, travel better.
          </p>

          <div className="ref-hero-cta-group">
            <a href="#journey" className="ref-btn-primary-glow" style={{ width: "100%", justifyContent: "center" }}>
              <span>Book Your Journey →</span>
            </a>
          </div>
        </div>
      </section>

      {/* 3. Vertically Stacked Touch-Friendly Mobile Booking Widget */}
      <ReferenceBookingEngine
        isMobile={true}
        onSearch={handleSearch}
        loading={loading}
      />

      {/* 4. Service Icons Grid */}
      <ReferenceServicesRow />

      {/* 5. Live Train Status & Popular Express Carousel */}
      <ReferencePopularSection />

      {/* 6. Footer */}
      <ReferenceFooter />
    </div>
  );
}
