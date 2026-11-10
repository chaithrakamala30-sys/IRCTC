"use client";

import { useState } from "react";

export function AdminReportsConsole() {
  const [downloading, setDownloading] = useState(false);

  const reports = [
    {
      id: "BOOKINGS",
      name: "Reservations & Bookings Report",
      description: "Comprehensive log of reservations, PNR allocations, fare amounts, and passenger details.",
    },
    {
      id: "FINANCE",
      name: "Finance & Dhruva Bank Settlement Report",
      description: "Payment transactions, reconciliation status, and demonstration refund settlement records.",
    },
    {
      id: "SUPPORT",
      name: "Customer Support & SLA Metrics",
      description: "Logged support tickets, escalation counts, resolution duration, and customer CSAT feedback.",
    },
    {
      id: "PASSENGERS",
      name: "Passenger Accounts & Security Log",
      description: "User registrations, verification state, account suspension records, and audit events.",
    },
  ];

  const handleDownload = (id: string) => {
    setDownloading(true);
    window.open(`/api/v1/admin/reports/export?reportType=${id}`, "_blank");
    setTimeout(() => setDownloading(false), 1000);
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h2 className="text-xl font-bold text-slate-900">Reports & Analytics Centre</h2>
        <p className="text-sm text-slate-500">
          Generate live administrative, financial, operational, and customer support reports in CSV format.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {reports.map((r) => (
          <div key={r.id} className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm flex flex-col justify-between">
            <div className="space-y-2">
              <span className="text-[10px] font-bold uppercase tracking-wider text-indigo-600">{r.id}</span>
              <h3 className="font-bold text-slate-900 text-base">{r.name}</h3>
              <p className="text-xs text-slate-500 leading-relaxed">{r.description}</p>
            </div>
            <div className="mt-6 pt-4 border-t border-slate-100 flex items-center justify-between">
              <span className="text-[11px] font-medium text-emerald-600 bg-emerald-50 px-2.5 py-1 rounded-full">
                Format: .CSV / Excel Compatible
              </span>
              <button
                onClick={() => handleDownload(r.id)}
                disabled={downloading}
                className="rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white shadow hover:bg-indigo-700 disabled:opacity-50"
              >
                Download CSV
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

