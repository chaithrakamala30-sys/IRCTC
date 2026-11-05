"use client";

import { useEffect } from "react";
import { Button, Card } from "@railconnect/ui";

export default function PassengerError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("RailConnect passenger page error", {
      name: error.name,
      digest: error.digest,
    });
  }, [error]);

  return (
    <main className="page-message">
      <Card>
        <p className="eyebrow">Something went wrong</p>
        <h1>We could not load this page.</h1>
        <p>The error has been recorded without exposing sensitive details.</p>
        <Button onClick={reset} type="button">
          Try again
        </Button>
      </Card>
    </main>
  );
}
