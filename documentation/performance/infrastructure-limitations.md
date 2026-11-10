# Zero-Cost Infrastructure Validation & Limitations

## 1. Hosting Tier Audit
- **Backend (Render Free Tier)**: Spins down after 15 minutes of inactivity; cold-start latency ~30–45s.
- **Frontend (Vercel Edge Network)**: Edge cached static assets with instant global CDN resolution.
- **Database (Cloud PostgreSQL Free Tier)**: Restricted to 20 concurrent connections and 500 MB storage cap.

## 2. Safe Operational Thresholds
- To maintain ₹0 cost indefinitely, synthetic load testing is executed strictly in local development or isolated container environments, never exhausting free-tier quotas on public deployments.
