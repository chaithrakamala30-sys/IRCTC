import {
  Brand,
  DeviceExperienceSwitch,
  PortalNavigation,
  type NavigationItem,
} from "@railconnect/ui";

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
        <PortalNavigation activeHref="/laptop" items={navigationItems} />
      ) : (
        <DeviceExperienceSwitch current={experience} />
      )}
    </header>
  );
}
