import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "RailConnect Operations",
  description:
    "RailConnect railway operations portal foundation. Operational actions are not enabled.",
};

export const viewport: Viewport = {
  themeColor: "#102b50",
  width: "device-width",
  initialScale: 1,
};

export default function OperationsLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
