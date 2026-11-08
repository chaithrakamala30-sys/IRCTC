import { Brand, PortalNavigation } from "@railconnect/ui";
import { AdminIdentityConsole } from "../components/admin-identity-console";

export default function AdminHomePage() {
  return (
    <div className="admin-shell">
      <header className="admin-header">
        <Brand href="/" />
        <PortalNavigation
          items={[
            { href: "/", label: "Identity administration" },
            { href: "#master-data", label: "Railway master data" },
          ]}
        />
      </header>
      <AdminIdentityConsole />
      <footer className="site-footer">
        Staff access is server-authorized. Search, seat availability, and reservations are outside Phase 3.
      </footer>
    </div>
  );
}
