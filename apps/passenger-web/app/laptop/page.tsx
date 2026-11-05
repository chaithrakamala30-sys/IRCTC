import Link from "next/link";
import {
  Card,
  DeviceExperienceSwitch,
  PortalNavigation,
  type NavigationItem,
} from "@railconnect/ui";
import { ExperienceStatus } from "../../components/experience-status";
import { PassengerHeader } from "../../components/passenger-header";

export const metadata = {
  title: "Laptop passenger view",
};

const desktopItems: NavigationItem[] = [
  { href: "/laptop", label: "Home" },
  { href: "#journey", label: "Passenger portal" },
  { href: "#status", label: "Service status" },
];

export default function PassengerLaptopPage() {
  return (
    <div className="page-shell">
      <PassengerHeader experience="laptop" />
      <main className="passenger-laptop" id="main-content">
        <div className="laptop-topline">
          <p className="eyebrow">Passenger portal</p>
          <span className="status-pill">
            <span aria-hidden="true" className="status-dot" />
            Foundation phase
          </span>
        </div>

        <section aria-labelledby="laptop-hero-title" className="laptop-main">
          <div className="laptop-hero" id="journey">
            <div className="laptop-hero-copy">
              <p className="eyebrow">RailConnect passenger experience</p>
              <h1 id="laptop-hero-title">
                Your journey, <span>made clearer.</span>
              </h1>
              <p>
                A dedicated laptop webpage for exploring services managed by
                RailConnect, designed around clear routes, dependable
                information, and accessible interactions.
              </p>
              <div className="switch-actions">
                <Link className="button button--primary" href="#status">
                  View service status
                </Link>
                <DeviceExperienceSwitch current="laptop" />
              </div>
            </div>
            <div
              aria-label="Illustration of a connected journey between stations"
              className="route-illustration"
              role="img"
            >
              <span aria-hidden="true" className="route-line" />
              <span aria-hidden="true" className="route-stop">
                A
              </span>
              <span aria-hidden="true" className="route-stop">
                B
              </span>
            </div>
          </div>

          <div className="laptop-details">
            <Card className="laptop-card">
              <p className="eyebrow">Built for clarity</p>
              <h2>Journey information</h2>
              <p>
                Planned routes and service information will be shown only when
                verified data is available.
              </p>
            </Card>
            <Card className="laptop-card">
              <p className="eyebrow">Accessible by design</p>
              <h2>Passenger-first experience</h2>
              <p>
                Keyboard navigation, readable typography, and clear status
                communication are part of the foundation.
              </p>
            </Card>
            <Card className="laptop-card">
              <p className="eyebrow">No live services yet</p>
              <h2>Booking is not enabled</h2>
              <p>
                This page does not display train availability, accept
                reservations, or process payments.
              </p>
            </Card>
          </div>

          <div className="laptop-details" id="status">
            <ExperienceStatus />
          </div>
        </section>
      </main>
      <footer className="site-footer laptop-footer">
        <span>RailConnect — independent passenger platform foundation</span>
        <PortalNavigation items={desktopItems} />
      </footer>
    </div>
  );
}
