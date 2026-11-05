"use client";

import { useEffect } from "react";
import { Button, Card } from "@railconnect/ui";

export default function AdminError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("RailConnect admin page error", {
      name: error.name,
      digest: error.digest,
    });
  }, [error]);

  return (
    <main className="page-message">
      <Card>
        <h1>We could not load this page.</h1>
        <p>Try again or contact the platform owner if the problem continues.</p>
        <Button onClick={reset} type="button">
          Try again
        </Button>
      </Card>
    </main>
  );
}
