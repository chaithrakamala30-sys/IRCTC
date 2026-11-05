import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "RailConnect Administration",
  description:
    "RailConnect administration portal foundation. Operational actions are not enabled.",
};

export const viewport: Viewport = {
  themeColor: "#102b50",
  width: "device-width",
  initialScale: 1,
};

export default function AdminLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
