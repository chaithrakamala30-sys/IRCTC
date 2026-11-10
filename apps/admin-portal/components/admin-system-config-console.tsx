"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@railconnect/api";

type SystemConfig = {
  configKey: string;
  configValue: string;
  description: string;
  isProtected: boolean;
  updatedAt: string;
};

export function AdminSystemConfigConsole() {
  const [configs, setConfigs] = useState<SystemConfig[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  const fetchConfigs = async () => {
    setLoading(true);
    try {
      const res = await apiFetch<SystemConfig[]>("/api/v1/admin/configurations");
      setConfigs(res.data || []);
    } catch (err: unknown) {
      setMessage({ type: "error", text: (err as Error)?.message || "Failed to load configurations" });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchConfigs();
  }, []);

  const handleSave = async (key: string) => {
    if (!reason.trim()) {
      alert("Please provide an administrative reason for auditing.");
      return;
    }
    setSubmitting(true);
    try {
      await apiFetch(`/api/v1/admin/configurations/${key}`, {
        method: "POST",
        body: JSON.stringify({ configValue: editValue, reason }),
      });
      setMessage({ type: "success", text: `Parameter ${key} successfully updated.` });
      setEditingKey(null);
      setReason("");
      fetchConfigs();
    } catch (err: unknown) {
      setMessage({ type: "error", text: (err as Error)?.message || "Configuration update failed" });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h2 className="text-xl font-bold text-slate-900">System Configuration & Operational Flags</h2>
        <p className="text-sm text-slate-500">
          Centrally govern runtime feature gates, maintenance switches, and threshold boundaries. Every modification is cryptographically audited.
        </p>
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

      <div className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
        <div className="divide-y divide-slate-200">
          {loading ? (
            <p className="p-6 text-center text-xs text-slate-500">Loading parameters...</p>
          ) : (
            configs.map((c) => (
              <div key={c.configKey} className="p-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-sm font-bold text-slate-900">{c.configKey}</span>
                    {c.isProtected && (
                      <span className="rounded bg-rose-100 px-2 py-0.5 text-[10px] font-semibold text-rose-800">
                        Protected / Core
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-slate-500 max-w-xl">{c.description}</p>
                </div>

                <div className="flex items-center gap-3">
                  {editingKey === c.configKey ? (
                    <div className="flex flex-col sm:flex-row items-end sm:items-center gap-2">
                      <input
                        type="text"
                        value={editValue}
                        onChange={(e) => setEditValue(e.target.value)}
                        className="rounded-md border border-slate-300 px-3 py-1.5 text-xs font-mono w-40"
                      />
                      <input
                        type="text"
                        placeholder="Audit reason..."
                        value={reason}
                        onChange={(e) => setReason(e.target.value)}
                        className="rounded-md border border-slate-300 px-3 py-1.5 text-xs w-48"
                      />
                      <div className="flex gap-1">
                        <button
                          onClick={() => handleSave(c.configKey)}
                          disabled={submitting}
                          className="rounded bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-indigo-700 disabled:opacity-50"
                        >
                          Save
                        </button>
                        <button
                          onClick={() => setEditingKey(null)}
                          className="rounded border border-slate-300 px-2.5 py-1.5 text-xs text-slate-600 hover:bg-slate-50"
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div className="flex items-center gap-4">
                      <span className="font-mono text-sm font-semibold rounded bg-slate-100 px-3 py-1 text-slate-800">
                        {c.configValue}
                      </span>
                      <button
                        onClick={() => {
                          setEditingKey(c.configKey);
                          setEditValue(c.configValue);
                        }}
                        className="rounded border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50"
                      >
                        Modify
                      </button>
                    </div>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

