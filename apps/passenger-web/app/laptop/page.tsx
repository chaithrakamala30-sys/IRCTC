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

export default function PassengerLaptopPage() {
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

      // Navigate smoothly with authoritative search parameters
      window.location.href = `/laptop?${params.toString()}#results`;
    },
    []
  );

  return (
    <div className="page-shell bg-[#040914] text-[#eaf3ff]">
      {/* 1. Header with Chakra + Logo + Center Pill Nav + Auth */}
      <ReferenceHeader experience="laptop" activeTab="home" />

      {/* 2. Hero Section: WAP-7 Locomotive in Station + Catchy Typography */}
      <section className="ref-hero-banner" id="journey">
        <ReferenceLocomotiveScene isMobile={false} />

        <div className="ref-hero-text-block">
          <h1 className="ref-hero-h1">
            Your Journey. <br />
            <span>Your Destination.</span>
          </h1>
          <p className="ref-hero-p">
            Discover India, one journey at a time. <br />
            Book smarter, travel better with RailConnect.
          </p>

          <div className="ref-hero-cta-group">
            <a href="#journey" className="ref-btn-primary-glow">
              <span>Book Your Journey</span>
              <span>→</span>
            </a>
            <Link href="/laptop/train-details" className="ref-btn-ghost">
              <span>▶ Explore Trains</span>
            </Link>
          </div>
        </div>
      </section>

      {/* 3. Reference Multi-Tab Glass Booking Widget */}
      <ReferenceBookingEngine
        isMobile={false}
        onSearch={handleSearch}
        loading={loading}
      />

      {/* 4. Six Feature Service Cards + Vande Bharat Banner */}
      <ReferenceServicesRow />

      {/* 5. Live Status, Popular Trains & Value Proposition Grid */}
      <ReferencePopularSection />

      {/* 6. Footer matching Reference Image */}
      <ReferenceFooter />
    </div>
  );
}
