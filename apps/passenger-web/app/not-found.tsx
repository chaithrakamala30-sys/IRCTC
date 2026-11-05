import Link from "next/link";
import { Card } from "@railconnect/ui";

export default function NotFound() {
  return (
    <main className="page-message">
      <Card>
        <p className="eyebrow">Page not found</p>
        <h1>This page is not available.</h1>
        <p>Choose the passenger webpage that suits this browser.</p>
        <div className="switch-actions">
          <Link className="button button--secondary" href="/mobile">
            Mobile view
          </Link>
          <Link className="button button--primary" href="/laptop">
            Laptop view
          </Link>
        </div>
      </Card>
    </main>
  );
}
