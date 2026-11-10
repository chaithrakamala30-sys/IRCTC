# Rollback & Forward-Recovery Procedure

## 1. Application Rollback
- **Frontend (Vercel)**: One-click instant deployment rollback via Vercel deployment history to the previous immutable build SHA.
- **Backend (Render)**: Redeploy previous container image tag from Render release history (< 3 minutes).

## 2. Database Recovery Strategy
- Forward recovery via corrective Flyway migrations is preferred over destructive rollbacks to protect existing production transaction history.

