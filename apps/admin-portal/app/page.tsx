import { Brand, Card, PortalNavigation } from "@railconnect/ui";

const sections = [
  {
    title: "Railway catalogue",
    description:
      "Station, train, route, schedule, class, and coach configuration will be added in later phases.",
  },
  {
    title: "Inventory and rules",
    description:
      "Inventory imports and versioned commercial rules require approved data and are not available yet.",
  },
  {
    title: "Finance and audit",
    description:
      "No payment, refund, passenger, or administrative records exist in this foundation.",
  },
];

export default function AdminHomePage() {
  return (
    <div className="admin-shell">
      <header className="admin-header">
        <Brand href="/" />
        <PortalNavigation
          items={[
            { href: "/", label: "Overview" },
            { href: "#modules", label: "Planned modules" },
          ]}
        />
      </header>
      <main className="admin-main" id="main-content">
        <div className="admin-title-row">
          <div>
            <p className="eyebrow">Administration portal</p>
            <h1>Railway management foundation</h1>
            <p>
              Administrative features are not active. No records can be
              changed from this page.
            </p>
          </div>
          <span className="status-pill">
            <span aria-hidden="true" className="status-dot" />
            Phase 1 foundation
          </span>
        </div>

        <section aria-label="Planned administration modules" className="admin-grid" id="modules">
          {sections.map((section) => (
            <Card className="admin-card" key={section.title}>
              <p className="eyebrow">Planned module</p>
              <h2>{section.title}</h2>
              <p>{section.description}</p>
              <span className="status-pill">Not enabled</span>
            </Card>
          ))}
        </section>

        <p className="site-footer">
          Access control foundations do not grant any operational permissions.
        </p>
      </main>
    </div>
  );
}
