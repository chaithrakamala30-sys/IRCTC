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
            { href: "#accounts", label: "Accounts" },
          ]}
        />
      </header>
      <AdminIdentityConsole />
      <footer className="site-footer">
        Staff access is server-authorized. Railway operations and reservations remain disabled.
      </footer>
    </div>
  );
}
