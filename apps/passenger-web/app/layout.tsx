import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "RailConnect | A clearer journey",
    template: "%s | RailConnect",
  },
  description:
    "Search published RailConnect train routes and schedules. Fares, seat availability, and booking are not enabled.",
};

export const viewport: Viewport = {
  themeColor: "#1056b5",
  width: "device-width",
  initialScale: 1,
};

export default function PassengerLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html data-scroll-behavior="smooth" lang="en">
      <body>{children}</body>
    </html>
  );
}
