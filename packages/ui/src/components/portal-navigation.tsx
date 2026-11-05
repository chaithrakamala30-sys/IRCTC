import Link from "next/link";

export type NavigationItem = {
  href: string;
  label: string;
};

type PortalNavigationProps = {
  activeHref?: string;
  items: NavigationItem[];
  mobile?: boolean;
};

export function PortalNavigation({
  activeHref,
  items,
  mobile = false,
}: PortalNavigationProps) {
  return (
    <nav
      aria-label="Primary navigation"
      className={mobile ? "portal-nav portal-nav--mobile" : "portal-nav"}
    >
      {items.map((item) => (
        <Link
          aria-current={activeHref === item.href ? "page" : undefined}
          className="portal-nav-link"
          href={item.href}
          key={item.href}
        >
          {item.label}
        </Link>
      ))}
    </nav>
  );
}
