import { AdminIdentityConsole } from "../components/admin-identity-console";

export default function AdminHomePage() {
  return (
    <div className="admin-shell">
      <AdminIdentityConsole />
      <footer className="site-footer">
        Staff access is server-authorized. Search, seat availability, and reservations are outside Phase 3.
      </footer>
    </div>
  );
}
