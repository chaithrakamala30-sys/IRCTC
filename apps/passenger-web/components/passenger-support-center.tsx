"use client";

import { useEffect, useState, useCallback } from "react";
import { apiFetch, ApiRequestError } from "../lib/api";
import { GoogleIcon } from "./google-icon";

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
    <div className="max-w-6xl mx-auto px-2 py-4 text-slate-100">
      {/* 1. Header Banner Card */}
      <div className="p-6 md:p-8 rounded-2xl bg-gradient-to-r from-[#06142e] via-[#081a3e] to-[#0a2352] border border-[#258bff]/30 shadow-2xl relative overflow-hidden mb-8">
        <div className="absolute right-0 top-0 bottom-0 w-1/3 bg-radial from-[#258bff]/15 to-transparent pointer-events-none" />
        
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-6 relative z-10">
          <div>
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#1877f2]/20 border border-[#38bdf8]/30 text-xs font-semibold text-[#73dfff] mb-3">
              <GoogleIcon name="support_agent" size={16} color="#73dfff" />
              <span>Official 24x7 RailConnect Helpdesk</span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
              Customer Support &amp; Grievances
            </h1>
            <p className="text-xs sm:text-sm text-[#8fa3c7] mt-1.5 max-w-2xl leading-relaxed">
              Track disputes, report payment or refund issues, request coach assistance, and communicate directly with Indian Railways passenger support staff.
            </p>
          </div>

          <button
            onClick={() => setShowCreateModal(true)}
            className="inline-flex items-center justify-center gap-2 px-5 py-3 rounded-xl bg-gradient-to-r from-[#1877f2] to-[#1264c5] hover:from-[#258bff] hover:to-[#1877f2] text-white font-bold text-xs sm:text-sm transition shadow-lg shadow-[#1877f2]/40 hover:-translate-y-0.5 border border-[#73dfff]/40 cursor-pointer flex-shrink-0"
          >
            <GoogleIcon name="add_circle" size={18} />
            <span>Raise New Ticket</span>
          </button>
        </div>

        {/* 24x7 Quick Channels Strip */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mt-6 pt-5 border-t border-[#258bff]/20">
          <div className="flex items-center gap-3 p-3 rounded-xl bg-[#0a1b38]/70 border border-[#258bff]/20">
            <div className="w-9 h-9 rounded-lg bg-[#1877f2]/20 flex items-center justify-center text-[#73dfff]">
              <GoogleIcon name="call" size={20} color="#73dfff" />
            </div>
            <div>
              <span className="text-[11px] text-[#8fa3c7] font-medium block">Toll-Free Helpline</span>
              <strong className="text-xs font-bold text-white">139 (Rail Madad)</strong>
            </div>
          </div>

          <div className="flex items-center gap-3 p-3 rounded-xl bg-[#0a1b38]/70 border border-[#258bff]/20">
            <div className="w-9 h-9 rounded-lg bg-[#1877f2]/20 flex items-center justify-center text-[#73dfff]">
              <GoogleIcon name="mail" size={20} color="#73dfff" />
            </div>
            <div>
              <span className="text-[11px] text-[#8fa3c7] font-medium block">Email Support</span>
              <strong className="text-xs font-bold text-white">care@railconnect.gov.in</strong>
            </div>
          </div>

          <div className="flex items-center gap-3 p-3 rounded-xl bg-[#0a1b38]/70 border border-[#258bff]/20">
            <div className="w-9 h-9 rounded-lg bg-[#1877f2]/20 flex items-center justify-center text-[#4ade80]">
              <GoogleIcon name="verified" size={20} color="#4ade80" />
            </div>
            <div>
              <span className="text-[11px] text-[#8fa3c7] font-medium block">Average SLA Response</span>
              <strong className="text-xs font-bold text-emerald-400">&lt; 15 Minutes</strong>
            </div>
          </div>
        </div>
      </div>

      {actionNotice && (
        <div className="mb-6 p-4 rounded-xl bg-[#1877f2]/15 border border-[#38bdf8]/40 text-[#73dfff] text-xs font-semibold flex items-center gap-2">
          <GoogleIcon name="info" size={18} color="#73dfff" />
          <span>{actionNotice}</span>
        </div>
      )}

      {/* Main Workspace Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: My Tickets List */}
        <div className="lg:col-span-1 space-y-3">
          <div className="flex items-center justify-between pb-2">
            <h2 className="text-sm font-bold text-white flex items-center gap-2">
              <GoogleIcon name="inbox" size={18} color="#38bdf8" />
              <span>My Requests ({tickets.length})</span>
            </h2>
            <button
              type="button"
              onClick={() => void loadData()}
              className="text-xs text-[#73dfff] hover:underline flex items-center gap-1 cursor-pointer"
            >
              <GoogleIcon name="refresh" size={14} />
              <span>Refresh</span>
            </button>
          </div>

          {loading ? (
            <div className="p-8 text-center text-xs text-[#8fa3c7] rounded-2xl bg-[#061228]/80 border border-[#258bff]/25">
              <div className="w-6 h-6 border-2 border-[#1877f2] border-t-transparent rounded-full animate-spin mx-auto mb-2" />
              <span>Loading tickets...</span>
            </div>
          ) : tickets.length === 0 ? (
            <div className="p-8 text-center rounded-2xl bg-[#061228]/80 border border-[#258bff]/25 text-xs text-[#8fa3c7]">
              <GoogleIcon name="check_circle" size={32} color="#4ade80" className="mx-auto mb-2 opacity-80" />
              <p className="font-semibold text-white">No active disputes or inquiries</p>
              <p className="mt-1">Everything looks smooth! Use &quot;+ Raise New Ticket&quot; if you need any assistance.</p>
            </div>
          ) : (
            tickets.map((t) => (
              <div
                key={t.ticketId}
                onClick={() => void viewTicket(t.ticketId)}
                className={`p-4 rounded-xl border transition cursor-pointer ${
                  selectedTicket?.ticketId === t.ticketId
                    ? "bg-[#0c224a] border-[#38bdf8] shadow-lg shadow-[#1877f2]/20"
                    : "bg-[#061228]/80 border-[#258bff]/25 hover:border-[#38bdf8]/60 hover:bg-[#0a1b38]"
                }`}
              >
                <div className="flex items-center justify-between mb-1.5">
                  <span className="font-mono text-xs text-[#73dfff] font-bold">{t.ticketReference}</span>
                  <span
                    className={`text-[10px] px-2 py-0.5 rounded-full font-bold uppercase tracking-wider ${
                      t.status === "RESOLVED"
                        ? "bg-emerald-500/20 text-emerald-300 border border-emerald-500/30"
                        : t.status === "OPEN"
                        ? "bg-blue-500/20 text-blue-300 border border-blue-500/30"
                        : "bg-amber-500/20 text-amber-300 border border-amber-500/30"
                    }`}
                  >
                    {t.status.replace("_", " ")}
                  </span>
                </div>
                <h3 className="text-xs font-bold text-white line-clamp-1">{t.subject}</h3>
                <div className="flex items-center justify-between text-[11px] text-[#8fa3c7] mt-2">
                  <span>{t.categoryDisplayName}</span>
                  <span>{new Date(t.createdAt).toLocaleDateString()}</span>
                </div>
              </div>
            ))
          )}
        </div>

        {/* Right Column: Ticket Conversation View */}
        <div className="lg:col-span-2">
          {selectedTicket ? (
            <div className="p-6 rounded-2xl bg-[#061228]/90 border border-[#258bff]/30 shadow-xl flex flex-col h-full min-h-[500px]">
              {/* Ticket Top bar */}
              <div className="pb-4 border-b border-[#258bff]/20 flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <span className="font-mono text-xs text-[#73dfff] font-bold">{selectedTicket.ticketReference}</span>
                    <span className="text-[11px] px-2.5 py-0.5 rounded-full bg-[#1877f2]/20 text-[#38bdf8] border border-[#258bff]/30 font-semibold">
                      {selectedTicket.categoryDisplayName}
                    </span>
                    <span className="text-[10px] uppercase font-bold text-[#8fa3c7] px-2 py-0.5 rounded bg-[#0a1b38] border border-white/5">
                      Priority: {selectedTicket.priority}
                    </span>
                  </div>
                  <h2 className="text-base font-bold text-white">{selectedTicket.subject}</h2>
                </div>

                <div className="flex items-center gap-2">
                  {selectedTicket.status === "RESOLVED" && (
                    <button
                      onClick={() => setShowFeedbackModal(true)}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-500/20 hover:bg-emerald-500/30 text-emerald-300 border border-emerald-500/40 text-xs font-semibold transition cursor-pointer"
                    >
                      <GoogleIcon name="star" size={15} color="#4ade80" />
                      <span>Rate Support</span>
                    </button>
                  )}
                </div>
              </div>

              {/* Message thread */}
              <div className="flex-1 py-4 space-y-3.5 overflow-y-auto max-h-[420px] pr-2">
                {selectedTicket.messages.map((m) => (
                  <div
                    key={m.messageId}
                    className={`p-4 rounded-2xl max-w-[85%] text-xs leading-relaxed ${
                      m.senderType === "STAFF"
                        ? "bg-[#0b1d3d] border border-[#258bff]/30 self-start text-slate-100 mr-auto"
                        : "bg-[#1877f2]/20 border border-[#38bdf8]/40 self-end text-white ml-auto"
                    }`}
                  >
                    <div className="flex items-center justify-between gap-3 mb-1.5 text-[10px]">
                      <span className="font-bold text-[#73dfff] flex items-center gap-1">
                        <GoogleIcon name={m.senderType === "STAFF" ? "shield_person" : "person"} size={13} />
                        <span>{m.senderName} ({m.senderType})</span>
                      </span>
                      <span className="text-[#8fa3c7]">{new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                    </div>
                    <p className="whitespace-pre-wrap text-slate-200">{m.messageBody}</p>
                  </div>
                ))}
              </div>

              {/* Reply Input */}
              {selectedTicket.status !== "CLOSED" ? (
                <form onSubmit={sendReply} className="pt-4 border-t border-[#258bff]/20 flex gap-2">
                  <input
                    type="text"
                    value={replyBody}
                    onChange={(e) => setReplyBody(e.target.value)}
                    placeholder="Type a message or provide requested ticket details..."
                    className="flex-1 px-4 py-2.5 rounded-xl bg-[#0a1834] border border-[#258bff]/30 text-white text-xs placeholder:text-[#5c7299] focus:outline-none focus:border-[#38bdf8] focus:ring-1 focus:ring-[#38bdf8]"
                  />
                  <button
                    type="submit"
                    className="inline-flex items-center gap-1.5 px-5 py-2.5 rounded-xl bg-[#1877f2] hover:bg-[#258bff] text-white font-bold text-xs transition cursor-pointer shadow-md shadow-[#1877f2]/30"
                  >
                    <span>Send Reply</span>
                    <GoogleIcon name="send" size={15} />
                  </button>
                </form>
              ) : (
                <div className="p-3 text-center text-xs text-[#8fa3c7] border-t border-[#258bff]/20">
                  This ticket has been officially closed.
                </div>
              )}
            </div>
          ) : (
            <div className="p-16 text-center rounded-2xl bg-[#061228]/80 border border-[#258bff]/25 text-[#8fa3c7] text-xs flex flex-col items-center justify-center min-h-[450px]">
              <GoogleIcon name="chat" size={44} color="#38bdf8" className="mb-3 opacity-60" />
              <p className="text-sm font-bold text-white mb-1">No ticket selected</p>
              <p className="max-w-sm text-[#8fa3c7]">
                Click any ticket from the left panel to read the full conversation or respond to the support officer.
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Create Ticket Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in">
          <div className="w-full max-w-lg p-6 rounded-2xl bg-[#06142e] border border-[#38bdf8]/40 shadow-2xl">
            <div className="flex items-center justify-between pb-3 border-b border-[#258bff]/20">
              <div className="flex items-center gap-2">
                <GoogleIcon name="support_agent" size={22} color="#73dfff" />
                <h2 className="text-base font-bold text-white">Raise a Support Ticket</h2>
              </div>
              <button
                type="button"
                onClick={() => setShowCreateModal(false)}
                className="text-[#8fa3c7] hover:text-white cursor-pointer"
              >
                <GoogleIcon name="close" size={20} />
              </button>
            </div>

            <form onSubmit={handleCreateTicket} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs text-[#8fa3c7] mb-1 font-semibold">Issue Category</label>
                <select
                  value={catId}
                  onChange={(e) => setCatId(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#0a1834] border border-[#258bff]/30 text-white text-xs focus:outline-none focus:border-[#38bdf8]"
                >
                  {categories.map((c) => (
                    <option key={c.categoryId} value={c.categoryId} className="bg-[#06142e] text-white">
                      {c.displayName} ({c.department})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs text-[#8fa3c7] mb-1 font-semibold">Subject</label>
                <input
                  type="text"
                  value={subject}
                  onChange={(e) => setSubject(e.target.value)}
                  placeholder="e.g. Payment deducted but PNR ticket not issued"
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#0a1834] border border-[#258bff]/30 text-white text-xs placeholder:text-[#5c7299] focus:outline-none focus:border-[#38bdf8]"
                  required
                />
              </div>

              <div>
                <label className="block text-xs text-[#8fa3c7] mb-1 font-semibold">Priority</label>
                <select
                  value={priority}
                  onChange={(e) => setPriority(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#0a1834] border border-[#258bff]/30 text-white text-xs focus:outline-none focus:border-[#38bdf8]"
                >
                  <option value="NORMAL" className="bg-[#06142e] text-white">NORMAL</option>
                  <option value="HIGH" className="bg-[#06142e] text-white">HIGH</option>
                  <option value="URGENT" className="bg-[#06142e] text-white">URGENT</option>
                </select>
              </div>

              <div>
                <label className="block text-xs text-[#8fa3c7] mb-1 font-semibold">Description</label>
                <textarea
                  rows={4}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Please describe the issue in detail with relevant booking reference, PNR, transaction ID or train number..."
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#0a1834] border border-[#258bff]/30 text-white text-xs placeholder:text-[#5c7299] focus:outline-none focus:border-[#38bdf8]"
                  required
                />
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t border-[#258bff]/20">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="px-4 py-2.5 rounded-xl bg-[#0a1834] hover:bg-[#0c224a] text-[#8fa3c7] hover:text-white text-xs font-semibold transition cursor-pointer border border-[#258bff]/20"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="inline-flex items-center gap-1.5 px-5 py-2.5 rounded-xl bg-gradient-to-r from-[#1877f2] to-[#1264c5] hover:from-[#258bff] hover:to-[#1877f2] text-white font-bold text-xs transition disabled:opacity-50 cursor-pointer shadow-lg shadow-[#1877f2]/30"
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
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in">
          <div className="w-full max-w-md p-6 rounded-2xl bg-[#06142e] border border-[#38bdf8]/40 shadow-2xl">
            <h2 className="text-base font-bold text-white">Rate Customer Support</h2>
            <p className="text-xs text-[#8fa3c7] mt-1">
              How satisfied are you with the resolution of ticket <span className="text-[#73dfff] font-mono">{selectedTicket?.ticketReference}</span>?
            </p>
            <form onSubmit={handleFeedback} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs text-[#8fa3c7] mb-2 font-semibold">Satisfaction Rating</label>
                <div className="flex gap-2">
                  {[1, 2, 3, 4, 5].map((star) => (
                    <button
                      type="button"
                      key={star}
                      onClick={() => setRating(star)}
                      className={`text-xl px-3.5 py-2 rounded-xl border transition cursor-pointer ${
                        rating >= star
                          ? "bg-[#1877f2]/30 border-[#38bdf8] text-[#73dfff]"
                          : "bg-[#0a1834] border-[#258bff]/25 text-[#5c7299]"
                      }`}
                    >
                      ★
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-xs text-[#8fa3c7] mb-1 font-semibold">Comments (Optional)</label>
                <textarea
                  rows={3}
                  value={feedbackComment}
                  onChange={(e) => setFeedbackComment(e.target.value)}
                  placeholder="Tell us what went well or how our officer can improve..."
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#0a1834] border border-[#258bff]/30 text-white text-xs placeholder:text-[#5c7299] focus:outline-none focus:border-[#38bdf8]"
                />
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t border-[#258bff]/20">
                <button
                  type="button"
                  onClick={() => setShowFeedbackModal(false)}
                  className="px-4 py-2.5 rounded-xl bg-[#0a1834] hover:bg-[#0c224a] text-[#8fa3c7] hover:text-white text-xs font-semibold transition cursor-pointer border border-[#258bff]/20"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2.5 rounded-xl bg-[#1877f2] hover:bg-[#258bff] text-white font-bold text-xs transition cursor-pointer shadow-md shadow-[#1877f2]/30"
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
