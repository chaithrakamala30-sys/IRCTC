# Frontend Production Deployment Runbook

## 1. Multi-Portal Setup on Vercel
Each frontend portal is deployed as an independent Next.js application on Vercel with dedicated environment variables pointing to `https://irctc-wb68.onrender.com`:
1. **Passenger Web** (`apps/passenger-web`): Configured with Static Site Generation (SSG) for public routes and client-side authenticated state.
2. **Admin Portal** (`apps/admin-portal`): Secured console supporting Maker-Checker governance, staff profiles, and daily reconciliation.
3. **Operations Portal** (`apps/operations-portal`): Internal train run and disruption dashboard.

## 2. Static Asset Caching & Core Web Vitals
- Initial JS payload: < 115 kB per portal.
- Static assets cached globally at the edge with immutable Cache-Control headers.

