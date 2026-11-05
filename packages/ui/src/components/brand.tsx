import Link from "next/link";

type BrandProps = {
  href?: string;
  compact?: boolean;
};

export function Brand({ href = "/", compact = false }: BrandProps) {
  return (
    <Link aria-label="RailConnect home" className="brand" href={href}>
      <span aria-hidden="true" className="brand-mark">
        R
      </span>
      {!compact && (
        <span className="brand-copy">
          <strong>RailConnect</strong>
          <span>Travel, clearly connected</span>
        </span>
      )}
    </Link>
  );
}
