"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Brand, Button, Card } from "@railconnect/ui";

export function DeviceRouter() {
  const router = useRouter();

  useEffect(() => {
    const experience = window.matchMedia("(max-width: 760px)").matches
      ? "mobile"
      : "laptop";
    router.replace(`/${experience}`);
  }, [router]);

  return (
    <main className="device-router" id="main-content">
      <Brand />
      <Card>
        <p className="eyebrow">Passenger portal</p>
        <h1>Choose your RailConnect view</h1>
        <p>
          We are opening the view that best fits this browser. You can also
          choose either experience.
        </p>
        <div className="device-router-links">
          <Button
            onClick={() => router.push("/mobile")}
            type="button"
            variant="secondary"
          >
            Mobile webpage
          </Button>
          <Button onClick={() => router.push("/laptop")} type="button">
            Laptop webpage
          </Button>
        </div>
      </Card>
    </main>
  );
}
