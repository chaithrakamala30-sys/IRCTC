import type { Metadata, Viewport } from "next";
import "./globals.css";
import { ThemeProvider } from "../components/theme-provider";
import { MobileBottomNav } from "../components/mobile-bottom-nav";

export const metadata: Metadata = {
  title: {
    default: "RailConnect | Enterprise Railway Reservation Platform",
    template: "%s | RailConnect",
  },
  description:
    "Next-generation enterprise railway booking, train search, real-time schedule tracking, and reservation platform.",
};

export const viewport: Viewport = {
  themeColor: "#ea580c",
  width: "device-width",
  initialScale: 1,
};

export default function PassengerLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html data-scroll-behavior="smooth" lang="en" suppressHydrationWarning>
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200"
        />
        <script
          dangerouslySetInnerHTML={{
            __html: `
              (function() {
                try {
                  var saved = localStorage.getItem('railconnect_theme');
                  var pref = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
                  var resolved = (saved === 'dark' || saved === 'light') ? saved : pref;
                  document.documentElement.classList.add(resolved);
                  document.documentElement.style.colorScheme = resolved;
                } catch (e) {}
              })();
            `,
          }}
        />
      </head>
      <body className="min-h-screen bg-slate-50 text-slate-900 transition-colors duration-200 dark:bg-[#090d16] dark:text-slate-100 antialiased selection:bg-orange-500 selection:text-white">
        <ThemeProvider>
          {children}
          <MobileBottomNav />
        </ThemeProvider>
      </body>
    </html>
  );
}
