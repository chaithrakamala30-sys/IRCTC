"use client";

import Link from "next/link";

export function ReferenceFooter() {
  return (
    <footer className="ref-site-footer">
      <div className="ref-footer-content">
        <div className="ref-footer-brand">
          <div className="flex items-center gap-2">
            <span className="ref-footer-logo">RC</span>
            <strong className="text-white text-base">RailConnect</strong>
          </div>
          <p className="text-xs text-[#8fa3c7] mt-1">Travel India. Together.</p>
        </div>

        <nav aria-label="Footer Navigation" className="ref-footer-links">
          <Link href="/about">About Us</Link>
          <span className="ref-dot-sep">|</span>
          <Link href="/privacy">Privacy Policy</Link>
          <span className="ref-dot-sep">|</span>
          <Link href="/terms">Terms & Conditions</Link>
          <span className="ref-dot-sep">|</span>
          <Link href="/support">Contact Us</Link>
        </nav>

        <div className="ref-footer-ir-notice">
          <span className="text-xs text-[#73dfff] font-semibold">
            Indian Railways 🇮🇳 | A Government of India Initiative
          </span>
        </div>
      </div>
    </footer>
  );
}

