import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "RailConnect Identity Administration",
  description: "Restricted staff identity, access, and security audit administration.",
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
