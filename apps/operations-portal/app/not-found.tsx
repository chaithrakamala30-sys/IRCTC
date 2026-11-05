import Link from "next/link";
import { Card } from "@railconnect/ui";

export default function NotFound() {
  return (
    <main className="page-message">
      <Card>
        <h1>Operations page not found.</h1>
        <Link href="/">Return to the operations foundation</Link>
      </Card>
    </main>
  );
}
