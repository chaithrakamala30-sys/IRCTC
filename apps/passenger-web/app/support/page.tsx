import { ReferenceHeader } from "../../components/reference-header";
import { PassengerSupportCenter } from "../../components/passenger-support-center";
import { ReferenceFooter } from "../../components/reference-footer";

export const metadata = {
  title: "Customer Support & 24x7 Helpdesk | RailConnect",
  description: "Raise disputes, track tickets, link bookings, and communicate with RailConnect support staff.",
};

export default function SupportPage() {
  return (
    <div className="min-h-screen bg-[#040914] text-[#eaf3ff] flex flex-col justify-between">
      <div>
        <ReferenceHeader experience="laptop" activeTab="services" />
        <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
          <PassengerSupportCenter />
        </main>
      </div>
      <ReferenceFooter />
    </div>
  );
}
