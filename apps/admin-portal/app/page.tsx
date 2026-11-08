import { AdminIdentityConsole } from "../components/admin-identity-console";

export default function AdminHomePage() {
  return (
    <div className="admin-shell">
      <AdminIdentityConsole />
      <footer className="site-footer">
        Staff operations are server-authorized; reservation policy and release actions require separate permissions.
      </footer>
    </div>
  );
}
