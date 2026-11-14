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
      <div className="exec-dash-loading">
        <div className="exec-dash-spinner" />
        <p>Loading executive telemetry and system analytics...</p>
      </div>
    );
  }

  if (error || !metrics) {
    return (
      <div className="exec-dash-error">
        <div className="exec-dash-error-icon">⚠️</div>
        <h3>Failed to load executive metrics</h3>
        <p>{error || "Unable to reach server telemetry stream."}</p>
        <button
          onClick={fetchDashboard}
          className="exec-dash-retry-btn"
          type="button"
        >
          Retry Telemetry
        </button>
      </div>
    );
  }

  const { platform, finance, service } = metrics;
  const totalVolume = Number(finance.totalPaymentVolumeInr || 0);
  const totalRefund = Number(finance.totalRefundVolumeInr || 0);

  return (
    <div className="exec-dashboard">
      {/* Header Banner & Action Bar */}
      <div className="exec-dash-banner">
        <div className="exec-dash-banner-info">
          <div className="exec-dash-badge">
            <span className="exec-live-dot" />
            <span>LIVE TELEMETRY</span>
          </div>
          <h2>Executive Command Center</h2>
          <p className="exec-dash-subtitle">
            System overview, financial volume reconciliation, and real-time network health across all divisions.
          </p>
        </div>
        <div className="exec-dash-actions">
          <div className="exec-demo-tag">
            <span className="demo-chip">DEMO PLATFORM</span>
            <span className="demo-text">Dhruva Bank simulated transactions</span>
          </div>
          <button
            onClick={fetchDashboard}
            className="exec-refresh-btn"
            type="button"
            title="Refresh Metrics"
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/>
            </svg>
            Refresh
          </button>
        </div>
      </div>

      {/* Primary KPI Overview Cards */}
      <div className="exec-section">
        <div className="exec-section-header">
          <span className="exec-section-icon">🚆</span>
          <div>
            <h3>Platform & Reservation Velocity</h3>
            <p>Passenger account health, ticketing reservations, and crew profile status</p>
          </div>
        </div>

        <div className="exec-grid-4">
          {/* Card 1: Registered Passengers */}
          <div className="exec-card">
            <div className="exec-card-head">
              <span className="exec-card-label">Registered Passengers</span>
              <span className="exec-card-chip chip-blue">Identity</span>
            </div>
            <div className="exec-card-val">
              {platform.totalPassengers.toLocaleString()}
            </div>
            <div className="exec-card-footer">
              <div className="status-pill status-pill--success">
                <span className="dot dot-success" />
                <span>{platform.activePassengers} Active</span>
              </div>
              <div className="status-pill status-pill--danger">
                <span className="dot dot-danger" />
                <span>{platform.suspendedPassengers} Suspended</span>
              </div>
            </div>
          </div>

          {/* Card 2: Total Reservations */}
          <div className="exec-card">
            <div className="exec-card-head">
              <span className="exec-card-label">Total Reservations</span>
              <span className="exec-card-chip chip-cyan">Bookings</span>
            </div>
            <div className="exec-card-val cyan-glow">
              {platform.totalBookings.toLocaleString()}
            </div>
            <div className="exec-card-footer">
              <div className="status-pill status-pill--success">
                <span className="dot dot-success" />
                <span>{platform.confirmedBookings} Confirmed</span>
              </div>
              <div className="status-pill status-pill--warning">
                <span className="dot dot-warning" />
                <span>{platform.racBookings + platform.waitlistBookings} RAC/WL</span>
              </div>
            </div>
          </div>

          {/* Card 3: Active Staff */}
          <div className="exec-card">
            <div className="exec-card-head">
              <span className="exec-card-label">Active Staff Profiles</span>
              <span className="exec-card-chip chip-purple">Personnel</span>
            </div>
            <div className="exec-card-val purple-glow">
              {platform.totalStaff.toLocaleString()}
            </div>
            <div className="exec-card-footer">
              <span className="exec-subtext">Across 6 operational railway divisions</span>
            </div>
          </div>

          {/* Card 4: Cancellations */}
          <div className="exec-card">
            <div className="exec-card-head">
              <span className="exec-card-label">Cancellations & Disputes</span>
              <span className="exec-card-chip chip-rose">Refunds</span>
            </div>
            <div className="exec-card-val rose-glow">
              {platform.cancelledBookings.toLocaleString()}
            </div>
            <div className="exec-card-footer">
              <span className="exec-subtext">Automated refund workflow pipeline</span>
            </div>
          </div>
        </div>
      </div>

      {/* Finance & Reconciliation Cards */}
      <div className="exec-section">
        <div className="exec-section-header">
          <span className="exec-section-icon">💳</span>
          <div>
            <h3>Financial Volume & Gateway Reconciliation</h3>
            <p>Simulated gateway liquidity, automated reconciliation audit and settlement state</p>
          </div>
        </div>

        <div className="exec-grid-3">
          {/* Finance Card 1 */}
          <div className="exec-card exec-card--hero">
            <div className="exec-card-head">
              <div>
                <span className="exec-card-label">Demo Gross Revenue</span>
                <div className="exec-card-hero-amt">₹{totalVolume.toLocaleString()}</div>
              </div>
              <div className="status-pill status-pill--success">
                <span className="dot dot-success" />
                <span>Live Reconciled</span>
              </div>
            </div>
            <div className="exec-card-breakdown">
              <div className="breakdown-item">
                <span className="breakdown-label">Succeeded</span>
                <span className="breakdown-val text-success">{finance.successfulPayments}</span>
              </div>
              <div className="breakdown-divider" />
              <div className="breakdown-item">
                <span className="breakdown-label">Failed</span>
                <span className="breakdown-val text-danger">{finance.failedPayments}</span>
              </div>
              <div className="breakdown-divider" />
              <div className="breakdown-item">
                <span className="breakdown-label">Pending</span>
                <span className="breakdown-val text-warning">{finance.pendingPayments}</span>
              </div>
            </div>
          </div>

          {/* Finance Card 2 */}
          <div className="exec-card exec-card--hero">
            <div className="exec-card-head">
              <div>
                <span className="exec-card-label">Demo Refund Outflow</span>
                <div className="exec-card-hero-amt text-indigo">₹{totalRefund.toLocaleString()}</div>
              </div>
              <div className="status-pill status-pill--purple">
                <span>Dhruva Bank</span>
              </div>
            </div>
            <div className="exec-card-breakdown">
              <div className="breakdown-item">
                <span className="breakdown-label">Settled Refunds</span>
                <span className="breakdown-val text-success">{finance.completedRefunds}</span>
              </div>
              <div className="breakdown-divider" />
              <div className="breakdown-item">
                <span className="breakdown-label">Pending Review</span>
                <span className="breakdown-val text-warning">{finance.pendingRefunds}</span>
              </div>
            </div>
          </div>

          {/* Finance Card 3: Gateway Health */}
          <div className="exec-card exec-card--hero">
            <div className="exec-card-head">
              <span className="exec-card-label">Dhruva Gateway Connectivity</span>
              <div className="status-pill status-pill--success">
                <span className="dot dot-success animate-pulse" />
                <span>ONLINE</span>
              </div>
            </div>
            <div className="exec-gateway-status">
              <div className="gateway-indicator">
                <span className="gateway-dot" />
                <span className="gateway-status-text">{finance.gatewayStatus || "OPERATIONAL"}</span>
              </div>
              <p className="gateway-desc">
                Direct merchant webhook listener active with automated settlement dispatch.
              </p>
              <div className="reconciliation-badge">
                Reconciliation State: <strong>{finance.reconciliationStatus || "SYNCHRONIZED"}</strong>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Operational Oversight & Support Health */}
      <div className="exec-section">
        <div className="exec-section-header">
          <span className="exec-section-icon">🚦</span>
          <div>
            <h3>Operations & Incident Oversight</h3>
            <p>Live train schedules, disruption alerts, and customer dispute resolution queues</p>
          </div>
        </div>

        <div className="exec-grid-4">
          <div className="exec-card">
            <div className="exec-card-head">
              <span className="exec-card-label">Scheduled Services Today</span>
              <span className="exec-card-chip chip-blue">Runs</span>
            </div>
            <div className="exec-card-val text-cyan">
              {service.activeTrainRuns}
            </div>
            <div className="exec-card-footer">
              <span className="exec-subtext">
                {service.delayedTrainRuns > 0 ? (
                  <span className="text-warning">⚠️ {service.delayedTrainRuns} running delayed</span>
                ) : (
                  <span className="text-success">✓ 100% On Time</span>
                )}
              </span>
            </div>
          </div>

          <div className="exec-card">
            <div className="exec-card-head">
              <span className="exec-card-label">Active Disruptions</span>
              <span className="exec-card-chip chip-amber">OCC</span>
            </div>
            <div className="exec-card-val text-warning">
              {service.activeDisruptions}
            </div>
            <div className="exec-card-footer">
              <span className="exec-subtext">Operations Control Centre monitoring</span>
            </div>
          </div>

          <div className="exec-card">
            <div className="exec-card-head">
              <span className="exec-card-label">Open Support Tickets</span>
              <span className="exec-card-chip chip-purple">Support</span>
            </div>
            <div className="exec-card-val text-purple">
              {service.openSupportTickets}
            </div>
            <div className="exec-card-footer">
              <span className="exec-subtext">Triage queue managed by customer desk</span>
            </div>
          </div>

          <div className="exec-card">
            <div className="exec-card-head">
              <span className="exec-card-label">Critical Incidents (P1)</span>
              <span className="exec-card-chip chip-rose">Urgent</span>
            </div>
            <div className="exec-card-val text-rose">
              {service.criticalSupportIncidents}
            </div>
            <div className="exec-card-footer">
              <span className="exec-subtext">
                {service.criticalSupportIncidents === 0 ? "✓ No critical escalations" : "Requires immediate action"}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
