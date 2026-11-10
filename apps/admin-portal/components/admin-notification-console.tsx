"use client";

import { useEffect, useState } from "react";
import { apiFetch, ApiRequestError } from "@railconnect/api";

type DeliveryStats = {
  totalInApp: number;
  unreadInApp: number;
  totalOutbox: number;
  pendingOutbox: number;
  failedOutbox: number;
  totalDeliveries: number;
  sentDeliveries: number;
  failedDeliveries: number;
};

type NotificationTemplate = {
  id: string;
  templateCode: string;
  category: string;
  eventType: string;
  titleTemplate: string;
  bodyTemplate: string;
  priority: string;
  defaultDeepLink: string;
  isActive: boolean;
};

export function AdminNotificationConsole() {
  const [stats, setStats] = useState<DeliveryStats | null>(null);
  const [templates, setTemplates] = useState<NotificationTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  // Announcement state
  const [announcementTitle, setAnnouncementTitle] = useState("");
  const [announcementMessage, setAnnouncementMessage] = useState("");
  const [priority, setPriority] = useState("NORMAL");
  const [broadcasting, setBroadcasting] = useState(false);

  const loadData = async () => {
    setLoading(true);
    setError("");
    try {
      const [statsRes, templatesRes] = await Promise.all([
        apiFetch<DeliveryStats>("/api/v1/admin/notifications/statistics"),
        apiFetch<NotificationTemplate[]>("/api/v1/admin/notifications/templates"),
      ]);
      setStats(statsRes.data ?? null);
      setTemplates(templatesRes.data ?? []);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Failed to load notification stats");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, []);

  const handleBroadcast = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!announcementTitle.trim() || !announcementMessage.trim()) return;
    setBroadcasting(true);
    setError("");
    setNotice("");
    try {
      await apiFetch("/api/v1/admin/notifications/announcements", {
        method: "POST",
        body: JSON.stringify({
          title: announcementTitle.trim(),
          message: announcementMessage.trim(),
          priority,
          targetScope: "ALL",
          expiryHours: 24,
        }),
      });
      setNotice("System-wide announcement published and queued for multi-channel dispatch.");
      setAnnouncementTitle("");
      setAnnouncementMessage("");
      void loadData();
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Failed to broadcast announcement");
    } finally {
      setBroadcasting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Metrics Banner */}
      {stats && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="p-4 rounded-2xl bg-slate-900/60 border border-slate-800">
            <span className="text-xs text-slate-400">Total In-App Messages</span>
            <p className="text-2xl font-bold text-slate-100 mt-1">{stats.totalInApp}</p>
            <span className="text-[11px] text-amber-400 mt-1 block">
              {stats.unreadInApp} unread across accounts
            </span>
          </div>

          <div className="p-4 rounded-2xl bg-slate-900/60 border border-slate-800">
            <span className="text-xs text-slate-400">Outbox Queue Status</span>
            <p className="text-2xl font-bold text-slate-100 mt-1">{stats.totalOutbox}</p>
            <span className="text-[11px] text-emerald-400 mt-1 block">
              {stats.pendingOutbox} pending / retrying
            </span>
          </div>

          <div className="p-4 rounded-2xl bg-slate-900/60 border border-slate-800">
            <span className="text-xs text-slate-400">Dead-Letter Events</span>
            <p className="text-2xl font-bold text-rose-400 mt-1">{stats.failedOutbox}</p>
            <span className="text-[11px] text-slate-500 mt-1 block">Exceeded max retry limits</span>
          </div>

          <div className="p-4 rounded-2xl bg-slate-900/60 border border-slate-800">
            <span className="text-xs text-slate-400">Total Deliveries</span>
            <p className="text-2xl font-bold text-slate-100 mt-1">{stats.totalDeliveries}</p>
            <span className="text-[11px] text-emerald-400 mt-1 block">
              {stats.sentDeliveries} delivered / sent
            </span>
          </div>
        </div>
      )}

      {notice && (
        <div className="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-sm">
          {notice}
        </div>
      )}
      {error && (
        <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-sm">
          {error}
        </div>
      )}

      {/* Broadcast Announcement Form */}
      <div className="p-6 rounded-2xl bg-slate-900/40 border border-slate-800/80">
        <h3 className="text-base font-semibold text-slate-100">
          Publish System-Wide Passenger Announcement
        </h3>
        <p className="text-xs text-slate-400 mt-1">
          Broadcast authorized maintenance notices, festival specials, or emergency advisories.
        </p>
        <form onSubmit={handleBroadcast} className="mt-4 space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="md:col-span-2">
              <label className="block text-xs text-slate-400 mb-1 font-medium">Title</label>
              <input
                type="text"
                value={announcementTitle}
                onChange={(e) => setAnnouncementTitle(e.target.value)}
                placeholder="e.g. Diwali Holiday Special Train Schedule Released"
                className="w-full px-3 py-2 rounded-xl bg-slate-800/80 border border-slate-700 text-slate-100 text-xs focus:ring-1 focus:ring-amber-400"
                required
              />
            </div>
            <div>
              <label className="block text-xs text-slate-400 mb-1 font-medium">Priority</label>
              <select
                value={priority}
                onChange={(e) => setPriority(e.target.value)}
                className="w-full px-3 py-2 rounded-xl bg-slate-800/80 border border-slate-700 text-slate-100 text-xs focus:ring-1 focus:ring-amber-400"
              >
                <option value="NORMAL">NORMAL</option>
                <option value="HIGH">HIGH</option>
                <option value="URGENT">URGENT</option>
              </select>
            </div>
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1 font-medium">Message Body</label>
            <textarea
              rows={3}
              value={announcementMessage}
              onChange={(e) => setAnnouncementMessage(e.target.value)}
              placeholder="Provide clear, concise details without exposing internal system identifiers..."
              className="w-full px-3 py-2 rounded-xl bg-slate-800/80 border border-slate-700 text-slate-100 text-xs focus:ring-1 focus:ring-amber-400"
              required
            />
          </div>
          <button
            type="submit"
            disabled={broadcasting}
            className="px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs transition disabled:opacity-50"
          >
            {broadcasting ? "Broadcasting..." : "Publish Announcement"}
          </button>
        </form>
      </div>

      {/* Templates Reference */}
      <div className="p-6 rounded-2xl bg-slate-900/40 border border-slate-800/80">
        <h3 className="text-base font-semibold text-slate-100">
          Configured Notification Templates
        </h3>
        <p className="text-xs text-slate-400 mt-1">
          Authorized multi-channel templates mapped across booking, payment, RAC/waitlist, and operations.
        </p>

        {loading ? (
          <div className="py-8 text-center text-slate-400 text-xs">Loading templates...</div>
        ) : (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-slate-800 text-slate-400">
                  <th className="py-2 px-3">Template Code</th>
                  <th className="py-2 px-3">Category</th>
                  <th className="py-2 px-3">Priority</th>
                  <th className="py-2 px-3">Title Template</th>
                  <th className="py-2 px-3">Target Deep Link</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {templates.map((tpl) => (
                  <tr key={tpl.id} className="hover:bg-slate-800/30">
                    <td className="py-2.5 px-3 font-mono font-medium text-amber-400">
                      {tpl.templateCode}
                    </td>
                    <td className="py-2.5 px-3 text-slate-300">{tpl.category}</td>
                    <td className="py-2.5 px-3">
                      <span
                        className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                          tpl.priority === "URGENT"
                            ? "bg-rose-500/20 text-rose-300 border border-rose-500/30"
                            : "bg-slate-800 text-slate-300"
                        }`}
                      >
                        {tpl.priority}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-slate-300 max-w-xs truncate">
                      {tpl.titleTemplate}
                    </td>
                    <td className="py-2.5 px-3 font-mono text-slate-400">{tpl.defaultDeepLink}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
