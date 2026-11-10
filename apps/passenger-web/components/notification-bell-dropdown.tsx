"use client";

import { useEffect, useState, useRef } from "react";
import Link from "next/link";
import { apiFetch } from "../lib/api";

type InAppNotification = {
  id: string;
  category: string;
  title: string;
  message: string;
  priority: string;
  isRead: boolean;
  createdAt: string;
  deepLink?: string;
};

export function NotificationBellDropdown() {
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [notifications, setNotifications] = useState<InAppNotification[]>([]);
  const [isOpen, setIsOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const fetchUnread = async () => {
    try {
      const res = await apiFetch<{ unreadCount: number }>("/api/v1/notifications/unread-count");
      if (res.data) setUnreadCount(res.data.unreadCount);
    } catch {
      // Ignored for unauthenticated or offline
    }
  };

  const loadNotifications = async () => {
    setLoading(true);
    try {
      const res = await apiFetch<InAppNotification[]>("/api/v1/notifications?limit=5");
      if (res.data) setNotifications(res.data);
    } catch {
      // Ignored
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchUnread();
    const interval = setInterval(() => {
      void fetchUnread();
    }, 20000);
    return () => clearInterval(interval);
  }, []);

  const toggleDropdown = () => {
    if (!isOpen) {
      void loadNotifications();
      void fetchUnread();
    }
    setIsOpen(!isOpen);
  };

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [isOpen]);

  const markAllRead = async () => {
    try {
      await apiFetch("/api/v1/notifications/read-all", { method: "PATCH" });
      setUnreadCount(0);
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
    } catch {
      // Ignored
    }
  };

  return (
    <div className="relative inline-block text-left" ref={dropdownRef}>
      <button
        onClick={toggleDropdown}
        className="relative p-2 rounded-full text-slate-200 hover:text-white hover:bg-slate-800/60 transition focus:outline-none focus:ring-2 focus:ring-amber-400"
        aria-label="View notifications"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          className="w-6 h-6"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.75}
            d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
          />
        </svg>
        {unreadCount > 0 && (
          <span className="absolute top-1 right-1 flex items-center justify-center min-w-[18px] h-[18px] px-1 text-[11px] font-bold text-white bg-rose-500 rounded-full shadow">
            {unreadCount > 99 ? "99+" : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div className="absolute right-0 mt-2 w-80 sm:w-96 rounded-2xl bg-slate-900/95 backdrop-blur-xl border border-slate-700/60 shadow-2xl z-50 overflow-hidden animate-in fade-in zoom-in-95 duration-150">
          <div className="px-4 py-3 border-b border-slate-800 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-sm font-semibold text-slate-100">Notifications</span>
              {unreadCount > 0 && (
                <span className="text-xs px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-300 font-medium border border-amber-500/30">
                  {unreadCount} new
                </span>
              )}
            </div>
            {unreadCount > 0 && (
              <button
                onClick={markAllRead}
                className="text-xs text-slate-400 hover:text-amber-400 transition"
              >
                Mark all read
              </button>
            )}
          </div>

          <div className="max-h-80 overflow-y-auto divide-y divide-slate-800/60">
            {loading ? (
              <div className="p-6 text-center text-slate-400 text-xs">Loading alerts...</div>
            ) : notifications.length === 0 ? (
              <div className="p-8 text-center text-slate-400 text-xs">
                No recent notifications. You&apos;re all caught up!
              </div>
            ) : (
              notifications.map((item) => (
                <Link
                  key={item.id}
                  href={item.deepLink || "/notifications"}
                  onClick={() => setIsOpen(false)}
                  className={`block px-4 py-3 hover:bg-slate-800/50 transition ${
                    !item.isRead ? "bg-slate-800/20" : ""
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <span className="text-xs font-semibold text-slate-200 line-clamp-1">
                      {item.title}
                    </span>
                    <span className="text-[10px] text-slate-500 whitespace-nowrap">
                      {new Date(item.createdAt).toLocaleTimeString([], {
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400 mt-1 line-clamp-2 leading-relaxed">
                    {item.message}
                  </p>
                </Link>
              ))
            )}
          </div>

          <div className="p-2 border-t border-slate-800 bg-slate-900/80 text-center">
            <Link
              href="/notifications"
              onClick={() => setIsOpen(false)}
              className="text-xs font-medium text-amber-400 hover:text-amber-300 block py-1.5 transition"
            >
              View all notifications & preferences &rarr;
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}
