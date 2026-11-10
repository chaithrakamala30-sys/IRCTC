import { PassengerHeader } from "../../components/passenger-header";
import { PassengerSupportCenter } from "../../components/passenger-support-center";

export const metadata = {
  title: "Customer Support & Complaints | RailConnect",
  description: "Raise disputes, track tickets, link bookings, and communicate with RailConnect support staff.",
};

export default function SupportPage() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <PassengerHeader experience="laptop" />
      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <PassengerSupportCenter />
      </main>
    </div>
  );
}

