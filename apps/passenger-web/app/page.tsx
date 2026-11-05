import Link from "next/link";
import { DeviceRouter } from "../components/device-router";
import { Card } from "@railconnect/ui";

export default function PassengerHomePage() {
  return (
    <DeviceRouterFallback>
      <DeviceRouter />
    </DeviceRouterFallback>
  );
}

function DeviceRouterFallback({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <div>
      {children}
      <noscript>
        <Card className="device-router">
          <p>Automatic device selection needs JavaScript.</p>
          <div className="device-router-links">
            <Link className="button button--secondary" href="/mobile">
              Mobile webpage
            </Link>
            <Link className="button button--primary" href="/laptop">
              Laptop webpage
            </Link>
          </div>
        </Card>
      </noscript>
    </div>
  );
}
