"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { ApiRequestError, apiFetch } from "@railconnect/api";

type Page<T> = { items: T[]; page: number; size: number; total: number };
type ReservationSummary = {
  reservationRequestId: string;
  trainRunId: string;
  journeyDate: string;
  coachClassCode: string;
  status: string;
  passengerCount: number;
  groupSeatingRequested: boolean;
  expiresAt: string | null;
  createdAt: string;
};
type ReservationDetails = {
  reservationRequestId: string;
  status: string;
  groupSeatingResult: string;
  expiresAt: string | null;
  failureCode: string | null;
  failureReason: string | null;
  passengers: {
    displayName: string;
    coachNumber: string | null;
    berthNumber: string | null;
    preferenceResult: string | null;
  }[];
};
type AllocationCount = { status: string; count: number };
type AllocationPolicy = {
  versionNumber: number;
  status: string;
  preferredBerthWeight: number;
  sameCoachWeight: number;
  nearbyBerthWeight: number;
  createdByName: string | null;
  approvedByName: string | null;
  publishedAt: string | null;
};
type PolicyAudit = {
  reservationPolicyAuditId: string;
  versionNumber: number;
  actorName: string | null;
  eventType: string;
  details: string;
  occurredAt: string;
};
type AllocationAudit = {
  reservationAuditId: string;
  reservationRequestId: string;
  actorName: string | null;
  eventType: string;
  details: string;
  occurredAt: string;
  correlationId: string;
};

function errorText(error: unknown): string {
  return error instanceof ApiRequestError
    ? error.message
    : error instanceof Error
      ? error.message
      : "Reservation administration could not be completed.";
}

