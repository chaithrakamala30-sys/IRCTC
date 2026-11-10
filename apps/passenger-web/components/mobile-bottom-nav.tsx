"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

export function MobileBottomNav({ unreadCount = 0 }: { unreadCount?: number }) {
  const pathname = usePathname();

  const isCurrent = (path: string) => {
    if (path === "/mobile" || path === "/") {
      return pathname === "/" || pathname === "/mobile" || pathname === "/laptop";
    }
    return pathname.startsWith(path);
  };

  return (
    <nav
      aria-label="Mobile Bottom Quick Navigation"
      className="mobile-bottom-nav"
    >
      <Link
        aria-current={isCurrent("/mobile") ? "page" : undefined}
        className={`mobile-bottom-nav-item ${isCurrent("/mobile") ? "is-active" : ""}`}
        href="/mobile"
      >
        <svg aria-hidden="true" fill="none" height="22" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" width="22">
          <path d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <span>Search</span>
      </Link>

      <Link
        aria-current={isCurrent("/mobile/account") ? "page" : undefined}
        className={`mobile-bottom-nav-item ${isCurrent("/mobile/account") ? "is-active" : ""}`}
        href="/mobile/account"
      >
        <svg aria-hidden="true" fill="none" height="22" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" width="22">
          <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <span>Bookings</span>
      </Link>

      <Link
        aria-current={isCurrent("/verify-ticket") ? "page" : undefined}
        className={`mobile-bottom-nav-item ${isCurrent("/verify-ticket") ? "is-active" : ""}`}
        href="/verify-ticket"
      >
        <svg aria-hidden="true" fill="none" height="22" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" width="22">
          <path d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <span>PNR / Verify</span>
      </Link>

      <Link
        aria-current={isCurrent("/notifications") ? "page" : undefined}
        className={`mobile-bottom-nav-item ${isCurrent("/notifications") ? "is-active" : ""}`}
        href="/notifications"
      >
        <div style={{ position: "relative", display: "inline-flex" }}>
          <svg aria-hidden="true" fill="none" height="22" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" width="22">
            <path d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          {unreadCount > 0 && (
            <span
              aria-label={`${unreadCount} unread alerts`}
              className="bottom-nav-badge"
            >
              {unreadCount > 9 ? "9+" : unreadCount}
            </span>
          )}
        </div>
        <span>Alerts</span>
      </Link>

      <Link
        aria-current={isCurrent("/support") ? "page" : undefined}
        className={`mobile-bottom-nav-item ${isCurrent("/support") ? "is-active" : ""}`}
        href="/support"
      >
        <svg aria-hidden="true" fill="none" height="22" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" width="22">
          <path d="M18.364 5.636l-3.536 3.536m0 5.656l3.536 3.536M9.172 9.172L5.636 5.636m3.536 9.192l-3.536 3.536M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-5 0a4 4 0 11-8 0 4 4 0 018 0z" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <span>Helpdesk</span>
      </Link>
    </nav>
  );
}

