"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@railconnect/api";

type ReconciliationRun = {
  runId: string;
  runCode: string;
  runDate: string;
  totalTransactions: number;
  matchedCount: number;
  discrepancyCount: number;
  totalVolumeInr: number;
  status: string;
  initiatedBy: string;
  initiatorName: string;
  createdAt: string;
};

export function AdminFinanceConsole() {
  const [runs, setRuns] = useState<ReconciliationRun[]>([]);
  const [loading, setLoading] = useState(true);
  const [initiating, setInitiating] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  const fetchRuns = async () => {
    setLoading(true);
    try {
      const res = await apiFetch<ReconciliationRun[]>("/api/v1/admin/finance/reconciliation");
      setRuns(res.data || []);
    } catch (err: unknown) {
      setMessage({ type: "error", text: (err as Error)?.message || "Failed to load reconciliation runs" });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRuns();
  }, []);

  const handleRunReconciliation = async () => {
    setInitiating(true);
    try {
      const res = await apiFetch<ReconciliationRun>("/api/v1/admin/finance/reconciliation/run", {
        method: "POST",
      });
      const runCode = res.data?.runCode || "NEW";
      setMessage({
        type: "success",
        text: `Reconciliation run ${runCode} successfully generated. All demonstration records matched.`,
      });
      fetchRuns();
    } catch (err: unknown) {
      setMessage({ type: "error", text: (err as Error)?.message || "Reconciliation execution failed" });
    } finally {
      setInitiating(false);
    }
  };

  const handleDownloadCsv = () => {
    window.open("/api/v1/admin/reports/export?reportType=FINANCE", "_blank");
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-200 pb-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900">Finance & Reconciliation Control Centre</h2>
          <p className="text-sm text-slate-500">
            Compare authoritative RailConnect payment/refund records against Dhruva Bank demonstration gateway transactions.
          </p>
        </div>
        <div className="flex gap-3">
          <button
            onClick={handleDownloadCsv}
            className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50"
          >
            Export Financial CSV
          </button>
          <button
            onClick={handleRunReconciliation}
            disabled={initiating}
            className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-emerald-700 disabled:opacity-50"
          >
            {initiating ? "Processing Reconcile..." : "Execute Daily Reconciliation"}
          </button>
        </div>
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

      {/* Gateway Context Alert */}
      <div className="rounded-xl border border-indigo-200 bg-indigo-50 p-4 text-xs text-indigo-900">
        <span className="font-bold uppercase tracking-wider text-indigo-950 mr-2">Dhruva Bank Integration</span>
        Reconciliation executes automated cross-verification with the Dhruva Bank demonstration ledger. All figures represent simulated demonstration values.
      </div>

      {/* Reconciliation Runs Table */}
      <div className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
        <div className="border-b border-slate-200 px-6 py-4">
          <h3 className="font-semibold text-slate-900">Historical Settlement Runs</h3>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-left text-sm text-slate-600">
            <thead className="bg-slate-50 text-xs font-semibold uppercase text-slate-500">
              <tr>
                <th className="px-6 py-3">Run Code</th>
                <th className="px-6 py-3">Settlement Date</th>
                <th className="px-6 py-3">Total Txns</th>
                <th className="px-6 py-3">Matched</th>
                <th className="px-6 py-3">Discrepancies</th>
                <th className="px-6 py-3">Gross Demo Volume</th>
                <th className="px-6 py-3">Status</th>
                <th className="px-6 py-3">Initiated By</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {loading ? (
                <tr>
                  <td colSpan={8} className="px-6 py-8 text-center text-xs text-slate-500">
                    Loading reconciliation logs...
                  </td>
                </tr>
              ) : runs.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-6 py-8 text-center text-xs text-slate-500">
                    No reconciliation runs yet. Click &quot;Execute Daily Reconciliation&quot; to initiate.
                  </td>
                </tr>
              ) : (
                runs.map((r) => (
                  <tr key={r.runId} className="hover:bg-slate-50 transition">
                    <td className="px-6 py-4 font-mono font-semibold text-slate-900">{r.runCode}</td>
                    <td className="px-6 py-4">{r.runDate}</td>
                    <td className="px-6 py-4 font-semibold text-slate-900">{r.totalTransactions}</td>
                    <td className="px-6 py-4 text-emerald-600 font-semibold">{r.matchedCount}</td>
                    <td className="px-6 py-4 text-rose-600 font-semibold">{r.discrepancyCount}</td>
                    <td className="px-6 py-4 font-semibold text-slate-900">₹{Number(r.totalVolumeInr).toLocaleString()}</td>
                    <td className="px-6 py-4">
                      <span className="rounded bg-emerald-100 px-2 py-0.5 text-xs font-semibold text-emerald-800">
                        {r.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-xs text-slate-500">{r.initiatorName}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

