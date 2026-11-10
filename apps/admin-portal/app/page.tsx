import { AdminIdentityConsole } from "../components/admin-identity-console";
import { TicketAdminConsole } from "../components/ticket-admin-console";

export default function AdminHomePage() {
  return (
    <div className="admin-shell">
      <AdminIdentityConsole />
      <main className="admin-main">
        <TicketAdminConsole />
      </main>
      <footer className="site-footer">
        Staff operations are server-authorized; reservation policy and release actions require separate permissions.
      </footer>
    </div>
  );
}
