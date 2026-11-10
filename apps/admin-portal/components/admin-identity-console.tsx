"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { ApiRequestError, apiFetch } from "@railconnect/api";
import { Brand } from "@railconnect/ui";
import { AdminMasterDataConsole } from "./admin-master-data-console";
import { AdminFareConsole } from "./admin-fare-console";
import { AdminInventoryConsole } from "./admin-inventory-console";
import { AdminReservationConsole } from "./admin-reservation-console";
import { AdminPaymentConsole } from "./admin-payment-console";
import { TicketAdminConsole } from "./ticket-admin-console";
import { AdminCancellationConsole } from "./admin-cancellation-console";
import { AdminQuotaConsole } from "./admin-quota-console";
import { AdminNotificationConsole } from "./admin-notification-console";
import { SupportStaffConsole } from "./support-staff-console";
import { AdminExecutiveDashboard } from "./admin-executive-dashboard";
import { AdminApprovalConsole } from "./admin-approval-console";
import { AdminFinanceConsole } from "./admin-finance-console";
import { AdminReportsConsole } from "./admin-reports-console";
import { AdminSystemConfigConsole } from "./admin-system-config-console";
import { ThemeToggle } from "./theme-toggle";

type AdminUser = {
  userId: string;
  fullName: string;
  email: string;
  mobileNumber: string;
  status: string;
  emailVerified: boolean;
  mobileVerified: boolean;
  mustChangePassword: boolean;
  roles: { roleCode: string; scopeType: string; scopeId: string }[];
  createdAt: string;
};
type CurrentUser = {
  userId: string;
  fullName: string;
  roles: string[];
  mustChangePassword: boolean;
};

type Page<T> = { items: T[]; page: number; size: number; totalItems: number };
type Role = { roleCode: string; displayName: string; permissions: string[] };
type Permission = { permissionCode: string; description: string };
type AuditEntry = {
  auditId: string;
  actorUserId: string | null;
  actorName: string | null;
  targetUserId: string | null;
  targetName: string | null;
  eventType: string;
  outcome: string;
  correlationId: string;
  occurredAt: string;
};
type AuthEvent = {
  eventId: string;
  userId: string | null;
  eventType: string;
  outcome: string;
  correlationId: string;
  occurredAt: string;
};
type AdminSession = {
  sessionId: string;
  createdAt: string;
  lastSeenAt: string;
  userAgent: string;
  current: boolean;
};

type AdminTab = "dashboard" | "identity" | "approvals" | "finance" | "reports" | "master-data" | "fares" | "inventory" | "reservations" | "quotas" | "payments" | "tickets" | "cancellations" | "notifications" | "support" | "config";

type AdminTabItem = {
  id: AdminTab;
  label: string;
};

