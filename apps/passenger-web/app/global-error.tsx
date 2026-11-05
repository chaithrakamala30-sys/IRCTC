"use client";

import { Button, Card } from "@railconnect/ui";
import "./globals.css";

export default function GlobalError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <html lang="en">
      <body>
        <main className="page-message">
          <Card>
            <h1>RailConnect is temporarily unavailable.</h1>
            <p>Please retry. No booking or payment was submitted by this page.</p>
            <Button onClick={reset} type="button">
              Try again
            </Button>
          </Card>
        </main>
      </body>
    </html>
  );
}
