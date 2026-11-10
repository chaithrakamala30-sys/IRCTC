import { Suspense } from "react";
import { TicketVerificationRoute } from "../../components/ticket-verification-route";

export const metadata = {
  title: "Verify RailConnect ticket",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default function TicketVerificationPage() {
  return (
    <div className="page-shell">
      <Suspense fallback={<main className="ticket-verify-shell"><p role="status">Loading verification…</p></main>}>
        <TicketVerificationRoute />
      </Suspense>
      <footer className="site-footer">
        RailConnect is independent. Verification checks only its own simulated reservation record.
      </footer>
    </div>
  );
}
