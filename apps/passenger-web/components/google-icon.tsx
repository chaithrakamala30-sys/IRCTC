"use client";

import React from "react";

type GoogleIconProps = {
  name: string;
  fill?: boolean;
  weight?: number;
  grade?: number;
  size?: number;
  className?: string;
  style?: React.CSSProperties;
  color?: string;
};

export function GoogleIcon({
  name,
  fill = false,
  weight = 500,
  grade = 0,
  size = 20,
  className = "",
  style = {},
  color,
}: GoogleIconProps) {
  return (
    <span
      className={`material-symbols-rounded select-none leading-none inline-flex items-center justify-center ${className}`.trim()}
      style={{
        fontVariationSettings: `'FILL' ${fill ? 1 : 0}, 'wght' ${weight}, 'GRAD' ${grade}, 'opsz' ${size}`,
        fontSize: `${size}px`,
        width: `${size}px`,
        height: `${size}px`,
        color: color || "currentColor",
        verticalAlign: "middle",
        ...style,
      }}
      aria-hidden="true"
    >
      {name}
    </span>
  );
}

