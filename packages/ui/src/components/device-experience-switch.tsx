import Link from "next/link";

type DeviceExperienceSwitchProps = {
  current: "laptop" | "mobile";
};

export function DeviceExperienceSwitch({
  current,
}: DeviceExperienceSwitchProps) {
  const alternate = current === "mobile" ? "laptop" : "mobile";
  const label =
    current === "mobile" ? "Switch to laptop view" : "Switch to mobile view";

  return (
    <Link className="device-switch" href={`/${alternate}`}>
      {label}
    </Link>
  );
}