function AdminPortalHeader({
  user,
  tabs = [],
  activeTab,
  onTabSelect,
  onSignOut,
}: {
  user?: CurrentUser | null;
  tabs?: AdminTabItem[];
  activeTab?: AdminTab;
  onTabSelect?: (tab: AdminTab) => void;
  onSignOut?: () => void;
}) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [openSection, setOpenSection] = useState<string>("core");

  return (
    <>
      <header className="admin-header">
        <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
          {tabs.length > 0 && (
            <button
              aria-label={drawerOpen ? "Close administration menu" : "Open administration menu"}
              className="admin-mobile-nav-toggle"
              onClick={() => setDrawerOpen(!drawerOpen)}
              type="button"
            >
              <svg fill="none" height="24" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" width="24">
                <path d="M4 6h16M4 12h16M4 18h16" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </button>
          )}
          <Brand href="/" />
        </div>
        {tabs.length > 0 && (
          <nav aria-label="Administration sections" className="admin-portal-tabs">
            {tabs.map((tab) => (
              <button
                aria-current={activeTab === tab.id ? "page" : undefined}
                className={activeTab === tab.id ? "admin-portal-tab is-active" : "admin-portal-tab"}
                key={tab.id}
                onClick={() => onTabSelect?.(tab.id)}
                type="button"
              >
                {tab.label}
              </button>
            ))}
          </nav>
        )}
        {user && onSignOut && (
          <div className="admin-header-actions">
            <ThemeToggle />
            <span style={{ fontSize: "0.88rem" }}>{user.fullName}</span>
            <button className="button button--secondary" onClick={onSignOut} style={{ minHeight: "44px" }} type="button">Sign out</button>
          </div>
        )}
        {!user && (
          <div className="admin-header-actions">
            <ThemeToggle />
          </div>
        )}
      </header>

      {/* Responsive Hamburger Mobile Drawer */}
      <div
        aria-hidden={!drawerOpen}
        className={`admin-mobile-drawer-overlay ${drawerOpen ? "is-open" : ""}`}
        onClick={() => setDrawerOpen(false)}
      />
      <aside
        aria-label="Admin Navigation Drawer"
        aria-modal="true"
        className={`admin-mobile-drawer ${drawerOpen ? "is-open" : ""}`}
        role="dialog"
      >
        <div className="admin-mobile-drawer-header">
          <Brand href="/" />
          <button
            aria-label="Close navigation drawer"
            className="admin-mobile-drawer-close"
            onClick={() => setDrawerOpen(false)}
            type="button"
          >
            <svg fill="none" height="24" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" width="24">
              <path d="M6 18L18 6M6 6l12 12" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
        </div>

        <div className="admin-mobile-drawer-content">
          <div>
            <button
              className="admin-drawer-accordion-btn"
              onClick={() => setOpenSection(openSection === "core" ? "" : "core")}
              type="button"
            >
              <span>Governance & Overview</span>
              <span>{openSection === "core" ? "▲" : "▼"}</span>
            </button>
            {openSection === "core" && (
              <div className="admin-drawer-items">
                {tabs.filter(t => ["dashboard", "identity", "approvals", "finance", "reports", "config"].includes(t.id)).map(tab => (
                  <button
                    aria-current={activeTab === tab.id ? "page" : undefined}
                    className={`admin-drawer-tab-link ${activeTab === tab.id ? "is-active" : ""}`}
                    key={tab.id}
                    onClick={() => {
                      onTabSelect?.(tab.id);
                      setDrawerOpen(false);
                    }}
                    type="button"
                  >
                    {tab.label}
                  </button>
                ))}
              </div>
            )}
          </div>

          <div>
            <button
              className="admin-drawer-accordion-btn"
              onClick={() => setOpenSection(openSection === "inventory" ? "" : "inventory")}
              type="button"
            >
              <span>Railway & Reservations</span>
              <span>{openSection === "inventory" ? "▲" : "▼"}</span>
            </button>
            {openSection === "inventory" && (
              <div className="admin-drawer-items">
                {tabs.filter(t => ["master-data", "fares", "inventory", "reservations", "quotas", "tickets", "cancellations"].includes(t.id)).map(tab => (
                  <button
                    aria-current={activeTab === tab.id ? "page" : undefined}
                    className={`admin-drawer-tab-link ${activeTab === tab.id ? "is-active" : ""}`}
                    key={tab.id}
                    onClick={() => {
                      onTabSelect?.(tab.id);
                      setDrawerOpen(false);
                    }}
                    type="button"
                  >
                    {tab.label}
                  </button>
                ))}
              </div>
            )}
          </div>

          <div>
            <button
              className="admin-drawer-accordion-btn"
              onClick={() => setOpenSection(openSection === "support" ? "" : "support")}
              type="button"
            >
              <span>Support & Payments</span>
              <span>{openSection === "support" ? "▲" : "▼"}</span>
            </button>
            {openSection === "support" && (
              <div className="admin-drawer-items">
                {tabs.filter(t => ["payments", "notifications", "support"].includes(t.id)).map(tab => (
                  <button
                    aria-current={activeTab === tab.id ? "page" : undefined}
                    className={`admin-drawer-tab-link ${activeTab === tab.id ? "is-active" : ""}`}
                    key={tab.id}
                    onClick={() => {
                      onTabSelect?.(tab.id);
                      setDrawerOpen(false);
                    }}
                    type="button"
                  >
                    {tab.label}
                  </button>
                ))}
              </div>
            )}
          </div>

          <div style={{ marginTop: "auto", paddingTop: "20px", borderTop: "1px solid var(--color-border)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "14px" }}>
              <span style={{ fontSize: "0.85rem", color: "var(--color-text-muted)" }}>Interface Theme</span>
              <ThemeToggle />
            </div>
            {user && onSignOut && (
              <button
                className="button button--secondary"
                onClick={() => {
                  setDrawerOpen(false);
                  onSignOut();
                }}
                style={{ width: "100%", minHeight: "44px" }}
                type="button"
              >
                Sign out ({user.fullName})
              </button>
            )}
          </div>
        </div>
      </aside>
    </>
  );
}

const staffRoles = [
  "RAILWAY_ADMIN",
  "STATION_MANAGER",
  "RESERVATION_OFFICER",
  "FINANCE_ADMIN",
  "CUSTOMER_SUPPORT",
  "OPERATIONS_MANAGER",
  "SUPER_ADMIN",
];

export function AdminIdentityConsole() {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [audit, setAudit] = useState<AuditEntry[]>([]);
  const [showAudit, setShowAudit] = useState(false);
  const [editingUser, setEditingUser] = useState<AdminUser | null>(null);
  const [activityUser, setActivityUser] = useState<AdminUser | null>(null);
  const [authEvents, setAuthEvents] = useState<AuthEvent[]>([]);
  const [activeSessions, setActiveSessions] = useState<AdminSession[]>([]);
  const [selectedRoles, setSelectedRoles] = useState<string[]>([]);
  const [stationScope, setStationScope] = useState("");
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState<Page<AdminUser> | null>(null);
  const [activeTab, setActiveTab] = useState<AdminTab>("identity");

  const loadUsers = useCallback(async () => {
    const query = new URLSearchParams({ page: "0", size: "50" });
    if (search.trim()) query.set("search", search.trim());
    if (roleFilter) query.set("role", roleFilter);
    if (statusFilter) query.set("status", statusFilter);
    const response = await apiFetch<Page<AdminUser>>(`/api/v1/admin/users?${query}`);
    setPage(response.data);
    setUsers(response.data?.items ?? []);
  }, [search, roleFilter, statusFilter]);

  const loadConsole = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const current = await apiFetch<CurrentUser>("/api/v1/auth/me");
      setUser(current.data);
      const currentRoles = current.data?.roles ?? [];
      setActiveTab(
        currentRoles.includes("SUPER_ADMIN")
          ? "identity"
          : currentRoles.includes("RAILWAY_ADMIN")
            ? "master-data"
            : currentRoles.includes("FINANCE_ADMIN")
              ? "fares"
              : "identity",
      );
      if (!current.data?.mustChangePassword && currentRoles.includes("SUPER_ADMIN")) {
        const [userPage, roleResponse, permissionResponse] = await Promise.all([
          apiFetch<Page<AdminUser>>("/api/v1/admin/users?page=0&size=50"),
          apiFetch<Role[]>("/api/v1/admin/roles"),
          apiFetch<Permission[]>("/api/v1/admin/permissions"),
        ]);
        setPage(userPage.data);
        setUsers(userPage.data?.items ?? []);
        setRoles(roleResponse.data ?? []);
        setPermissions(permissionResponse.data ?? []);
      } else {
        setPage(null);
        setUsers([]);
        setRoles([]);
        setPermissions([]);
      }
    } catch (caught) {
      setError(
        caught instanceof ApiRequestError
          ? caught.message
          : "The administration console could not be loaded.",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadConsole();
  }, [loadConsole]);

  async function signIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setNotice("");
    const form = new FormData(event.currentTarget);
    try {
      await apiFetch<CurrentUser>("/api/v1/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          identifier: form.get("identifier"),
          password: form.get("password"),
        }),
      });
      await loadConsole();
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "Sign-in failed.");
    }
  }

  async function changeInitialPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const form = new FormData(event.currentTarget);
    try {
      await apiFetch("/api/v1/auth/password/change", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          currentPassword: form.get("currentPassword"),
          newPassword: form.get("newPassword"),
        }),
      });
      setUser(null);
      setNotice("Password changed. Your session was revoked. Sign in again.");
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "Password change failed.");
    }
  }

  async function signOut() {
    try {
      await apiFetch("/api/v1/auth/logout", { method: "POST" });
      setUser(null);
      setUsers([]);
      setPage(null);
      setActiveTab("identity");
      setError("");
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "Sign out could not be completed.");
    }
  }

  async function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    try {
      await loadUsers();
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "The user list could not be loaded.");
    }
  }

  async function createStaff(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setNotice("");
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const selected = form.getAll("roles").map(String);
    const grants = selected.map((roleCode) => ({
      roleCode,
      scopeType: roleCode === "STATION_MANAGER" ? "STATION" : "GLOBAL",
      scopeId: roleCode === "STATION_MANAGER" ? String(form.get("stationScope")) : "*",
    }));
    try {
      await apiFetch<AdminUser>("/api/v1/admin/users", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          fullName: form.get("fullName"),
          email: form.get("email"),
          mobileNumber: form.get("mobileNumber"),
          temporaryPassword: form.get("temporaryPassword"),
          roles: grants,
        }),
      });
      formElement.reset();
      setNotice("Staff account created. Give the temporary password to the user out of band.");
      await loadUsers();
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "The staff account could not be created.");
    }
  }

  async function changeStatus(target: AdminUser) {
    setError("");
    try {
      const status = target.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
      await apiFetch(`/api/v1/admin/users/${target.userId}/status`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ status }),
      });
      await loadUsers();
      setNotice(`Account status changed to ${status}.`);
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "The account status could not be changed.");
    }
  }

  function beginRoleEdit(target: AdminUser) {
    setEditingUser(target);
    setSelectedRoles(target.roles.map((assignment) => assignment.roleCode));
    setStationScope(
      target.roles.find((assignment) => assignment.roleCode === "STATION_MANAGER")?.scopeId ?? "",
    );
    setError("");
  }

  async function saveRoles(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editingUser) return;
    setError("");
    const grants = selectedRoles.map((roleCode) => ({
      roleCode,
      scopeType: roleCode === "STATION_MANAGER" ? "STATION" : "GLOBAL",
      scopeId: roleCode === "STATION_MANAGER" ? stationScope.trim() : "*",
    }));
    try {
      await apiFetch(`/api/v1/admin/users/${editingUser.userId}/roles`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ roles: grants }),
      });
      setEditingUser(null);
      await loadUsers();
      setNotice("Role assignments were updated and the user's sessions were revoked.");
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "The role assignments could not be saved.");
    }
  }

  async function loadAudit() {
    setError("");
    try {
      const response = await apiFetch<Page<AuditEntry>>("/api/v1/admin/security/audit-logs?page=0&size=50");
      setAudit(response.data?.items ?? []);
      setShowAudit(true);
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "Audit logs could not be loaded.");
    }
  }

  async function openActivity(target: AdminUser) {
    setError("");
    try {
      const [events, sessions] = await Promise.all([
        apiFetch<Page<AuthEvent>>(`/api/v1/admin/users/${target.userId}/authentication-events?page=0&size=50`),
        apiFetch<AdminSession[]>(`/api/v1/admin/users/${target.userId}/sessions`),
      ]);
      setActivityUser(target);
      setAuthEvents(events.data?.items ?? []);
      setActiveSessions(sessions.data ?? []);
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "Account activity could not be loaded.");
    }
  }

  async function revokeAdminSession(sessionId: string) {
    if (!activityUser) return;
    setError("");
    try {
      await apiFetch(
        `/api/v1/admin/users/${activityUser.userId}/sessions/${encodeURIComponent(sessionId)}`,
        { method: "DELETE" },
      );
      setActiveSessions((sessions) => sessions.filter((session) => session.sessionId !== sessionId));
      setNotice("The selected browser session was revoked.");
    } catch (caught) {
      setError(caught instanceof ApiRequestError ? caught.message : "The session could not be revoked.");
    }
  }

  if (loading) {
    return (
      <>
        <AdminPortalHeader />
        <main className="admin-main" role="status">Loading administration portal…</main>
      </>
    );
  }

  if (!user) {
    return (
      <>
        <AdminPortalHeader />
        <main className="admin-main admin-auth-main">
          <section className="admin-auth-card">
            <p className="eyebrow">Restricted access</p>
            <h1>Administrator sign in</h1>
            <p>Only provisioned staff accounts with server-side permissions can open this portal.</p>
            {error && <p className="admin-alert admin-alert--error" role="alert">{error}</p>}
            {notice && <p className="admin-alert admin-alert--success" role="status">{notice}</p>}
            <form className="admin-form" onSubmit={signIn}>
              <label>
                Email address
                <input autoComplete="email" name="identifier" required type="email" />
              </label>
              <label>
                Password
                <input autoComplete="current-password" name="password" required type="password" />
              </label>
              <button className="button button--primary" type="submit">Sign in</button>
            </form>
          </section>
        </main>
      </>
    );
  }

  if (user.mustChangePassword) {
    return (
      <>
        <AdminPortalHeader user={user} onSignOut={() => void signOut()} />
        <main className="admin-main admin-auth-main">
          <section className="admin-auth-card">
            <p className="eyebrow">Required security step</p>
            <h1>Change your temporary password</h1>
            <p>Administrator features remain locked until you set a personal password.</p>
            {error && <p className="admin-alert admin-alert--error" role="alert">{error}</p>}
            <form className="admin-form" onSubmit={changeInitialPassword}>
              <label>Temporary password<input autoComplete="current-password" name="currentPassword" required type="password" /></label>
              <label>New password<input autoComplete="new-password" minLength={12} name="newPassword" required type="password" /></label>
              <button className="button button--primary" type="submit">Change password</button>
            </form>
          </section>
        </main>
      </>
    );
  }

  const isSuperAdmin = user.roles.includes("SUPER_ADMIN");
  const isRailwayAdmin = user.roles.includes("RAILWAY_ADMIN");
  const isFinanceAdmin = user.roles.includes("FINANCE_ADMIN");
  const isReservationOfficer = user.roles.includes("RESERVATION_OFFICER");
  const isCustomerSupport = user.roles.includes("CUSTOMER_SUPPORT");
  const tabs: AdminTabItem[] = isSuperAdmin
    ? [
      { id: "dashboard", label: "Executive Dashboard" },
      { id: "identity", label: "Identity & Staff" },
      { id: "approvals", label: "Approvals (Maker-Checker)" },
      { id: "finance", label: "Finance & Reconciliation" },
      { id: "reports", label: "Reports & Analytics" },
      { id: "master-data", label: "Railway Master Data" },
      { id: "fares", label: "Fare Management" },
      { id: "inventory", label: "Seat Inventory" },
      { id: "reservations", label: "Reservations" },
      { id: "quotas", label: "Quotas & Queues" },
      { id: "payments", label: "Payments" },
      { id: "tickets", label: "Ticket Management" },
      { id: "cancellations", label: "Cancellations" },
      { id: "notifications", label: "Notifications" },
      { id: "support", label: "Customer Support" },
      { id: "config", label: "System Config" },
    ]
    : isRailwayAdmin
      ? [
        { id: "dashboard", label: "Executive Dashboard" },
        { id: "approvals", label: "Approvals (Maker-Checker)" },
        { id: "reports", label: "Reports & Analytics" },
        { id: "master-data", label: "Railway Master Data" },
        { id: "fares", label: "Fare Management" },
        { id: "inventory", label: "Seat Inventory" },
        { id: "reservations", label: "Reservations" },
        { id: "quotas", label: "Quotas & Queues" },
        { id: "tickets", label: "Ticket Management" },
        { id: "cancellations", label: "Cancellations" },
        { id: "notifications", label: "Notifications" },
        { id: "support", label: "Customer Support" },
      ]
      : isCustomerSupport
        ? [
          { id: "support", label: "Customer Support" },
        ]
      : isReservationOfficer
        ? [
          { id: "reservations", label: "Reservations" },
          { id: "tickets", label: "Ticket management" },
          { id: "cancellations", label: "Cancellations" },
        ]
      : isFinanceAdmin
        ? [
          { id: "dashboard", label: "Executive Dashboard" },
          { id: "finance", label: "Finance & Reconciliation" },
          { id: "reports", label: "Reports & Analytics" },
          { id: "fares", label: "Fare Management" },
          { id: "payments", label: "Payments" },
          { id: "cancellations", label: "Cancellations" },
        ]
        : [];
  const currentTab = tabs.some((tab) => tab.id === activeTab) ? activeTab : tabs[0]?.id ?? activeTab;
  return (
    <>
      <AdminPortalHeader
        activeTab={currentTab}
        onSignOut={() => void signOut()}
        onTabSelect={setActiveTab}
        tabs={tabs}
        user={user}
      />
      <main className="admin-main">
        {error && <p className="admin-alert admin-alert--error" role="alert">{error}</p>}
        {notice && <p className="admin-alert admin-alert--success" role="status">{notice}</p>}
        {tabs.length === 0 && (
          <p className="admin-alert admin-alert--warning" role="status">
            Your account is authenticated but is not authorized to use this administration portal.
          </p>
        )}
        {(isSuperAdmin || isRailwayAdmin || isFinanceAdmin) && (
          <div className={currentTab === "dashboard" ? undefined : "admin-tab-content-hidden"}>
            <AdminExecutiveDashboard />
          </div>
        )}
        {(isSuperAdmin || isRailwayAdmin) && (
          <div className={currentTab === "approvals" ? undefined : "admin-tab-content-hidden"}>
            <AdminApprovalConsole />
          </div>
        )}
        {(isSuperAdmin || isFinanceAdmin) && (
          <div className={currentTab === "finance" ? undefined : "admin-tab-content-hidden"}>
            <AdminFinanceConsole />
          </div>
        )}
        {(isSuperAdmin || isRailwayAdmin || isFinanceAdmin) && (
          <div className={currentTab === "reports" ? undefined : "admin-tab-content-hidden"}>
            <AdminReportsConsole />
          </div>
        )}
        <div className={currentTab === "identity" && isSuperAdmin ? undefined : "admin-tab-content-hidden"}>
          {isSuperAdmin && (
            <>
              <div className="admin-title-row">
                <div>
                  <p className="eyebrow">Identity and access</p>
                  <h1>Administrator console</h1>
                  <p>Signed in as {user.fullName}. Changes to staff access require a recent sign-in.</p>
                </div>
              </div>
            <section aria-labelledby="create-staff-title" className="admin-panel">
              <h2 id="create-staff-title">Provision staff account</h2>
              <p>Never use passenger registration for staff. Share the temporary password out of band.</p>
              <form className="admin-staff-form" onSubmit={createStaff}>
                <label>Full name<input maxLength={120} minLength={2} name="fullName" required /></label>
                <label>Email<input maxLength={254} name="email" required type="email" /></label>
                <label>Mobile with country code<input name="mobileNumber" placeholder="+14155552671" required type="tel" /></label>
                <label>Temporary password<input minLength={12} name="temporaryPassword" required type="password" /></label>
                <fieldset>
                  <legend>Assign roles</legend>
                  <div className="admin-role-options">
                    {staffRoles.map((roleCode) => (
                      <label key={roleCode}>
                        <input name="roles" type="checkbox" value={roleCode} />
                        {roleCode.replaceAll("_", " ")}
                      </label>
                    ))}
                  </div>
                </fieldset>
                <label>Station scope (required for Station Manager)<input maxLength={100} name="stationScope" /></label>
                <button className="button button--primary" type="submit">Create staff account</button>
              </form>
            </section>

            <section aria-labelledby="user-directory-title" className="admin-panel">
              <div className="admin-panel-heading">
                <div><h2 id="user-directory-title">Identity accounts</h2><p>{page?.totalItems ?? 0} matching accounts</p></div>
                <button className="button button--secondary" onClick={() => void loadAudit()} type="button">Security audit log</button>
              </div>
              <form className="admin-filters" onSubmit={applyFilters}>
                <label>Search<input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Name, email, or mobile" /></label>
                <label>Role
                  <select value={roleFilter} onChange={(event) => setRoleFilter(event.target.value)}>
                    <option value="">All roles</option>
                    {roles.map((role) => <option key={role.roleCode} value={role.roleCode}>{role.displayName}</option>)}
                  </select>
                </label>
                <label>Status
                  <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
                    <option value="">All statuses</option>
                    <option value="ACTIVE">Active</option>
                    <option value="SUSPENDED">Suspended</option>
                    <option value="DISABLED">Disabled</option>
                  </select>
                </label>
                <button className="button button--primary" type="submit">Filter</button>
              </form>
              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead><tr><th>Account</th><th>Status</th><th>Roles and scope</th><th>Verification</th><th>Actions</th></tr></thead>
                  <tbody>
                    {users.map((target) => (
                      <tr key={target.userId}>
                        <td><strong>{target.fullName}</strong><span>{target.email}</span><span>{target.mobileNumber}</span></td>
                        <td><span className={`admin-status admin-status--${target.status.toLowerCase()}`}>{target.status}</span></td>
                        <td>{target.roles.map((role) => `${role.roleCode} (${role.scopeType}${role.scopeType === "GLOBAL" ? "" : `: ${role.scopeId}`})`).join(", ") || "No roles"}</td>
                        <td>Email {target.emailVerified ? "verified" : "unverified"}<br />Mobile {target.mobileVerified ? "verified" : "unverified"}</td>
                        <td>
                          <div className="admin-row-actions">
                            <button className="button button--secondary" onClick={() => beginRoleEdit(target)} type="button">Roles</button>
                            <button className="button button--secondary" onClick={() => void openActivity(target)} type="button">Activity</button>
                            <button className="button button--secondary" onClick={() => void changeStatus(target)} type="button">
                              {target.status === "ACTIVE" ? "Suspend" : "Reactivate"}
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                    {users.length === 0 && <tr><td colSpan={5}>No accounts match these filters.</td></tr>}
                  </tbody>
                </table>
              </div>
            </section>

            <section aria-labelledby="role-matrix-title" className="admin-panel">
              <h2 id="role-matrix-title">Role and permission catalogue</h2>
              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead><tr><th>Role</th><th>Granted permissions</th></tr></thead>
                  <tbody>{roles.map((role) => (
                    <tr key={role.roleCode}>
                      <td><strong>{role.displayName}</strong></td>
                      <td>{role.permissions.join(", ") || "No permissions"}</td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
              <details className="admin-permission-details">
                <summary>View permission descriptions</summary>
                <ul>{permissions.map((permission) => (
                  <li key={permission.permissionCode}>
                    <code>{permission.permissionCode}</code> — {permission.description}
                  </li>
                ))}</ul>
              </details>
            </section>

            {editingUser && (
              <div className="admin-modal-backdrop" role="presentation">
                <section aria-labelledby="role-editor-title" aria-modal="true" className="admin-role-dialog" role="dialog">
                  <h2 id="role-editor-title">Manage roles for {editingUser.fullName}</h2>
                  <form className="admin-form" onSubmit={saveRoles}>
                    <div className="admin-role-options">
                      {staffRoles.map((roleCode) => (
                        <label key={roleCode}>
                          <input
                            checked={selectedRoles.includes(roleCode)}
                            onChange={(event) => setSelectedRoles((current) =>
                              event.target.checked
                                ? [...current, roleCode]
                                : current.filter((value) => value !== roleCode))}
                            type="checkbox"
                          />
                          {roleCode.replaceAll("_", " ")}
                        </label>
                      ))}
                    </div>
                    {selectedRoles.includes("STATION_MANAGER") && (
                      <label>Station scope identifier<input maxLength={100} onChange={(event) => setStationScope(event.target.value)} required value={stationScope} /></label>
                    )}
                    <p className="identity-note">Role changes revoke the account’s existing sessions. The last active Super Administrator is protected.</p>
                    <div className="admin-row-actions">
                      <button className="button button--primary" type="submit">Save role assignments</button>
                      <button className="button button--secondary" onClick={() => setEditingUser(null)} type="button">Cancel</button>
                    </div>
                  </form>
                </section>
              </div>
            )}

            {showAudit && (
              <section aria-labelledby="audit-title" className="admin-panel">
                <div className="admin-panel-heading">
                  <h2 id="audit-title">Recent security audit events</h2>
                  <button className="button button--secondary" onClick={() => setShowAudit(false)} type="button">Close</button>
                </div>
                <div className="admin-table-wrap">
                  <table className="admin-table">
                    <thead><tr><th>Time</th><th>Event</th><th>Outcome</th><th>Actor</th><th>Target</th><th>Correlation</th></tr></thead>
                    <tbody>{audit.map((entry) => (
                      <tr key={entry.auditId}>
                        <td>{new Date(entry.occurredAt).toLocaleString()}</td>
                        <td>{entry.eventType}</td><td>{entry.outcome}</td>
                        <td>{entry.actorName ?? (entry.actorUserId ? "Unknown user" : "System")}</td>
                        <td>{entry.targetName ?? "—"}</td>
                        <td title={`Correlation ID: ${entry.correlationId}`}>
                          Security event · {new Date(entry.occurredAt).toLocaleString()}
                        </td>
                      </tr>
                    ))}</tbody>
                  </table>
                </div>
              </section>
            )}

            {activityUser && (
              <section aria-labelledby="activity-title" className="admin-panel">
                <div className="admin-panel-heading">
                  <div><h2 id="activity-title">Account activity: {activityUser.fullName}</h2><p>Session references are opaque identifiers, never browser credentials.</p></div>
                  <button className="button button--secondary" onClick={() => setActivityUser(null)} type="button">Close</button>
                </div>
                <h3>Active browser sessions</h3>
                <div className="admin-table-wrap">
                  <table className="admin-table">
                    <thead><tr><th>Last active</th><th>Browser</th><th>Action</th></tr></thead>
                    <tbody>{activeSessions.map((session) => (
                      <tr key={session.sessionId}>
                        <td>{new Date(session.lastSeenAt).toLocaleString()}</td>
                        <td>{session.userAgent || "Browser details unavailable"}</td>
                        <td><button className="button button--secondary" onClick={() => void revokeAdminSession(session.sessionId)} type="button">Revoke</button></td>
                      </tr>
                    ))}</tbody>
                  </table>
                </div>
                <h3>Authentication events</h3>
                <div className="admin-table-wrap">
                  <table className="admin-table">
                    <thead><tr><th>Time</th><th>Event</th><th>Outcome</th><th>Correlation</th></tr></thead>
                    <tbody>{authEvents.map((entry) => (
                      <tr key={entry.eventId}>
                        <td>{new Date(entry.occurredAt).toLocaleString()}</td>
                        <td>{entry.eventType}</td><td>{entry.outcome}</td><td>{entry.correlationId}</td>
                      </tr>
                    ))}{authEvents.length === 0 && <tr><td colSpan={4}>No authentication events found.</td></tr>}</tbody>
                  </table>
                </div>
              </section>
            )}
            </>
          )}
        </div>
        {(isSuperAdmin || isRailwayAdmin) && (
          <div className={currentTab === "master-data" ? undefined : "admin-tab-content-hidden"}>
            <AdminMasterDataConsole />
          </div>
        )}
        {(isSuperAdmin || isFinanceAdmin || isRailwayAdmin) && (
          <div className={currentTab === "fares" ? undefined : "admin-tab-content-hidden"}>
            <AdminFareConsole roles={user.roles} />
          </div>
        )}
        {(isSuperAdmin || isRailwayAdmin) && (
          <div className={currentTab === "inventory" ? undefined : "admin-tab-content-hidden"}>
            <AdminInventoryConsole />
          </div>
        )}
        {(isSuperAdmin || isRailwayAdmin || isReservationOfficer) && (
          <div className={currentTab === "reservations" ? undefined : "admin-tab-content-hidden"}>
            <AdminReservationConsole roles={user.roles} />
          </div>
        )}
        {(isSuperAdmin || isRailwayAdmin) && (
          <div className={currentTab === "quotas" ? undefined : "admin-tab-content-hidden"}>
            <AdminQuotaConsole />
          </div>
        )}
        {(isSuperAdmin || isFinanceAdmin) && (
          <div className={currentTab === "payments" ? undefined : "admin-tab-content-hidden"}>
            <AdminPaymentConsole />
          </div>
        )}
        {(isSuperAdmin || isRailwayAdmin || isReservationOfficer) && (
          <div className={currentTab === "tickets" ? undefined : "admin-tab-content-hidden"}>
            <TicketAdminConsole />
          </div>
        )}
        {(isSuperAdmin || isFinanceAdmin || isRailwayAdmin || isReservationOfficer) && (
          <div className={currentTab === "cancellations" ? undefined : "admin-tab-content-hidden"}>
            <AdminCancellationConsole />
          </div>
        )}
        {(isSuperAdmin || isRailwayAdmin) && (
          <div className={currentTab === "notifications" ? undefined : "admin-tab-content-hidden"}>
            <AdminNotificationConsole />
          </div>
        )}
        {(isSuperAdmin || isRailwayAdmin || isCustomerSupport) && (
          <div className={currentTab === "support" ? undefined : "admin-tab-content-hidden"}>
            <SupportStaffConsole />
          </div>
        )}
        {isSuperAdmin && (
          <div className={currentTab === "config" ? undefined : "admin-tab-content-hidden"}>
            <AdminSystemConfigConsole />
          </div>
        )}
      </main>
    </>
  );
}
