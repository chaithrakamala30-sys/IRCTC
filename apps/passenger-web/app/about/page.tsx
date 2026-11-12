import Link from "next/link";
import { ReferenceHeader } from "../../components/reference-header";
import { GoogleIcon } from "../../components/google-icon";

export const metadata = {
  title: "About Us | RailConnect",
  description: "About RailConnect — Enterprise Passenger Rail Platform",
};

export default function AboutPage() {
  return (
    <div className="page-shell bg-[#040914] text-[#eaf3ff] min-h-screen">
      <ReferenceHeader experience="laptop" />
      <main className="legal-page max-w-4xl mx-auto px-4 py-10">
        <Link href="/laptop" className="text-[#38bdf8] hover:underline mb-6 inline-flex items-center gap-1.5 text-sm font-semibold">
          <GoogleIcon name="arrow_back" size={16} />
          <span>Back to Home</span>
        </Link>
        
        <div className="mb-6">
          <span className="text-xs uppercase tracking-widest font-bold text-[#73dfff] bg-[#1877f2]/20 px-3 py-1 rounded-full border border-[#258bff]/30">
            About RailConnect
          </span>
        </div>

        <h1 className="text-3xl font-extrabold text-white mb-4">
          Empowering Seamless Rail Travel Across India
        </h1>

        <p className="text-slate-300 leading-relaxed mb-6">
          RailConnect is a modern railway reservation and passenger intelligence platform engineered
          to deliver high-speed ticket search, seat availability forecasting, real-time live train tracking,
          and instant digital ticketing.
        </p>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 my-8">
          <div className="p-5 rounded-2xl bg-[#0a1730]/80 border border-[#258bff]/30">
            <GoogleIcon name="radar" size={28} color="#38bdf8" />
            <h3 className="font-bold text-lg text-white mt-3 mb-1">Live Telemetry</h3>
            <p className="text-xs text-slate-400">
              Station arrival forecasts and platform delay updates powered by operational control logs.
            </p>
          </div>

          <div className="p-5 rounded-2xl bg-[#0a1730]/80 border border-[#258bff]/30">
            <GoogleIcon name="shield" size={28} color="#4ade80" />
            <h3 className="font-bold text-lg text-white mt-3 mb-1">Zero Convenience Fees</h3>
            <p className="text-xs text-slate-400">
              Direct UPI integration with transparent fare breakdowns and zero payment gateway charges.
            </p>
          </div>

          <div className="p-5 rounded-2xl bg-[#0a1730]/80 border border-[#258bff]/30">
            <GoogleIcon name="bolt" size={28} color="#f59e0b" />
            <h3 className="font-bold text-lg text-white mt-3 mb-1">Instant PNR Insights</h3>
            <p className="text-xs text-slate-400">
              Confirmation probability engine, coach placement guides, and automated chart preparation alerts.
            </p>
          </div>
        </div>

        <h2 className="text-xl font-bold text-white mt-8 mb-3">Our Mission</h2>
        <p className="text-slate-300 leading-relaxed mb-4">
          To simplify India&apos;s rail travel experience through ultra-responsive digital architecture,
          dark-mode-first glassmorphism interfaces, and accessible customer support channels available 24x7.
        </p>
      </main>
    </div>
  );
}
