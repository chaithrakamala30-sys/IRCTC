"use client";

import { useEffect, useState, useCallback } from "react";
import { apiFetch, ApiRequestError } from "@railconnect/api";

type TicketSummary = {
  ticketId: string;
  ticketReference: string;
  passengerName: string;
  categoryDisplayName: string;
  subject: string;
  priority: string;
  status: string;
  staffName: string | null;
  isSlaBreached: boolean;
  createdAt: string;
};

type TicketDetail = {
  ticketId: string;
  ticketReference: string;
  passengerName: string;
  passengerEmail: string;
  categoryDisplayName: string;
  subject: string;
  description: string;
  priority: string;
  status: string;
  assignedStaffName: string | null;
  assignedTeamName: string | null;
  linkedBookingReference?: string;
  linkedPnr?: string;
  isSlaBreached: boolean;
  createdAt: string;
  messages: {
    messageId: string;
    senderName: string;
    senderType: string;
    isInternalNote: boolean;
    messageBody: string;
    createdAt: string;
  }[];
};

type DashboardStats = {
  totalTickets: number;
  openTickets: number;
  unassignedTickets: number;
  myAssignedTickets: number;
  escalatedTickets: number;
  resolvedTickets: number;
  slaBreachedTickets: number;
  averageSatisfactionScore: number;
};

