import { Metadata } from "next";
import { NotificationCenterView } from "../../components/notification-center-view";
import { PassengerHeader } from "../../components/passenger-header";

export const metadata: Metadata = {
  title: "Notifications & Alerts | RailConnect",
  description: "Passenger Notification Center, Browser Push Settings, and Travel Alerts",
};

export default function NotificationsPage() {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      <PassengerHeader experience="laptop" />
      <main className="flex-1">
        <NotificationCenterView />
      </main>
    </div>
  );
}
