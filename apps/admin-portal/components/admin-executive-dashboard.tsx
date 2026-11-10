"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@railconnect/api";

type DashboardMetrics = {
  platform: {
    totalPassengers: number;
    activePassengers: number;
    suspendedPassengers: number;
    totalStaff: number;
    totalBookings: number;
    confirmedBookings: number;
    racBookings: number;
    waitlistBookings: number;
    cancelledBookings: number;
  };
  finance: {
    totalPaymentVolumeInr: number;
    successfulPayments: number;
    failedPayments: number;
    pendingPayments: number;
    totalRefundVolumeInr: number;
    completedRefunds: number;
    pendingRefunds: number;
    gatewayStatus: string;
    reconciliationStatus: string;
  };
  service: {
    activeTrainRuns: number;
    delayedTrainRuns: number;
    cancelledTrainRuns: number;
    activeDisruptions: number;
    openSupportTickets: number;
    criticalSupportIncidents: number;
  };
};

export function AdminExecutiveDashboard() {
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchDashboard = async () => {
    setLoading(true);
    try {
      const response = await apiFetch<DashboardMetrics>("/api/v1/admin/dashboard");
      setMetrics(response.data);
      setError(null);
    } catch (err: unknown) {
      setError((err as Error)?.message || "Failed to load dashboard metrics");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboard();
  }, []);

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent" />
      </div>
    );
  }

  if (error || !metrics) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-center text-red-700">
        <p className="font-medium">Failed to load executive metrics</p>
        <p className="text-sm mt-1">{error}</p>
        <button
          onClick={fetchDashboard}
          className="mt-4 rounded-md bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700"
        >
          Retry
        </button>
      </div>
    );
  }

  const { platform, finance, service } = metrics;

  return (
    <div className="space-y-6">
      {/* Disclaimer Banner */}
      <div className="rounded-lg bg-amber-50 border border-amber-200 p-4 text-xs text-amber-800 flex items-center justify-between">
        <div>
          <span className="font-bold uppercase tracking-wider text-amber-900 mr-2">Demo Financials</span>
          Financial values displayed represent simulated volumes on the Dhruva Bank demonstration platform and do not constitute real money transactions.
        </div>
        <button
          onClick={fetchDashboard}
          className="rounded bg-amber-200 px-2.5 py-1 font-semibold text-amber-900 hover:bg-amber-300"
        >
          Refresh Data
        </button>
      </div>

      {/* KPI Cards: Platform Overview */}
      <div>
        <h3 className="text-sm font-semibold uppercase tracking-wider text-slate-500 mb-3">Platform Overview</h3>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs text-slate-500 font-medium">Registered Passengers</p>
            <p className="mt-2 text-2xl font-bold text-slate-900">{platform.totalPassengers.toLocaleString()}</p>
            <div className="mt-1 flex items-center text-xs text-slate-600 gap-2">
              <span className="text-emerald-600 font-semibold">{platform.activePassengers} Active</span>
              <span>•</span>
              <span className="text-rose-600 font-semibold">{platform.suspendedPassengers} Suspended</span>
            </div>
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs text-slate-500 font-medium">Total Reservations</p>
            <p className="mt-2 text-2xl font-bold text-indigo-600">{platform.totalBookings.toLocaleString()}</p>
            <div className="mt-1 flex items-center text-xs text-slate-600 gap-2">
              <span className="text-emerald-600">{platform.confirmedBookings} Confirmed</span>
              <span>•</span>
              <span className="text-amber-600">{platform.racBookings + platform.waitlistBookings} RAC/WL</span>
            </div>
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs text-slate-500 font-medium">Active Staff Profiles</p>
            <p className="mt-2 text-2xl font-bold text-slate-900">{platform.totalStaff.toLocaleString()}</p>
            <p className="mt-1 text-xs text-slate-500">Across 6 operational divisions</p>
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs text-slate-500 font-medium">Cancellations & Disputes</p>
            <p className="mt-2 text-2xl font-bold text-rose-600">{platform.cancelledBookings.toLocaleString()}</p>
            <p className="mt-1 text-xs text-slate-500">Refund workflow initiated</p>
          </div>
        </div>
      </div>

      {/* KPI Cards: Finance Overview */}
      <div>
        <h3 className="text-sm font-semibold uppercase tracking-wider text-slate-500 mb-3">Finance & Demonstration Settlement</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">Demo Gross Revenue</span>
              <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-800">Live Reconciled</span>
            </div>
            <p className="mt-3 text-3xl font-extrabold text-slate-900">
              ₹{(finance.totalPaymentVolumeInr || 0).toLocaleString()}
            </p>
            <div className="mt-2 flex items-center gap-3 text-xs text-slate-600">
              <span className="text-emerald-600 font-semibold">{finance.successfulPayments} Succeeded</span>
              <span>•</span>
              <span className="text-rose-600 font-semibold">{finance.failedPayments} Failed</span>
              <span>•</span>
              <span className="text-amber-600 font-semibold">{finance.pendingPayments} Pending</span>
            </div>
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">Demo Refund Outflow</span>
              <span className="rounded-full bg-indigo-100 px-2 py-0.5 text-xs font-medium text-indigo-800">Dhruva Bank</span>
            </div>
            <p className="mt-3 text-3xl font-extrabold text-slate-900">
              ₹{(finance.totalRefundVolumeInr || 0).toLocaleString()}
            </p>
            <div className="mt-2 flex items-center gap-3 text-xs text-slate-600">
              <span className="text-emerald-600 font-semibold">{finance.completedRefunds} Settled</span>
              <span>•</span>
              <span className="text-amber-600 font-semibold">{finance.pendingRefunds} Pending Verification</span>
            </div>
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">Dhruva Gateway Connectivity</span>
            <div className="mt-4 flex items-center gap-2">
              <div className="h-3 w-3 rounded-full bg-emerald-500 animate-pulse" />
              <p className="text-base font-bold text-slate-900">{finance.gatewayStatus}</p>
            </div>
            <p className="mt-1 text-xs text-slate-500">Merchant callback and webhook listener active</p>
          </div>
        </div>
      </div>

      {/* Service & Operational Oversight */}
      <div>
        <h3 className="text-sm font-semibold uppercase tracking-wider text-slate-500 mb-3">Operations & Support Oversight</h3>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs text-slate-500 font-medium">Scheduled Services Today</p>
            <p className="mt-2 text-2xl font-bold text-slate-900">{service.activeTrainRuns}</p>
            <p className="mt-1 text-xs text-slate-500">{service.delayedTrainRuns} running delayed</p>
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs text-slate-500 font-medium">Active Disruptions</p>
            <p className="mt-2 text-2xl font-bold text-amber-600">{service.activeDisruptions}</p>
            <p className="mt-1 text-xs text-slate-500">Operational control centre monitoring</p>
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs text-slate-500 font-medium">Open Support Tickets</p>
            <p className="mt-2 text-2xl font-bold text-indigo-600">{service.openSupportTickets}</p>
            <p className="mt-1 text-xs text-slate-500">Triage by customer support desk</p>
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs text-slate-500 font-medium">Critical Escalate Incidents</p>
            <p className="mt-2 text-2xl font-bold text-rose-600">{service.criticalSupportIncidents}</p>
            <p className="mt-1 text-xs text-slate-500">Priority 1 attention required</p>
          </div>
        </div>
      </div>
    </div>
  );
}