export function AdminReservationConsole({ roles }: { roles: string[] }) {
  const [requests, setRequests] = useState<ReservationSummary[]>([]);
  const [failures, setFailures] = useState<ReservationSummary[]>([]);
  const [counts, setCounts] = useState<AllocationCount[]>([]);
  const [allocationAudit, setAllocationAudit] = useState<AllocationAudit[]>([]);
  const [policies, setPolicies] = useState<AllocationPolicy[]>([]);
  const [policyAudit, setPolicyAudit] = useState<PolicyAudit[]>([]);
  const [selected, setSelected] = useState<ReservationDetails | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const canManagePolicy = roles.some((role) => ["RAILWAY_ADMIN", "SUPER_ADMIN"].includes(role));

  const refresh = useCallback(async () => {
    const [requestResponse, failureResponse, countResponse, auditResponse, policyResponse, policyAuditResponse] =
      await Promise.all([
        apiFetch<Page<ReservationSummary>>("/api/v1/admin/reservations?page=0&size=50"),
        apiFetch<Page<ReservationSummary>>("/api/v1/admin/allocations/failures?page=0&size=20"),
        apiFetch<AllocationCount[]>("/api/v1/admin/allocations/summary"),
        apiFetch<Page<AllocationAudit>>("/api/v1/admin/allocations/audit?page=0&size=50"),
        canManagePolicy
          ? apiFetch<AllocationPolicy[]>("/api/v1/admin/allocations/policies")
          : Promise.resolve(null),
        canManagePolicy
          ? apiFetch<Page<PolicyAudit>>("/api/v1/admin/allocations/policies/audit?page=0&size=20")
          : Promise.resolve(null),
      ]);
    setRequests(requestResponse.data?.items ?? []);
    setFailures(failureResponse.data?.items ?? []);
    setCounts(countResponse.data ?? []);
    setAllocationAudit(auditResponse.data?.items ?? []);
    setPolicies(policyResponse?.data ?? []);
    setPolicyAudit(policyAuditResponse?.data?.items ?? []);
  }, [canManagePolicy]);

  useEffect(() => {
    void refresh().catch((caught: unknown) => setError(errorText(caught)));
  }, [refresh]);

  async function loadRequest(requestId: string) {
    setError("");
    try {
      const response = await apiFetch<ReservationDetails>(
        `/api/v1/admin/reservations/${encodeURIComponent(requestId)}`,
      );
      setSelected(response.data);
    } catch (caught) {
      setError(errorText(caught));
    }
  }

  async function releaseRequest() {
    if (!selected) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const response = await apiFetch<ReservationDetails>(
        `/api/v1/admin/reservations/${encodeURIComponent(selected.reservationRequestId)}/release`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ reasonCode: "ADMIN_RELEASED" }),
        },
      );
      setSelected(response.data);
      setNotice("Reservation request released.");
      await refresh();
    } catch (caught) {
      setError(errorText(caught));
    } finally {
      setBusy(false);
    }
  }

  async function createPolicy(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await apiFetch<AllocationPolicy>("/api/v1/admin/allocations/policies", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          preferredBerthWeight: Number(form.get("preferredBerthWeight")),
          sameCoachWeight: Number(form.get("sameCoachWeight")),
          nearbyBerthWeight: Number(form.get("nearbyBerthWeight")),
        }),
      });
      formElement.reset();
      setNotice("Draft allocation policy created. Approval and publishing are separate steps.");
      await refresh();
    } catch (caught) {
      setError(errorText(caught));
    } finally {
      setBusy(false);
    }
  }

  async function changePolicy(version: number, action: "approve" | "publish") {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await apiFetch<AllocationPolicy>(
        `/api/v1/admin/allocations/policies/${version}/${action}`,
        { method: "POST" },
      );
      setNotice(`Allocation policy v${version} ${action === "approve" ? "approved" : "published"}.`);
      await refresh();
    } catch (caught) {
      setError(errorText(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="admin-reservation-console">
      {error && <p className="admin-alert admin-alert--error" role="alert">{error}</p>}
      {notice && <p className="admin-alert admin-alert--success" role="status">{notice}</p>}

      <section className="admin-panel" aria-labelledby="reservation-counts-title">
        <div className="admin-panel-heading">
          <div>
            <h2 id="reservation-counts-title">Allocation summary</h2>
            <p>Counts only; passenger details are available in authorized request records.</p>
          </div>
          <button className="button button--secondary" disabled={busy}
            onClick={() => void refresh().catch((caught: unknown) => setError(errorText(caught)))} type="button">
            Refresh
          </button>
        </div>
        <div className="admin-reservation-stats">
          {counts.map((item) => (
            <div className="admin-inventory-stat" key={item.status}>
              <span>{item.status.replaceAll("_", " ")}</span><strong>{item.count}</strong>
            </div>
          ))}
          {counts.length === 0 && <p>No allocation records.</p>}
        </div>
      </section>

      {selected && (
        <section className="admin-panel" aria-labelledby="reservation-detail-title">
          <div className="admin-panel-heading">
            <div>
              <h2 id="reservation-detail-title">Request details</h2>
              <p>{selected.reservationRequestId} · {selected.status.replaceAll("_", " ")}</p>
            </div>
            <button className="button button--secondary" onClick={() => setSelected(null)} type="button">Close</button>
          </div>
          {selected.failureCode && <p>{selected.failureCode}: {selected.failureReason}</p>}
          {selected.expiresAt && <p>Expires {new Date(selected.expiresAt).toLocaleString()}</p>}
          <ul>
            {selected.passengers.map((passenger, index) => (
              <li key={`${selected.reservationRequestId}-${index}`}>
                {passenger.displayName}
                {passenger.coachNumber && passenger.berthNumber
                  ? ` — ${passenger.coachNumber}, berth ${passenger.berthNumber}`
                  : ""}
                {passenger.preferenceResult ? ` · ${passenger.preferenceResult}` : ""}
              </li>
            ))}
          </ul>
          {["REQUESTED", "ALLOCATED_PENDING_BOOKING"].includes(selected.status) && (
            <button className="button button--secondary" disabled={busy}
              onClick={() => void releaseRequest()} type="button">
              Release provisional request
            </button>
          )}
          <p>Provisional allocations are not confirmed bookings or tickets.</p>
        </section>
      )}

      <section className="admin-panel" aria-labelledby="reservation-requests-title">
        <h2 id="reservation-requests-title">Recent reservation requests</h2>
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead><tr><th>Request</th><th>Run/date</th><th>Class</th><th>Passengers</th><th>Status</th><th>Action</th></tr></thead>
            <tbody>
              {requests.map((item) => (
                <tr key={item.reservationRequestId}>
                  <td>{item.reservationRequestId}</td>
                  <td>{item.trainRunId}<br />{item.journeyDate}</td>
                  <td>{item.coachClassCode}</td>
                  <td>{item.passengerCount}</td>
                  <td>{item.status.replaceAll("_", " ")}</td>
                  <td><button className="button button--secondary"
                    onClick={() => void loadRequest(item.reservationRequestId)} type="button">View</button></td>
                </tr>
              ))}
              {requests.length === 0 && <tr><td colSpan={6}>No reservation requests.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>

      <section className="admin-panel" aria-labelledby="allocation-failures-title">
        <h2 id="allocation-failures-title">Recent allocation failures</h2>
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead><tr><th>Request</th><th>Run/date</th><th>Passengers</th><th>Created</th></tr></thead>
            <tbody>
              {failures.map((item) => (
                <tr key={item.reservationRequestId}>
                  <td>{item.reservationRequestId}</td><td>{item.trainRunId}<br />{item.journeyDate}</td>
                  <td>{item.passengerCount}</td><td>{new Date(item.createdAt).toLocaleString()}</td>
                </tr>
              ))}
              {failures.length === 0 && <tr><td colSpan={4}>No allocation failures.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>

      <section className="admin-panel" aria-labelledby="allocation-audit-title">
        <h2 id="allocation-audit-title">Allocation audit events</h2>
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead><tr><th>Time</th><th>Request</th><th>Event</th><th>Actor</th><th>Details</th><th>Correlation</th></tr></thead>
            <tbody>
              {allocationAudit.map((entry) => (
                <tr key={entry.reservationAuditId}>
                  <td>{new Date(entry.occurredAt).toLocaleString()}</td>
                  <td>{entry.reservationRequestId}</td><td>{entry.eventType}</td>
                  <td>{entry.actorName ?? "System"}</td><td><code>{entry.details}</code></td>
                  <td>{entry.correlationId}</td>
                </tr>
              ))}
              {allocationAudit.length === 0 && <tr><td colSpan={6}>No allocation audit events.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>

      {canManagePolicy && (
        <section className="admin-panel" aria-labelledby="allocation-policy-title">
          <h2 id="allocation-policy-title">Allocation policy versions</h2>
          <p>Draft changes require a different authorized approver before publication.</p>
          <form className="admin-form admin-form--inventory" onSubmit={(event) => void createPolicy(event)}>
            <label>Preferred berth weight<input max="10000" min="0" name="preferredBerthWeight" required type="number" defaultValue="100" /></label>
            <label>Same-coach weight<input max="10000" min="0" name="sameCoachWeight" required type="number" defaultValue="25" /></label>
            <label>Nearby berth weight<input max="10000" min="0" name="nearbyBerthWeight" required type="number" defaultValue="1" /></label>
            <button className="button button--primary" disabled={busy} type="submit">Create draft</button>
          </form>
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead><tr><th>Version</th><th>Status</th><th>Weights</th><th>Created by</th><th>Actions</th></tr></thead>
              <tbody>
                {policies.map((policy) => (
                  <tr key={policy.versionNumber}>
                    <td>{policy.versionNumber}</td><td>{policy.status}</td>
                    <td>Preference {policy.preferredBerthWeight}; coach {policy.sameCoachWeight}; nearby {policy.nearbyBerthWeight}</td>
                    <td>{policy.createdByName ?? "System"}{policy.approvedByName ? ` / approved by ${policy.approvedByName}` : ""}</td>
                    <td>
                      {policy.status === "DRAFT" && (
                        <button className="button button--secondary" disabled={busy}
                          onClick={() => void changePolicy(policy.versionNumber, "approve")} type="button">Approve</button>
                      )}
                      {policy.status === "APPROVED" && (
                        <button className="button button--primary" disabled={busy}
                          onClick={() => void changePolicy(policy.versionNumber, "publish")} type="button">Publish</button>
                      )}
                    </td>
                  </tr>
                ))}
                {policies.length === 0 && <tr><td colSpan={5}>No allocation policy versions.</td></tr>}
              </tbody>
            </table>
          </div>
          <h3>Policy audit</h3>
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead><tr><th>Time</th><th>Version</th><th>Event</th><th>Actor</th><th>Details</th></tr></thead>
              <tbody>
                {policyAudit.map((entry) => (
                  <tr key={entry.reservationPolicyAuditId}>
                    <td>{new Date(entry.occurredAt).toLocaleString()}</td>
                    <td>{entry.versionNumber}</td><td>{entry.eventType}</td>
                    <td>{entry.actorName ?? "System"}</td><td><code>{entry.details}</code></td>
                  </tr>
                ))}
                {policyAudit.length === 0 && <tr><td colSpan={5}>No policy changes have been recorded.</td></tr>}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}
