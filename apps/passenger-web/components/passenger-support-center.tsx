"use client";

import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { apiFetch, ApiRequestError } from "../lib/api";

type Category = {
  categoryId: string;
  categoryCode: string;
  displayName: string;
  department: string;
  defaultPriority: string;
};

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
  categoryDisplayName: string;
  subject: string;
  description: string;
  priority: string;
  status: string;
  staffName: string | null;
  linkedBookingReference?: string;
  linkedPnr?: string;
  createdAt: string;
  messages: {
    messageId: string;
    senderName: string;
    senderType: string;
    messageBody: string;
    createdAt: string;
  }[];
};

export function PassengerSupportCenter() {
  const [tickets, setTickets] = useState<TicketSummary[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [selectedTicket, setSelectedTicket] = useState<TicketDetail | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [loading, setLoading] = useState(true);
  const [actionNotice, setActionNotice] = useState("");
  const [replyBody, setReplyBody] = useState("");

  // Create Form State
  const [catId, setCatId] = useState("");
  const [subject, setSubject] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState("NORMAL");
  const [submitting, setSubmitting] = useState(false);

  // Feedback State
  const [showFeedbackModal, setShowFeedbackModal] = useState(false);
  const [rating, setRating] = useState(5);
  const [feedbackComment, setFeedbackComment] = useState("");

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [tRes, cRes] = await Promise.all([
        apiFetch<TicketSummary[]>("/api/v1/support/tickets"),
        apiFetch<Category[]>("/api/v1/support/categories"),
      ]);
      setTickets(tRes.data ?? []);
      setCategories(cRes.data ?? []);
      if (cRes.data && cRes.data.length > 0 && !catId) {
        setCatId(cRes.data[0].categoryId);
      }
    } catch {
      // Offline or unauthenticated
    } finally {
      setLoading(false);
    }
  }, [catId]);

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

  const handleCreateTicket = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!subject.trim() || !description.trim() || !catId) return;
    setSubmitting(true);
    try {
      await apiFetch("/api/v1/support/tickets", {
        method: "POST",
        body: JSON.stringify({
          categoryId: catId,
          subject: subject.trim(),
          description: description.trim(),
          priority,
        }),
      });
      setShowCreateModal(false);
      setSubject("");
      setDescription("");
      setActionNotice("Support ticket submitted successfully! A reference number has been assigned.");
      void loadData();
    } catch (err) {
      setActionNotice(err instanceof ApiRequestError ? err.message : "Failed to create ticket");
    } finally {
      setSubmitting(false);
    }
  };

  const sendReply = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTicket || !replyBody.trim()) return;
    try {
      await apiFetch(`/api/v1/support/tickets/${selectedTicket.ticketId}/messages`, {
        method: "POST",
        body: JSON.stringify({
          messageBody: replyBody.trim(),
          isInternalNote: false,
        }),
      });
      setReplyBody("");
      void viewTicket(selectedTicket.ticketId);
    } catch {
      // Ignored
    }
  };

  const handleFeedback = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTicket) return;
    try {
      await apiFetch(`/api/v1/support/tickets/${selectedTicket.ticketId}/feedback`, {
        method: "POST",
        body: JSON.stringify({
          satisfactionRating: rating,
          comments: feedbackComment.trim(),
        }),
      });
      setShowFeedbackModal(false);
      setActionNotice("Thank you for your feedback! It helps us improve our customer service.");
    } catch {
      // Ignored
    }
  };

  return (
    <div className="max-w-6xl mx-auto px-4 py-8 text-slate-100">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-6 border-b border-slate-800 gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Customer Support & Helpdesk</h1>
          <p className="text-xs text-slate-400 mt-1">
            Track inquiries, report payment or booking issues, and communicate directly with railway staff.
          </p>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs transition shadow-lg shadow-amber-500/20"
        >
          + Raise New Ticket
        </button>
      </div>

      {actionNotice && (
        <div className="mt-4 p-3 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-300 text-xs">
          {actionNotice}
        </div>
      )}

      {/* Main Workspace Layout */}
      <div className="mt-6 grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: My Tickets List */}
        <div className="lg:col-span-1 space-y-3">
          <h2 className="text-sm font-semibold text-slate-300">My Requests ({tickets.length})</h2>
          {loading ? (
            <div className="p-8 text-center text-xs text-slate-500">Loading tickets...</div>
          ) : tickets.length === 0 ? (
            <div className="p-8 text-center rounded-2xl bg-slate-900/40 border border-slate-800 text-xs text-slate-400">
              No support tickets found. Everything is smooth!
            </div>
          ) : (
            tickets.map((t) => (
              <div
                key={t.ticketId}
                onClick={() => void viewTicket(t.ticketId)}
                className={`p-4 rounded-xl border transition cursor-pointer ${
                  selectedTicket?.ticketId === t.ticketId
                    ? "bg-slate-800/80 border-amber-500/50 shadow-md"
                    : "bg-slate-900/60 border-slate-800 hover:border-slate-700"
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="font-mono text-[10px] text-amber-400 font-semibold">{t.ticketReference}</span>
                  <span
                    className={`text-[9px] px-1.5 py-0.5 rounded font-bold uppercase tracking-wider ${
                      t.status === "RESOLVED"
                        ? "bg-emerald-500/20 text-emerald-300"
                        : t.status === "OPEN"
                        ? "bg-blue-500/20 text-blue-300"
                        : "bg-amber-500/20 text-amber-300"
                    }`}
                  >
                    {t.status.replace("_", " ")}
                  </span>
                </div>
                <h3 className="text-xs font-semibold text-slate-200 mt-1 line-clamp-1">{t.subject}</h3>
                <span className="text-[10px] text-slate-500 mt-2 block">
                  {t.categoryDisplayName} &bull; {new Date(t.createdAt).toLocaleDateString()}
                </span>
              </div>
            ))
          )}
        </div>

        {/* Right Column: Ticket Conversation View */}
        <div className="lg:col-span-2">
          {selectedTicket ? (
            <div className="p-6 rounded-2xl bg-slate-900/60 border border-slate-800 flex flex-col h-full min-h-[500px]">
              {/* Ticket Top bar */}
              <div className="pb-4 border-b border-slate-800 flex flex-wrap items-center justify-between gap-2">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs text-amber-400 font-bold">{selectedTicket.ticketReference}</span>
                    <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                      {selectedTicket.categoryDisplayName}
                    </span>
                    <span className="text-[10px] uppercase font-bold text-slate-400">
                      Priority: {selectedTicket.priority}
                    </span>
                  </div>
                  <h2 className="text-base font-bold text-slate-100 mt-1">{selectedTicket.subject}</h2>
                </div>

                <div className="flex items-center gap-2">
                  {selectedTicket.status === "RESOLVED" && (
                    <button
                      onClick={() => setShowFeedbackModal(true)}
                      className="px-3 py-1.5 rounded-lg bg-emerald-500/20 hover:bg-emerald-500/30 text-emerald-300 border border-emerald-500/40 text-xs font-medium transition"
                    >
                      Rate Support
                    </button>
                  )}
                </div>
              </div>

              {/* Message thread */}
              <div className="flex-1 py-4 space-y-4 overflow-y-auto max-h-[420px] pr-2">
                {selectedTicket.messages.map((m) => (
                  <div
                    key={m.messageId}
                    className={`p-4 rounded-2xl max-w-[85%] text-xs leading-relaxed ${
                      m.senderType === "STAFF"
                        ? "bg-slate-800 border border-slate-700 self-start text-slate-200 mr-auto"
                        : "bg-amber-500/10 border border-amber-500/30 self-end text-amber-100 ml-auto"
                    }`}
                  >
                    <div className="flex items-center justify-between gap-3 mb-1 text-[10px]">
                      <span className="font-bold text-slate-300">{m.senderName} ({m.senderType})</span>
                      <span className="text-slate-500">{new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                    </div>
                    <p className="whitespace-pre-wrap">{m.messageBody}</p>
                  </div>
                ))}
              </div>

              {/* Reply Input */}
              {selectedTicket.status !== "CLOSED" ? (
                <form onSubmit={sendReply} className="pt-4 border-t border-slate-800 flex gap-2">
                  <input
                    type="text"
                    value={replyBody}
                    onChange={(e) => setReplyBody(e.target.value)}
                    placeholder="Type a message or provide requested information..."
                    className="flex-1 px-3 py-2 rounded-xl bg-slate-800/80 border border-slate-700 text-slate-100 text-xs focus:ring-1 focus:ring-amber-400"
                  />
                  <button
                    type="submit"
                    className="px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs transition"
                  >
                    Reply
                  </button>
                </form>
              ) : (
                <div className="p-3 text-center text-xs text-slate-500 border-t border-slate-800">
                  This ticket has been officially closed.
                </div>
              )}
            </div>
          ) : (
            <div className="p-16 text-center rounded-2xl bg-slate-900/40 border border-slate-800 text-slate-500 text-xs">
              Select a ticket from the left column to view the conversation and status.
            </div>
          )}
        </div>
      </div>

      {/* Create Ticket Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-in fade-in">
          <div className="w-full max-w-lg p-6 rounded-2xl bg-slate-900 border border-slate-700 shadow-2xl">
            <h2 className="text-base font-bold text-slate-100">Raise a Support Ticket</h2>
            <p className="text-xs text-slate-400 mt-1">
              Select the appropriate department and provide relevant details.
            </p>
            <form onSubmit={handleCreateTicket} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs text-slate-400 mb-1 font-medium">Issue Category</label>
                <select
                  value={catId}
                  onChange={(e) => setCatId(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-slate-800 border border-slate-700 text-slate-100 text-xs"
                >
                  {categories.map((c) => (
                    <option key={c.categoryId} value={c.categoryId}>
                      {c.displayName} ({c.department})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs text-slate-400 mb-1 font-medium">Subject</label>
                <input
                  type="text"
                  value={subject}
                  onChange={(e) => setSubject(e.target.value)}
                  placeholder="e.g. Payment deducted but PNR not generated"
                  className="w-full px-3 py-2 rounded-xl bg-slate-800 border border-slate-700 text-slate-100 text-xs"
                  required
                />
              </div>

              <div>
                <label className="block text-xs text-slate-400 mb-1 font-medium">Priority</label>
                <select
                  value={priority}
                  onChange={(e) => setPriority(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-slate-800 border border-slate-700 text-slate-100 text-xs"
                >
                  <option value="NORMAL">NORMAL</option>
                  <option value="HIGH">HIGH</option>
                  <option value="URGENT">URGENT</option>
                </select>
              </div>

              <div>
                <label className="block text-xs text-slate-400 mb-1 font-medium">Description</label>
                <textarea
                  rows={4}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Please describe the issue in detail with relevant booking or payment references..."
                  className="w-full px-3 py-2 rounded-xl bg-slate-800 border border-slate-700 text-slate-100 text-xs"
                  required
                />
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs transition disabled:opacity-50"
                >
                  {submitting ? "Submitting..." : "Submit Ticket"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Feedback Modal */}
      {showFeedbackModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-in fade-in">
          <div className="w-full max-w-md p-6 rounded-2xl bg-slate-900 border border-slate-700 shadow-2xl">
            <h2 className="text-base font-bold text-slate-100">Rate Customer Support</h2>
            <p className="text-xs text-slate-400 mt-1">
              How satisfied are you with the resolution of ticket {selectedTicket?.ticketReference}?
            </p>
            <form onSubmit={handleFeedback} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs text-slate-400 mb-2 font-medium">Satisfaction Rating</label>
                <div className="flex gap-2">
                  {[1, 2, 3, 4, 5].map((star) => (
                    <button
                      type="button"
                      key={star}
                      onClick={() => setRating(star)}
                      className={`text-xl px-3 py-1.5 rounded-lg border transition ${
                        rating >= star
                          ? "bg-amber-500/20 border-amber-400 text-amber-400"
                          : "bg-slate-800 border-slate-700 text-slate-500"
                      }`}
                    >
                      ★
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-xs text-slate-400 mb-1 font-medium">Comments (Optional)</label>
                <textarea
                  rows={3}
                  value={feedbackComment}
                  onChange={(e) => setFeedbackComment(e.target.value)}
                  placeholder="Tell us what went well or how we can improve..."
                  className="w-full px-3 py-2 rounded-xl bg-slate-800 border border-slate-700 text-slate-100 text-xs"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setShowFeedbackModal(false)}
                  className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs transition"
                >
                  Submit Rating
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
