import { Metadata } from "next";
import { NotificationCenterView } from "../../components/notification-center-view";
import { ReferenceHeader } from "../../components/reference-header";

export const metadata: Metadata = {
  title: "Notifications & Alerts | RailConnect",
  description: "Passenger Notification Center, Browser Push Settings, and Travel Alerts",
};

export default function NotificationsPage() {
  return (
    <div className="min-h-screen bg-[#040914] text-slate-100 flex flex-col">
      <ReferenceHeader experience="laptop" activeTab="more" />
      <main className="flex-1">
        <NotificationCenterView />
      </main>
    </div>
  );
}
