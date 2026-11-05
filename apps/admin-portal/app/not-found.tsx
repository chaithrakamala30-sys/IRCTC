import Link from "next/link";
import { Card } from "@railconnect/ui";

export default function NotFound() {
  return (
    <main className="page-message">
      <Card>
        <h1>Administration page not found.</h1>
        <Link href="/">Return to the portal foundation</Link>
      </Card>
    </main>
  );
}
