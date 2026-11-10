"use client";

import { useSearchParams } from "next/navigation";
import { TicketVerification } from "./ticket-verification";

export function TicketVerificationRoute() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token");

  if (!token) {
    return (
      <main className="ticket-verify-shell">
        <p className="eyebrow">Independent reservation platform</p>
        <h1>Ticket verification</h1>
        <p role="alert">A signed RailConnect verification token is required.</p>
      </main>
    );
  }

  return <TicketVerification token={token} />;
}
