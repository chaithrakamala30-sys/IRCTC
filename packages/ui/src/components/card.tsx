import type { HTMLAttributes } from "react";

type CardProps = HTMLAttributes<HTMLElement> & {
  as?: "article" | "section" | "div";
};

export function Card({
  as: Element = "section",
  className = "",
  ...props
}: CardProps) {
  return <Element className={`card ${className}`.trim()} {...props} />;
}
