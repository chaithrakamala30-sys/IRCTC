import {
  Brand,
  DeviceExperienceSwitch,
  PortalNavigation,
  type NavigationItem,
} from "@railconnect/ui";
import Link from "next/link";

const navigationItems: NavigationItem[] = [
  { href: "/laptop", label: "Home" },
  { href: "#journey", label: "Journey" },
  { href: "#status", label: "Service status" },
];

type PassengerHeaderProps = {
  experience: "laptop" | "mobile";
};

export function PassengerHeader({ experience }: PassengerHeaderProps) {
  return (
    <header className="site-header">
      <Brand href={`/${experience}`} />
      {experience === "laptop" ? (
        <div className="passenger-header-actions">
          <PortalNavigation activeHref="/laptop" items={navigationItems} />
          <Link className="button button--secondary" href="/laptop/login">Sign in</Link>
          <Link className="button button--primary" href="/laptop/register">Create account</Link>
        </div>
      ) : (
        <div className="passenger-header-actions">
          <DeviceExperienceSwitch current={experience} />
          <Link className="header-account-link" href="/mobile/login">Sign in</Link>
          <Link className="header-account-link" href="/mobile/register">Join</Link>
        </div>
      )}
    </header>
  );
}
