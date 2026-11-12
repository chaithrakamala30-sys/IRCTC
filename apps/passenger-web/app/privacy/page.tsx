import Link from "next/link";
import { ReferenceHeader } from "../../components/reference-header";

export const metadata = { title: "Privacy notice" };

export default function PassengerPrivacyPage() {
  return (
    <div className="page-shell bg-[#040914] text-[#eaf3ff] min-h-screen">
      <ReferenceHeader experience="laptop" />
      <main className="legal-page max-w-4xl mx-auto px-4 py-8">
        <Link href="/laptop" className="text-[#38bdf8] hover:underline mb-4 inline-block">
          ← Back to RailConnect
        </Link>
        <p className="eyebrow">Version phase2-2026-11</p>
        <h1>Passenger account privacy notice</h1>
      <p>
        RailConnect is an independent project, not an official railway or
        IRCTC service. This notice describes data handled by the Phase 2
        identity foundation.
      </p>
      <h2>Data collected</h2>
      <p>
        The account stores your name, email address, international mobile
        number, Supabase Auth identity reference, terms/privacy version acceptance,
        account status, and timestamps. Email and mobile addresses are not
        verified in this phase.
      </p>
      <h2>Security and session data</h2>
      <p>
        The service stores browser sessions, session user-agent information,
        hashed network addresses, authentication outcomes, and security audit
        events. Passwords, session credentials, and raw network addresses are
        not written to audit records.
      </p>
      <h2>Purpose and sharing</h2>
      <p>
        Account and security data are used to authenticate users, enforce
        access, prevent repeated sign-in failures, and investigate security
        events. Phase 2 does not send account data to paid identity, email, or
        SMS providers. There are no live booking or payment records.
      </p>
      <h2>Current choices and retention</h2>
      <p>
        You can update your name, change your password, and revoke browser
        sessions. Self-service account deletion, contact changes, password
        recovery, and a published retention schedule are not yet available.
        The operator must define retention and deletion procedures before
        production use.
      </p>
      <p>
        For account terms, see the <Link href="/terms">passenger terms</Link>.
      </p>
    </main>
  </div>
  );
}
