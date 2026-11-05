import { Card } from "@railconnect/ui";

export function ExperienceStatus() {
  return (
    <Card className="mobile-status-card laptop-card" id="status">
      <span aria-hidden="true" className="status-icon">
        i
      </span>
      <div>
        <h2>Passenger services are being prepared</h2>
        <p>
          Train search, availability, and booking are not active in this
          foundation phase. No train inventory or payment is being offered.
        </p>
      </div>
    </Card>
  );
}