export function SupportStaffConsole() {
  const [filter, setFilter] = useState("ALL");
  const [tickets, setTickets] = useState<TicketSummary[]>([]);
  const [selectedTicket, setSelectedTicket] = useState<TicketDetail | null>(null);
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [replyBody, setReplyBody] = useState("");
  const [isInternalNote, setIsInternalNote] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [qRes, sRes] = await Promise.all([
        apiFetch<TicketSummary[]>(`/api/v1/support/staff/queue?filter=${filter}`),
        apiFetch<DashboardStats>("/api/v1/support/admin/dashboard"),
      ]);
      setTickets(qRes.data ?? []);
      setStats(sRes.data ?? null);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Failed to load support workspace");
    } finally {
      setLoading(false);
    }
  }, [filter]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const viewTicket = async (ticketId: string) => {
    try {
      const res = await apiFetch<TicketDetail>(`/api/v1/support/tickets/${ticketId}`);
      if (res.data) setSelectedTicket(res.data);
    } catch {
      // Ignored
    }
  };

  const assignToMe = async () => {
    if (!selectedTicket) return;
    try {
      await apiFetch(`/api/v1/support/staff/tickets/${selectedTicket.ticketId}/assign`, {
        method: "POST",
        body: JSON.stringify({}),
      });
      setNotice(`Ticket ${selectedTicket.ticketReference} assigned to you.`);
      void viewTicket(selectedTicket.ticketId);
      void loadData();
    } catch {
      setError("Failed to assign ticket.");
    }
  };

  const updateStatus = async (newStatus: string) => {
    if (!selectedTicket) return;
    try {
      await apiFetch(`/api/v1/support/staff/tickets/${selectedTicket.ticketId}/status`, {
        method: "PATCH",
        body: JSON.stringify({
          newStatus,
          reason: `Status transition triggered by staff`,
        }),
      });
      setNotice(`Status updated to ${newStatus}`);
      void viewTicket(selectedTicket.ticketId);
      void loadData();
    } catch {
      setError("Failed to update status.");
    }
  };

  const escalateTicket = async () => {
    if (!selectedTicket) return;
    try {
      await apiFetch(`/api/v1/support/staff/tickets/${selectedTicket.ticketId}/escalate`, {
        method: "POST",
        body: JSON.stringify({
          escalationLevel: 2,
          reason: "Supervisor escalation requested from console",
        }),
      });
      setNotice(`Ticket ${selectedTicket.ticketReference} escalated.`);
      void viewTicket(selectedTicket.ticketId);
      void loadData();
    } catch {
      setError("Failed to escalate ticket.");
    }
  };

  const sendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTicket || !replyBody.trim()) return;
    try {
      await apiFetch(`/api/v1/support/tickets/${selectedTicket.ticketId}/messages`, {
        method: "POST",
        body: JSON.stringify({
          messageBody: replyBody.trim(),
          isInternalNote,
        }),
      });
      setReplyBody("");
      setIsInternalNote(false);
      void viewTicket(selectedTicket.ticketId);
    } catch {
      setError("Failed to post message.");
    }
  };

  return (
    <div className="space-y-6 text-slate-100">
      {/* Metric Tiles */}
      {stats && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="p-4 rounded-2xl bg-slate-900/60 border border-slate-800">
            <span className="text-xs text-slate-400">Open Tickets</span>
            <p className="text-2xl font-bold text-slate-100 mt-1">{stats.openTickets}</p>
            <span className="text-[11px] text-blue-400 mt-1 block">Awaiting response</span>
          </div>

          <div className="p-4 rounded-2xl bg-slate-900/60 border border-slate-800">
            <span className="text-xs text-slate-400">Unassigned Queue</span>
            <p className="text-2xl font-bold text-amber-400 mt-1">{stats.unassignedTickets}</p>
            <span className="text-[11px] text-slate-500 mt-1 block">Needs triage</span>
          </div>

          <div className="p-4 rounded-2xl bg-slate-900/60 border border-slate-800">
            <span className="text-xs text-slate-400">Escalated Cases</span>
            <p className="text-2xl font-bold text-rose-400 mt-1">{stats.escalatedTickets}</p>
            <span className="text-[11px] text-rose-300 mt-1 block">Supervisor attention</span>
          </div>

          <div className="p-4 rounded-2xl bg-slate-900/60 border border-slate-800">
            <span className="text-xs text-slate-400">Customer CSAT</span>
            <p className="text-2xl font-bold text-emerald-400 mt-1">{stats.averageSatisfactionScore} / 5.0</p>
            <span className="text-[11px] text-emerald-300 mt-1 block">Satisfaction score</span>
          </div>
        </div>
      )}

      {notice && (
        <div className="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-xs">
          {notice}
        </div>
      )}
      {error && (
        <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs">
          {error}
        </div>
      )}

      {/* Main Support Workspace */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left: Queue Filter & List */}
        <div className="lg:col-span-1 space-y-3">
          <div className="flex gap-1.5 flex-wrap">
            {["ALL", "MY_ASSIGNED", "UNASSIGNED", "ESCALATED", "HIGH_CRITICAL"].map((f) => (
              <button
                key={f}
                onClick={() => setFilter(f)}
                className={`text-[11px] px-2.5 py-1.5 rounded-lg border transition ${
                  filter === f
                    ? "bg-amber-500 text-slate-950 font-bold border-amber-400"
                    : "bg-slate-900/60 text-slate-400 border-slate-800 hover:border-slate-700 hover:text-slate-200"
                }`}
              >
                {f.replace("_", " ")}
              </button>
            ))}
          </div>

          <div className="space-y-2 max-h-[600px] overflow-y-auto pr-1">
            {loading ? (
              <div className="p-8 text-center text-xs text-slate-500">Loading cases...</div>
            ) : tickets.length === 0 ? (
              <div className="p-8 text-center rounded-xl bg-slate-900/40 border border-slate-800 text-xs text-slate-400">
                No tickets in this queue.
              </div>
            ) : (
              tickets.map((t) => (
                <div
                  key={t.ticketId}
                  onClick={() => void viewTicket(t.ticketId)}
                  className={`p-3.5 rounded-xl border transition cursor-pointer ${
                    selectedTicket?.ticketId === t.ticketId
                      ? "bg-slate-800/80 border-amber-500/50 shadow-md"
                      : "bg-slate-900/60 border-slate-800 hover:border-slate-700"
                  }`}
                >
                  <div className="flex items-center justify-between text-[10px]">
                    <span className="font-mono text-amber-400 font-semibold">{t.ticketReference}</span>
                    <span
                      className={`px-1.5 py-0.5 rounded font-bold uppercase ${
                        t.priority === "URGENT" || t.priority === "CRITICAL"
                          ? "bg-rose-500/20 text-rose-300"
                          : "bg-slate-800 text-slate-400"
                      }`}
                    >
                      {t.priority}
                    </span>
                  </div>
                  <h4 className="text-xs font-semibold text-slate-200 mt-1 line-clamp-1">{t.subject}</h4>
                  <div className="flex items-center justify-between text-[10px] text-slate-500 mt-2">
                    <span>{t.passengerName}</span>
                    <span>{t.status}</span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Right: Ticket Detail & Conversation Hub */}
        <div className="lg:col-span-2">
          {selectedTicket ? (
            <div className="p-6 rounded-2xl bg-slate-900/60 border border-slate-800 flex flex-col h-full min-h-[600px]">
              {/* Action Toolbar */}
              <div className="pb-4 border-b border-slate-800 flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs text-amber-400 font-bold">{selectedTicket.ticketReference}</span>
                    <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300">
                      {selectedTicket.status}
                    </span>
                    {selectedTicket.isSlaBreached && (
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-rose-500/20 text-rose-300 border border-rose-500/30 font-bold">
                        SLA BREACHED
                      </span>
                    )}
                  </div>
                  <h3 className="text-base font-bold text-slate-100 mt-1">{selectedTicket.subject}</h3>
                  <p className="text-xs text-slate-400 mt-0.5">
                    Passenger: {selectedTicket.passengerName} ({selectedTicket.passengerEmail})
                  </p>
                </div>

                <div className="flex flex-wrap gap-2">
                  <button
                    onClick={assignToMe}
                    className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs border border-slate-700 transition"
                  >
                    Assign to Me
                  </button>
                  <button
                    onClick={escalateTicket}
                    className="px-3 py-1.5 rounded-lg bg-rose-500/20 hover:bg-rose-500/30 text-rose-300 text-xs border border-rose-500/30 transition"
                  >
                    Escalate
                  </button>
                  <button
                    onClick={() => updateStatus("RESOLVED")}
                    className="px-3 py-1.5 rounded-lg bg-emerald-500/20 hover:bg-emerald-500/30 text-emerald-300 text-xs border border-emerald-500/30 transition font-bold"
                  >
                    Resolve
                  </button>
                </div>
              </div>

              {/* Context Panel (Linked Entities) */}
              {(selectedTicket.linkedBookingReference || selectedTicket.linkedPnr) && (
                <div className="my-3 p-3 rounded-xl bg-slate-800/40 border border-slate-700/60 text-xs flex gap-6">
                  {selectedTicket.linkedBookingReference && (
                    <div>
                      <span className="text-slate-500 block text-[10px]">Linked Booking</span>
                      <span className="font-mono font-semibold text-slate-200">{selectedTicket.linkedBookingReference}</span>
                    </div>
                  )}
                  {selectedTicket.linkedPnr && (
                    <div>
                      <span className="text-slate-500 block text-[10px]">Linked PNR</span>
                      <span className="font-mono font-semibold text-amber-400">{selectedTicket.linkedPnr}</span>
                    </div>
                  )}
                </div>
              )}

              {/* Thread */}
              <div className="flex-1 py-4 space-y-4 overflow-y-auto max-h-[380px] pr-2">
                {selectedTicket.messages.map((m) => (
                  <div
                    key={m.messageId}
                    className={`p-4 rounded-2xl max-w-[85%] text-xs leading-relaxed ${
                      m.isInternalNote
                        ? "bg-amber-500/10 border border-amber-500/40 text-amber-200 mx-auto w-full max-w-full"
                        : m.senderType === "STAFF"
                        ? "bg-blue-600/20 border border-blue-500/30 self-end ml-auto text-blue-100"
                        : "bg-slate-800 border border-slate-700 self-start mr-auto text-slate-200"
                    }`}
                  >
                    <div className="flex items-center justify-between gap-3 mb-1 text-[10px]">
                      <span className="font-bold">
                        {m.isInternalNote ? "🔒 INTERNAL STAFF NOTE" : `${m.senderName} (${m.senderType})`}
                      </span>
                      <span className="text-slate-400">{new Date(m.createdAt).toLocaleTimeString()}</span>
                    </div>
                    <p className="whitespace-pre-wrap">{m.messageBody}</p>
                  </div>
                ))}
              </div>

              {/* Reply Box with Internal Note toggle */}
              <form onSubmit={sendMessage} className="pt-4 border-t border-slate-800 space-y-2">
                <div className="flex items-center justify-between">
                  <label className="flex items-center gap-2 text-xs text-amber-400 cursor-pointer select-none">
                    <input
                      type="checkbox"
                      checked={isInternalNote}
                      onChange={(e) => setIsInternalNote(e.target.checked)}
                      className="rounded border-slate-700 text-amber-500 focus:ring-amber-400 bg-slate-900"
                    />
                    Mark as internal note (hidden from passenger)
                  </label>
                  <span className="text-[10px] text-slate-500">
                    {isInternalNote ? "Confidential note" : "Visible to passenger"}
                  </span>
                </div>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={replyBody}
                    onChange={(e) => setReplyBody(e.target.value)}
                    placeholder={
                      isInternalNote
                        ? "Add private staff investigation notes..."
                        : "Send reply to passenger..."
                    }
                    className="flex-1 px-3 py-2 rounded-xl bg-slate-800/80 border border-slate-700 text-slate-100 text-xs focus:ring-1 focus:ring-amber-400"
                  />
                  <button
                    type="submit"
                    className={`px-4 py-2 rounded-xl text-xs font-bold transition ${
                      isInternalNote
                        ? "bg-amber-600 hover:bg-amber-500 text-slate-950"
                        : "bg-blue-600 hover:bg-blue-500 text-white"
                    }`}
                  >
                    {isInternalNote ? "Post Note" : "Send Reply"}
                  </button>
                </div>
              </form>
            </div>
          ) : (
            <div className="p-16 text-center rounded-2xl bg-slate-900/40 border border-slate-800 text-slate-500 text-xs">
              Select a support ticket from the queue to start investigating and communicating.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

