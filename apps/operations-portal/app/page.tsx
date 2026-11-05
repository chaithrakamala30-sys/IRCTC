import { Brand, Card, PortalNavigation } from "@railconnect/ui";

export default function OperationsHomePage() {
  return (
    <div className="ops-shell">
      <header className="ops-header">
        <Brand href="/" />
        <PortalNavigation
          items={[
            { href: "/", label: "Operations" },
            { href: "#status", label: "System status" },
          ]}
        />
      </header>
      <main className="ops-main" id="main-content">
        <div className="ops-title">
          <p className="eyebrow">Railway operations</p>
          <h1>Operational awareness foundation</h1>
          <p>
            Run status, disruptions, and station operations are not connected
            yet. No live railway data is being displayed.
          </p>
        </div>

        <section aria-label="Operations readiness" className="ops-grid" id="status">
          <Card className="ops-card ops-unavailable">
            <span aria-hidden="true" className="status-icon">
              i
            </span>
            <div>
              <h2>Operational feeds are not configured</h2>
              <p>
                This portal does not connect to railway systems or show
                fabricated train status. Approved data sources and permissions
                are required before operations features can be enabled.
              </p>
            </div>
          </Card>
          <Card className="ops-card">
            <p className="eyebrow">Foundation phase</p>
            <h2>No operational actions</h2>
            <p>
              No disruption, cancellation, announcement, or inventory control
              can be performed from this portal.
            </p>
            <p className="site-footer">
              Access is not enabled for this portal foundation.
            </p>
          </Card>
        </section>
      </main>
    </div>
  );
}
