"use client";

import { useCallback, useEffect, useState } from "react";
import { apiFetch } from "@railconnect/api";

type ApprovalRequest = {
  requestId: string;
  requestType: string;
  title: string;
  description: string;
  payloadJson: string;
  status: string;
  requestedBy: string;
  requesterName: string;
  reviewedBy: string | null;
  reviewerName: string | null;
  reviewRemarks: string | null;
  requestedAt: string;
  reviewedAt: string | null;
  appliedAt: string | null;
};

export function AdminApprovalConsole() {
  const [requests, setRequests] = useState<ApprovalRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedStatus, setSelectedStatus] = useState<string>("PENDING_APPROVAL");
  const [activeRequest, setActiveRequest] = useState<ApprovalRequest | null>(null);
  const [remarks, setRemarks] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  // New Request Form
  const [isCreating, setIsCreating] = useState(false);
  const [newType, setNewType] = useState("FARE_RULE_CHANGE");
  const [newTitle, setNewTitle] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [newPayload, setNewPayload] = useState('{\n  "parameter": "value"\n}');

  const fetchRequests = useCallback(async () => {
    setLoading(true);
    try {
      const res = await apiFetch<ApprovalRequest[]>(`/api/v1/admin/approvals?status=${selectedStatus}`);
      setRequests(res.data || []);
      if (activeRequest) {
        const refreshed = (res.data || []).find((r) => r.requestId === activeRequest.requestId);
        setActiveRequest(refreshed || null);
      }
    } catch (err: unknown) {
      setMessage({ type: "error", text: (err as Error)?.message || "Failed to load approval requests" });
    } finally {
      setLoading(false);
    }
  }, [selectedStatus, activeRequest]);

  useEffect(() => {
    fetchRequests();
  }, [fetchRequests]);

  const handleReview = async (approved: boolean) => {
    if (!activeRequest) return;
    if (!remarks.trim()) {
      alert("Please provide review remarks for the audit record.");
      return;
    }

    setSubmitting(true);
    try {
      await apiFetch(`/api/v1/admin/approvals/${activeRequest.requestId}/review`, {
        method: "POST",
        body: JSON.stringify({ approved, reviewRemarks: remarks }),
      });
      setMessage({
        type: "success",
        text: `Request successfully ${approved ? "approved" : "rejected"}.`,
      });
      setRemarks("");
      fetchRequests();
    } catch (err: unknown) {
      setMessage({ type: "error", text: (err as Error)?.message || "Review action failed" });
    } finally {
      setSubmitting(false);
    }
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await apiFetch("/api/v1/admin/approvals", {
        method: "POST",
        body: JSON.stringify({
          requestType: newType,
          title: newTitle,
          description: newDescription,
          payloadJson: newPayload,
        }),
      });
      setMessage({ type: "success", text: "New approval proposal submitted." });
      setIsCreating(false);
      setNewTitle("");
      setNewDescription("");
      setSelectedStatus("PENDING_APPROVAL");
      fetchRequests();
    } catch (err: unknown) {
      setMessage({ type: "error", text: (err as Error)?.message || "Failed to submit request" });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-200 pb-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900">Maker-Checker Governance Console</h2>
          <p className="text-sm text-slate-500">
            Four-Eye Principle: Critical changes require review and approval by an independent authorized checker. Self-approvals are strictly blocked.
          </p>
        </div>
        <button
          onClick={() => setIsCreating(!isCreating)}
          className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-indigo-700"
        >
          {isCreating ? "View Approvals" : "+ Propose New Change"}
        </button>
      </div>

      {message && (
        <div
          className={`rounded-lg p-4 text-sm ${
            message.type === "success" ? "bg-emerald-50 text-emerald-800 border border-emerald-200" : "bg-rose-50 text-rose-800 border border-rose-200"
          }`}
        >
          {message.text}
        </div>
      )}

      {isCreating ? (
        <form onSubmit={handleCreate} className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm space-y-4">
          <h3 className="font-semibold text-slate-900">Submit Change Proposal (Maker Role)</h3>
          <div>
            <label className="block text-xs font-medium text-slate-700">Proposal Category</label>
            <select
              value={newType}
              onChange={(e) => setNewType(e.target.value)}
              className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="FARE_RULE_CHANGE">Fare Rule Modification</option>
              <option value="POLICY_UPDATE">Reservation Policy Update</option>
              <option value="STAFF_PRIVILEGE_CHANGE">Staff Privilege Escalation</option>
              <option value="REFUND_OVERRIDE">High-Value Refund Approval</option>
              <option value="SYSTEM_CONFIG">System Configuration Update</option>
            </select>
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-700">Title</label>
            <input
              type="text"
              required
              value={newTitle}
              onChange={(e) => setNewTitle(e.target.value)}
              placeholder="e.g. Adjust dynamic surcharge for Tejas Express AC Chair"
              className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-700">Business Justification</label>
            <textarea
              required
              rows={3}
              value={newDescription}
              onChange={(e) => setNewDescription(e.target.value)}
              placeholder="State the operational or commercial rationale for this proposed update..."
              className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-700">Payload Configuration (JSON)</label>
            <textarea
              rows={4}
              value={newPayload}
              onChange={(e) => setNewPayload(e.target.value)}
              className="mt-1 block w-full rounded-md border border-slate-300 font-mono text-xs px-3 py-2"
            />
          </div>

          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={() => setIsCreating(false)}
              className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="rounded-lg bg-indigo-600 px-5 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-50"
            >
              {submitting ? "Submitting..." : "Submit to Approvers"}
            </button>
          </div>
        </form>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Queue List */}
          <div className="lg:col-span-1 rounded-xl border border-slate-200 bg-white p-4 shadow-sm flex flex-col h-[600px]">
            <div className="flex gap-2 border-b border-slate-200 pb-3 mb-3">
              {(["PENDING_APPROVAL", "APPROVED", "REJECTED"] as const).map((st) => (
                <button
                  key={st}
                  onClick={() => setSelectedStatus(st)}
                  className={`flex-1 rounded-md py-1.5 text-xs font-semibold ${
                    selectedStatus === st ? "bg-indigo-600 text-white" : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                  }`}
                >
                  {st.replace("_", " ")}
                </button>
              ))}
            </div>

            <div className="flex-1 overflow-y-auto space-y-2">
              {loading ? (
                <p className="text-center text-xs text-slate-500 py-6">Loading requests...</p>
              ) : requests.length === 0 ? (
                <p className="text-center text-xs text-slate-500 py-6">No requests in this status</p>
              ) : (
                requests.map((r) => (
                  <div
                    key={r.requestId}
                    onClick={() => setActiveRequest(r)}
                    className={`cursor-pointer rounded-lg border p-3 text-left transition ${
                      activeRequest?.requestId === r.requestId
                        ? "border-indigo-600 bg-indigo-50"
                        : "border-slate-200 hover:border-slate-300 bg-white"
                    }`}
                  >
                    <span className="text-[10px] font-bold uppercase tracking-wider text-indigo-700">
                      {r.requestType.replace(/_/g, " ")}
                    </span>
                    <p className="text-sm font-semibold text-slate-900 line-clamp-1">{r.title}</p>
                    <p className="text-xs text-slate-500 mt-1">Proposed by: {r.requesterName}</p>
                    <span
                      className={`inline-block mt-2 rounded px-1.5 py-0.5 text-[10px] font-semibold ${
                        r.status === "PENDING_APPROVAL"
                          ? "bg-amber-100 text-amber-800"
                          : r.status === "APPROVED"
                          ? "bg-emerald-100 text-emerald-800"
                          : "bg-rose-100 text-rose-800"
                      }`}
                    >
                      {r.status}
                    </span>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Details & Action Panel */}
          <div className="lg:col-span-2 rounded-xl border border-slate-200 bg-white p-6 shadow-sm flex flex-col h-[600px] overflow-y-auto">
            {activeRequest ? (
              <div className="space-y-6">
                <div>
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-indigo-600 uppercase tracking-wider">
                      {activeRequest.requestType}
                    </span>
                    <span
                      className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                        activeRequest.status === "PENDING_APPROVAL"
                          ? "bg-amber-100 text-amber-800"
                          : activeRequest.status === "APPROVED"
                          ? "bg-emerald-100 text-emerald-800"
                          : "bg-rose-100 text-rose-800"
                      }`}
                    >
                      {activeRequest.status}
                    </span>
                  </div>
                  <h3 className="text-lg font-bold text-slate-900 mt-1">{activeRequest.title}</h3>
                  <p className="text-xs text-slate-500 mt-1">
                    Submitted by <span className="font-semibold text-slate-700">{activeRequest.requesterName}</span> on{" "}
                    {new Date(activeRequest.requestedAt).toLocaleString()}
                  </p>
                </div>

                <div className="rounded-lg bg-slate-50 p-4 border border-slate-200">
                  <h4 className="text-xs font-bold uppercase text-slate-500 tracking-wider mb-2">Justification</h4>
                  <p className="text-sm text-slate-700 whitespace-pre-wrap">{activeRequest.description}</p>
                </div>

                <div>
                  <h4 className="text-xs font-bold uppercase text-slate-500 tracking-wider mb-2">Payload Data</h4>
                  <pre className="rounded-lg bg-slate-900 text-emerald-400 p-4 text-xs font-mono overflow-x-auto">
                    {activeRequest.payloadJson}
                  </pre>
                </div>

                {activeRequest.reviewedBy && (
                  <div className="rounded-lg bg-indigo-50 border border-indigo-200 p-4 text-xs text-indigo-900 space-y-1">
                    <p className="font-semibold">Review Completed</p>
                    <p>Reviewer: {activeRequest.reviewerName}</p>
                    <p>Remarks: {activeRequest.reviewRemarks || "None"}</p>
                    {activeRequest.reviewedAt && <p>Date: {new Date(activeRequest.reviewedAt).toLocaleString()}</p>}
                  </div>
                )}

                {activeRequest.status === "PENDING_APPROVAL" && (
                  <div className="border-t border-slate-200 pt-4 space-y-3">
                    <h4 className="text-xs font-bold uppercase text-slate-700 tracking-wider">
                      Checker Review & Action
                    </h4>
                    <textarea
                      rows={2}
                      value={remarks}
                      onChange={(e) => setRemarks(e.target.value)}
                      placeholder="Provide detailed reviewer comments or audit notes..."
                      className="block w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                    />
                    <div className="flex gap-3 justify-end">
                      <button
                        onClick={() => handleReview(false)}
                        disabled={submitting}
                        className="rounded-lg bg-rose-600 px-4 py-2 text-sm font-semibold text-white hover:bg-rose-700 disabled:opacity-50"
                      >
                        Reject Change
                      </button>
                      <button
                        onClick={() => handleReview(true)}
                        disabled={submitting}
                        className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-50"
                      >
                        Approve & Apply
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div className="flex h-full items-center justify-center text-slate-400 text-sm">
                Select an approval proposal from the queue to view details and review.
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

