import Link from "next/link";

export const metadata = { title: "Passenger terms" };

export default function PassengerTermsPage() {
  return (
    <main className="legal-page">
      <Link href="/mobile">Back to RailConnect</Link>
      <p className="eyebrow">Version phase2-2026-11</p>
      <h1>Passenger account terms</h1>
      <p>
        RailConnect is an independent passenger-platform project and is not an
        official railway or IRCTC service. No live train search, reservation,
        ticketing, payment, or railway-system integration is available in this
        phase.
      </p>
      <h2>Account use</h2>
      <p>
        Provide accurate account details, keep your password confidential, and
        notify the operator if you believe someone else accessed your account.
        You are responsible for activity performed through an authenticated
        session until that session is revoked.
      </p>
      <h2>Current service limits</h2>
      <p>
        Email and mobile ownership are not verified. Password recovery and
        authenticator-app MFA are not available. Do not use an account as a
        substitute for an official railway service or submit payment details.
      </p>
      <h2>Availability and account controls</h2>
      <p>
        The service is a development foundation and may be changed or
        unavailable. Account profile editing is limited to the display name;
        account deletion and self-service contact changes are not currently
        available.
      </p>
      <p>
        See the <Link href="/privacy">privacy notice</Link> for information
        about account and security data.
      </p>
    </main>
  );
}
