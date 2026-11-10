"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { apiFetch } from "../lib/api";

type InAppNotification = {
  id: string;
  category: string;
  eventType: string;
  title: string;
  message: string;
  priority: string;
  isRead: boolean;
  createdAt: string;
  deepLink?: string;
};

type NotificationPreferences = {
  inAppEnabled: boolean;
  pushEnabled: boolean;
  emailEnabled: boolean;
  bookingAlerts: boolean;
  paymentAlerts: boolean;
  ticketAlerts: boolean;
  reservationAlerts: boolean;
  operationalAlerts: boolean;
  announcementAlerts: boolean;
};

const CATEGORIES = [
  "ALL",
  "BOOKING",
  "PAYMENT",
  "TICKET",
  "RESERVATION",
  "WAITLIST",
  "RAC",
  "TRAIN_OPERATION",
  "PLATFORM_CHANGE",
  "ANNOUNCEMENT",
];

export function NotificationCenterView() {
  const [activeTab, setActiveTab] = useState<"inbox" | "preferences">("inbox");
  const [category, setCategory] = useState("ALL");
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [notifications, setNotifications] = useState<InAppNotification[]>([]);
  const [preferences, setPreferences] = useState<NotificationPreferences | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionNotice, setActionNotice] = useState("");
  const [pushStatus, setPushStatus] = useState<string>("Checking...");

  const loadNotifications = useCallback(async () => {
    setLoading(true);
    try {
      const q = new URLSearchParams();
      if (category !== "ALL") q.set("category", category);
      if (unreadOnly) q.set("unreadOnly", "true");
      const res = await apiFetch<InAppNotification[]>(`/api/v1/notifications?${q.toString()}`);
      setNotifications(res.data ?? []);
    } catch {
      // Ignored
    } finally {
      setLoading(false);
    }
  }, [category, unreadOnly]);

  const loadPreferences = async () => {
    try {
      const res = await apiFetch<NotificationPreferences>("/api/v1/notifications/preferences");
      if (res.data) setPreferences(res.data);
    } catch {
      // Ignored
    }
  };

  useEffect(() => {
    void loadNotifications();
  }, [loadNotifications]);

  useEffect(() => {
    void loadPreferences();
    if (typeof window !== "undefined" && "Notification" in window) {
      setPushStatus(Notification.permission);
    } else {
      setPushStatus("unsupported");
    }
  }, []);

  const markAsRead = async (id: string) => {
    try {
      await apiFetch(`/api/v1/notifications/${id}/read`, { method: "PATCH" });
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, isRead: true } : n))
      );
    } catch {
      // Ignored
    }
  };

  const markAllRead = async () => {
    try {
      await apiFetch("/api/v1/notifications/read-all", { method: "PATCH" });
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
      setActionNotice("All notifications marked as read.");
      setTimeout(() => setActionNotice(""), 3000);
    } catch {
      // Ignored
    }
  };

  const deleteNotification = async (id: string) => {
    try {
      await apiFetch(`/api/v1/notifications/${id}`, { method: "DELETE" });
      setNotifications((prev) => prev.filter((n) => n.id !== id));
    } catch {
      // Ignored
    }
  };

  const requestPushPermission = async () => {
    if (typeof window === "undefined" || !("Notification" in window)) {
      setActionNotice("Browser notifications are not supported by this browser.");
      return;
    }
    try {
      const perm = await Notification.requestPermission();
      setPushStatus(perm);
      if (perm === "granted") {
        let registrationToken = `web-push-${Date.now()}`;

        // If WebPush service worker or FCM VAPID is configured
        const vapidKey = process.env.NEXT_PUBLIC_FIREBASE_VAPID_KEY;
        if ("serviceWorker" in navigator) {
          try {
            const registration = await navigator.serviceWorker.register("/sw-push.js");
            await navigator.serviceWorker.ready;
            
            // If VAPID key is provided, subscribe with pushManager
            if (vapidKey && registration.pushManager) {
              const sub = await registration.pushManager.subscribe({
                userVisibleOnly: true,
                applicationServerKey: vapidKey,
              });
              registrationToken = JSON.stringify(sub);
            }
          } catch (swErr) {
            console.warn("Service worker registration fallback:", swErr);
          }
        }

        // Register token with RailConnect backend
        await apiFetch("/api/v1/notifications/devices", {
          method: "POST",
          body: JSON.stringify({
            deviceToken: registrationToken,
            platform: "WEB",
            metadata: { userAgent: navigator.userAgent },
          }),
        });
        setActionNotice("Browser push notifications successfully enabled and registered!");
      } else {
        setActionNotice("Browser push notification permission was not granted.");
      }
    } catch {
      setActionNotice("Failed to request push notification permission.");
    }
  };

  const sendTestAlert = async () => {
    try {
      await apiFetch("/api/v1/notifications/test", {
        method: "POST",
        body: JSON.stringify({
          category: "SYSTEM",
          title: "RailConnect Test Notification",
          message: "Real-time communication test event dispatched successfully.",
        }),
      });
      setActionNotice("Test notification dispatched! Refreshing inbox...");
      setTimeout(() => {
        void loadNotifications();
        setActionNotice("");
      }, 1500);
    } catch {
      setActionNotice("Test dispatch failed.");
    }
  };

  const updatePreferenceToggle = async (key: keyof NotificationPreferences) => {
    if (!preferences) return;
    const updated = { ...preferences, [key]: !preferences[key] };
    setPreferences(updated);
    try {
      await apiFetch("/api/v1/notifications/preferences", {
        method: "PUT",
        body: JSON.stringify(updated),
      });
      setActionNotice("Preferences updated successfully.");
      setTimeout(() => setActionNotice(""), 2500);
    } catch {
      setActionNotice("Failed to save preferences.");
    }
  };

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-6 border-b border-slate-700/60 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">Notification Center</h1>
          <p className="text-sm text-slate-400 mt-1">
            Stay updated with your bookings, train delays, platform updates and refunds.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setActiveTab("inbox")}
            className={`px-4 py-2 text-sm font-medium rounded-xl transition ${
              activeTab === "inbox"
                ? "bg-amber-500 text-slate-950 font-semibold shadow-lg shadow-amber-500/20"
                : "bg-slate-800 text-slate-300 hover:bg-slate-700"
            }`}
          >
            Inbox
          </button>
          <button
            onClick={() => setActiveTab("preferences")}
            className={`px-4 py-2 text-sm font-medium rounded-xl transition ${
              activeTab === "preferences"
                ? "bg-amber-500 text-slate-950 font-semibold shadow-lg shadow-amber-500/20"
                : "bg-slate-800 text-slate-300 hover:bg-slate-700"
            }`}
          >
            Preferences & Push
          </button>
        </div>
      </div>

      {actionNotice && (
        <div className="mt-4 p-3 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-300 text-sm animate-in fade-in">
          {actionNotice}
        </div>
      )}

      {activeTab === "inbox" ? (
        <div className="mt-6">
          {/* Filters & Actions */}
          <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
            <div className="flex flex-wrap gap-1.5">
              {CATEGORIES.map((cat) => (
                <button
                  key={cat}
                  onClick={() => setCategory(cat)}
                  className={`text-xs px-3 py-1.5 rounded-lg border transition ${
                    category === cat
                      ? "bg-slate-700 border-amber-400 text-amber-300 font-semibold"
                      : "bg-slate-900/60 border-slate-800 text-slate-400 hover:border-slate-700 hover:text-slate-200"
                  }`}
                >
                  {cat.replace("_", " ")}
                </button>
              ))}
            </div>

            <div className="flex items-center gap-3">
              <label className="flex items-center gap-2 text-xs text-slate-300 cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={unreadOnly}
                  onChange={(e) => setUnreadOnly(e.target.checked)}
                  className="rounded border-slate-700 text-amber-500 focus:ring-amber-400 bg-slate-900"
                />
                Unread only
              </label>
              <button
                onClick={markAllRead}
                className="text-xs px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg border border-slate-700 transition"
              >
                Mark all read
              </button>
            </div>
          </div>

          {/* List */}
          {loading ? (
            <div className="p-12 text-center text-slate-400">Loading notifications...</div>
          ) : notifications.length === 0 ? (
            <div className="p-16 text-center rounded-2xl bg-slate-900/40 border border-slate-800/80">
              <p className="text-base text-slate-300 font-medium">No notifications found.</p>
              <p className="text-xs text-slate-500 mt-1">
                You&apos;re all caught up with your journeys and updates.
              </p>
              <button
                onClick={sendTestAlert}
                className="mt-4 text-xs px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-amber-400 border border-slate-700 transition"
              >
                Trigger a Test Notification
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              {notifications.map((n) => (
                <div
                  key={n.id}
                  className={`p-4 rounded-2xl border transition flex flex-col sm:flex-row sm:items-center justify-between gap-4 ${
                    !n.isRead
                      ? "bg-slate-800/40 border-slate-700/80 shadow-md"
                      : "bg-slate-900/30 border-slate-800/50 opacity-80 hover:opacity-100"
                  }`}
                >
                  <div className="flex-1">
                    <div className="flex items-center gap-2.5">
                      <span className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded bg-slate-800 text-amber-400 border border-slate-700">
                        {n.category.replace("_", " ")}
                      </span>
                      {n.priority === "URGENT" && (
                        <span className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded bg-rose-500/20 text-rose-300 border border-rose-500/40">
                          Urgent
                        </span>
                      )}
                      <span className="text-xs text-slate-400">
                        {new Date(n.createdAt).toLocaleString()}
                      </span>
                      {!n.isRead && (
                        <span className="w-2 h-2 rounded-full bg-amber-400 inline-block" />
                      )}
                    </div>
                    <h3 className="text-sm font-semibold text-slate-100 mt-2">{n.title}</h3>
                    <p className="text-xs text-slate-300 mt-1 leading-relaxed">{n.message}</p>
                  </div>

                  <div className="flex items-center gap-2 self-end sm:self-center">
                    {n.deepLink && (
                      <Link
                        href={n.deepLink}
                        className="text-xs px-3 py-1.5 rounded-lg bg-amber-500/20 hover:bg-amber-500/30 text-amber-300 border border-amber-500/40 font-medium transition"
                      >
                        View &rarr;
                      </Link>
                    )}
                    {!n.isRead && (
                      <button
                        onClick={() => markAsRead(n.id)}
                        className="text-xs px-2.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 transition"
                      >
                        Mark read
                      </button>
                    )}
                    <button
                      onClick={() => deleteNotification(n.id)}
                      className="text-xs p-1.5 rounded-lg text-slate-500 hover:text-rose-400 hover:bg-slate-800 transition"
                      aria-label="Delete notification"
                    >
                      <svg
                        xmlns="http://www.w3.org/2000/svg"
                        className="w-4 h-4"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                        />
                      </svg>
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      ) : (
        /* Preferences & Push Tab */
        <div className="mt-6 space-y-6">
          {/* Push Permission Card */}
          <div className="p-6 rounded-2xl bg-slate-900/60 border border-slate-800">
            <h2 className="text-base font-semibold text-slate-100">
              Browser Web Push Notifications
            </h2>
            <p className="text-xs text-slate-400 mt-1">
              Receive real-time alerts on desktop and mobile even when the browser tab is closed.
            </p>
            <div className="mt-4 flex flex-wrap items-center justify-between gap-4 p-4 rounded-xl bg-slate-800/40 border border-slate-700/60">
              <div>
                <span className="text-xs text-slate-400">Current browser permission status:</span>
                <span
                  className={`ml-2 text-xs font-bold uppercase tracking-wider px-2 py-0.5 rounded ${
                    pushStatus === "granted"
                      ? "bg-emerald-500/20 text-emerald-300 border border-emerald-500/30"
                      : pushStatus === "denied"
                      ? "bg-rose-500/20 text-rose-300 border border-rose-500/30"
                      : "bg-slate-700 text-slate-300"
                  }`}
                >
                  {pushStatus}
                </span>
              </div>
              <div className="flex gap-2">
                {pushStatus !== "granted" ? (
                  <button
                    onClick={requestPushPermission}
                    className="text-xs px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold transition shadow-lg shadow-amber-500/20"
                  >
                    Enable Browser Push
                  </button>
                ) : (
                  <button
                    onClick={sendTestAlert}
                    className="text-xs px-4 py-2 rounded-xl bg-slate-700 hover:bg-slate-600 text-slate-100 font-semibold transition"
                  >
                    Send Test Alert
                  </button>
                )}
              </div>
            </div>
          </div>

          {/* Preferences Matrix */}
          {preferences && (
            <div className="p-6 rounded-2xl bg-slate-900/60 border border-slate-800">
              <h2 className="text-base font-semibold text-slate-100">Notification Channels & Topics</h2>
              <p className="text-xs text-slate-400 mt-1">
                Customize what alerts you receive across different delivery channels.
              </p>

              <div className="mt-6 space-y-4 divide-y divide-slate-800">
                <div className="flex items-center justify-between pt-3">
                  <div>
                    <h3 className="text-sm font-medium text-slate-200">In-App Notification Center</h3>
                    <p className="text-xs text-slate-400">Show notification badge and inbox alerts.</p>
                  </div>
                  <input
                    type="checkbox"
                    checked={preferences.inAppEnabled}
                    onChange={() => updatePreferenceToggle("inAppEnabled")}
                    className="h-4 w-4 rounded border-slate-700 text-amber-500 focus:ring-amber-400 bg-slate-900"
                  />
                </div>

                <div className="flex items-center justify-between pt-3">
                  <div>
                    <h3 className="text-sm font-medium text-slate-200">Browser Web Push</h3>
                    <p className="text-xs text-slate-400">Receive instant push notifications on your device.</p>
                  </div>
                  <input
                    type="checkbox"
                    checked={preferences.pushEnabled}
                    onChange={() => updatePreferenceToggle("pushEnabled")}
                    className="h-4 w-4 rounded border-slate-700 text-amber-500 focus:ring-amber-400 bg-slate-900"
                  />
                </div>

                <div className="flex items-center justify-between pt-3">
                  <div>
                    <h3 className="text-sm font-medium text-slate-200">Train Operation & Platform Alerts</h3>
                    <p className="text-xs text-slate-400">Delays, platform changes, and route diversions.</p>
                  </div>
                  <input
                    type="checkbox"
                    checked={preferences.operationalAlerts}
                    onChange={() => updatePreferenceToggle("operationalAlerts")}
                    className="h-4 w-4 rounded border-slate-700 text-amber-500 focus:ring-amber-400 bg-slate-900"
                  />
                </div>

                <div className="flex items-center justify-between pt-3">
                  <div>
                    <h3 className="text-sm font-medium text-slate-200">Reservation & Waitlist Upgrades</h3>
                    <p className="text-xs text-slate-400">RAC confirmations, waitlist promotions, and chart status.</p>
                  </div>
                  <input
                    type="checkbox"
                    checked={preferences.reservationAlerts}
                    onChange={() => updatePreferenceToggle("reservationAlerts")}
                    className="h-4 w-4 rounded border-slate-700 text-amber-500 focus:ring-amber-400 bg-slate-900"
                  />
                </div>

                <div className="flex items-center justify-between pt-3">
                  <div>
                    <h3 className="text-sm font-medium text-slate-200">General Railway Announcements</h3>
                    <p className="text-xs text-slate-400">Station advisories and holiday special updates.</p>
                  </div>
                  <input
                    type="checkbox"
                    checked={preferences.announcementAlerts}
                    onChange={() => updatePreferenceToggle("announcementAlerts")}
                    className="h-4 w-4 rounded border-slate-700 text-amber-500 focus:ring-amber-400 bg-slate-900"
                  />
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
