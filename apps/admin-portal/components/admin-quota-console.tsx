"use client";

import { useEffect, useState } from "react";
import { ApiRequestError, apiFetch } from "@railconnect/api";

type QuotaView = {
  quotaId: string;
  quotaCode: string;
  displayName: string;
  description: string;
  active: boolean;
};

type QuotaAllocationView = {
  allocationId: string;
  trainRunId: string;
  coachClassCode: string;
  quotaCode: string;
  allocatedBerths: number;
  racCapacity: number;
  waitlistCapacity: number;
  confirmedCount: number;
  racCount: number;
  waitlistCount: number;
};

type QueueView = {
  queueId: string;
  coachClassCode: string;
  quotaCode: string;
  queueType: string;
  currentHighestSequence: number;
  waitingCount: number;
};

type PromotionResult = {
  promotionId: string;
  bookingPassengerId: string;
  fromStatus: string;
  toStatus: string;
};

export function AdminQuotaConsole() {
  const [quotas, setQuotas] = useState<QuotaView[]>([]);
  const [trainRunId, setTrainRunId] = useState("");
  const [allocations, setAllocations] = useState<QuotaAllocationView[]>([]);
  const [queues, setQueues] = useState<QueueView[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    async function loadQuotas() {
      try {
        const res = await apiFetch<QuotaView[]>("/api/v1/admin/quotas");
        setQuotas(res.data ?? []);
      } catch (err) {
        setError(err instanceof ApiRequestError ? err.message : "Failed to load quotas");
      }
    }
    void loadQuotas();
  }, []);

  async function fetchRunData(runId: string) {
    if (!runId.trim()) return;
    setError("");
    setNotice("");
    setLoading(true);
    try {
      const [allocRes, queueRes] = await Promise.all([
        apiFetch<QuotaAllocationView[]>(`/api/v1/admin/train-runs/${runId.trim()}/quota-allocations`),
        apiFetch<QueueView[]>(`/api/v1/admin/train-runs/${runId.trim()}/queues`)
      ]);
      setAllocations(allocRes.data ?? []);
      setQueues(queueRes.data ?? []);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Failed to load train run allocations");
    } finally {
      setLoading(false);
    }
  }

  async function searchRun(e: React.FormEvent) {
    e.preventDefault();
    await fetchRunData(trainRunId);
  }

  async function triggerPromotion() {
    if (!trainRunId.trim()) return;
    setError("");
    setNotice("");
    setLoading(true);
    try {
      const res = await apiFetch<PromotionResult[]>(`/api/v1/admin/train-runs/${trainRunId.trim()}/process-promotions`, {
        method: "POST"
      });
      setNotice(`Promotion engine processed ${res.data?.length ?? 0} passenger transitions.`);
      await fetchRunData(trainRunId);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Promotion run failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="admin-panel" aria-labelledby="quota-admin-title">
      <div className="admin-title-row">
        <div>
          <p className="eyebrow">Capacity &amp; Queuing</p>
          <h2 id="quota-admin-title">Quota, RAC &amp; Waitlist Administration</h2>
        </div>
      </div>

      {error && <p className="admin-alert admin-alert--error" role="alert">{error}</p>}
      {notice && <p className="admin-alert admin-alert--success" role="status">{notice}</p>}

      <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: "1.5rem", marginTop: "1rem" }}>
        <div>
          <h3>System Quotas</h3>
          <p style={{ fontSize: "0.85rem", color: "#64748b" }}>Active reservation quota types and eligibility categories.</p>
          <ul style={{ listStyle: "none", padding: 0, margin: 0, display: "grid", gap: "0.5rem" }}>
            {quotas.map((q) => (
              <li key={q.quotaId} style={{ padding: "0.75rem", background: "#f8fafc", borderRadius: "6px", border: "1px solid #e2e8f0" }}>
                <strong>{q.displayName}</strong> ({q.quotaCode})
                <p style={{ margin: "0.25rem 0 0 0", fontSize: "0.8rem", color: "#64748b" }}>{q.description}</p>
              </li>
            ))}
          </ul>
        </div>

        <div>
          <h3>Train Run Queue &amp; Quota Telemetry</h3>
          <form onSubmit={searchRun} style={{ display: "flex", gap: "0.5rem", marginBottom: "1rem" }}>
            <input
              placeholder="Enter Train Run UUID"
              value={trainRunId}
              onChange={(e) => setTrainRunId(e.target.value)}
              style={{ flex: 1, padding: "0.5rem 0.75rem", borderRadius: "6px", border: "1px solid #cbd5e1" }}
              required
            />
            <button className="button button--primary" type="submit" disabled={loading}>Inspect Run</button>
            <button className="button button--secondary" type="button" onClick={triggerPromotion} disabled={loading || !trainRunId.trim()}>
              Trigger Promotions
            </button>
          </form>

          {allocations.length > 0 && (
            <div style={{ marginBottom: "1.5rem" }}>
              <h4>Class Quota Allocations</h4>
              <table style={{ width: "100%", fontSize: "0.85rem", borderCollapse: "collapse" }}>
                <thead>
                  <tr style={{ background: "#f1f5f9", textAlign: "left" }}>
                    <th style={{ padding: "0.5rem" }}>Class</th>
                    <th style={{ padding: "0.5rem" }}>Quota</th>
                    <th style={{ padding: "0.5rem" }}>Berths</th>
                    <th style={{ padding: "0.5rem" }}>RAC Cap</th>
                    <th style={{ padding: "0.5rem" }}>WL Cap</th>
                    <th style={{ padding: "0.5rem" }}>Confirmed / RAC / WL</th>
                  </tr>
                </thead>
                <tbody>
                  {allocations.map((a) => (
                    <tr key={a.allocationId} style={{ borderBottom: "1px solid #e2e8f0" }}>
                      <td style={{ padding: "0.5rem" }}><strong>{a.coachClassCode}</strong></td>
                      <td style={{ padding: "0.5rem" }}>{a.quotaCode}</td>
                      <td style={{ padding: "0.5rem" }}>{a.allocatedBerths}</td>
                      <td style={{ padding: "0.5rem" }}>{a.racCapacity}</td>
                      <td style={{ padding: "0.5rem" }}>{a.waitlistCapacity}</td>
                      <td style={{ padding: "0.5rem" }}>{a.confirmedCount} / {a.racCount} / {a.waitlistCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {queues.length > 0 && (
            <div>
              <h4>Active Queues (RAC &amp; WL)</h4>
              <table style={{ width: "100%", fontSize: "0.85rem", borderCollapse: "collapse" }}>
                <thead>
                  <tr style={{ background: "#f1f5f9", textAlign: "left" }}>
                    <th style={{ padding: "0.5rem" }}>Class</th>
                    <th style={{ padding: "0.5rem" }}>Type</th>
                    <th style={{ padding: "0.5rem" }}>Quota</th>
                    <th style={{ padding: "0.5rem" }}>Max Sequence</th>
                    <th style={{ padding: "0.5rem" }}>Active Waiting</th>
                  </tr>
                </thead>
                <tbody>
                  {queues.map((q) => (
                    <tr key={q.queueId} style={{ borderBottom: "1px solid #e2e8f0" }}>
                      <td style={{ padding: "0.5rem" }}><strong>{q.coachClassCode}</strong></td>
                      <td style={{ padding: "0.5rem" }}>{q.queueType}</td>
                      <td style={{ padding: "0.5rem" }}>{q.quotaCode}</td>
                      <td style={{ padding: "0.5rem" }}>#{q.currentHighestSequence}</td>
                      <td style={{ padding: "0.5rem" }}>{q.waitingCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}

