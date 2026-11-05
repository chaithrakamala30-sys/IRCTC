import Link from "next/link";
import { Card, DeviceExperienceSwitch } from "@railconnect/ui";
import { ExperienceStatus } from "../../components/experience-status";
import { PassengerHeader } from "../../components/passenger-header";

export const metadata = {
  title: "Mobile passenger view",
};

const mobileItems = [
  { href: "/mobile", label: "Home" },
  { href: "#journey", label: "Journey" },
  { href: "#status", label: "Status" },
];

export default function PassengerMobilePage() {
  return (
    <div className="page-shell">
      <PassengerHeader experience="mobile" />
      <main className="passenger-mobile" id="main-content">
        <div className="mobile-topline">
          <p className="eyebrow">Passenger portal</p>
          <span className="status-pill">
            <span aria-hidden="true" className="status-dot" />
            Foundation phase
          </span>
        </div>

        <section aria-labelledby="mobile-hero-title" className="mobile-main">
          <div className="mobile-hero" id="journey">
            <p className="eyebrow">Your journey, thoughtfully connected</p>
            <h1 id="mobile-hero-title">A clearer way to travel.</h1>
            <p>
              RailConnect is building an independent passenger experience for
              journeys managed by RailConnect.
            </p>
          </div>

          <ExperienceStatus />

          <Card className="mobile-card">
            <h2>Designed for your phone</h2>
            <p>
              This dedicated mobile webpage keeps information in a simple
              single-column flow, with touch-friendly navigation and readable
              status details.
            </p>
            <div className="switch-actions">
              <DeviceExperienceSwitch current="mobile" />
            </div>
          </Card>

          <Card className="mobile-card">
            <h2>What happens next?</h2>
            <p>
              Search and reservation features will be introduced only after
              their data, rules, and security foundations are ready.
            </p>
          </Card>
        </section>
      </main>

      <nav aria-label="Mobile navigation" className="mobile-nav">
        {mobileItems.map((item) => (
          <Link
            aria-current={item.href === "/mobile" ? "page" : undefined}
            href={item.href}
            key={item.href}
          >
            {item.label}
          </Link>
        ))}
      </nav>

      <footer className="site-footer">
        RailConnect is independent and is not an official railway service.
      </footer>
    </div>
  );
}
