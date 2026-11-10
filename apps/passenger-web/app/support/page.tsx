import { Metadata } from "next";
import { PassengerSupportCenter } from "../../components/passenger-support-center";
import { PassengerHeader } from "../../components/passenger-header";

export const metadata: Metadata = {
  title: "Support & Helpdesk | RailConnect",
  description: "Customer Support, Inquiries, Complaints, and Resolution Assistance",
};

export default function SupportPage() {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      <PassengerHeader experience="laptop" />
      <main className="flex-1">
        <PassengerSupportCenter />
      </main>
    </div>
  );
}
