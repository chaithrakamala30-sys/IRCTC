# Cloud & Hosting Provider Selection

## 1. Selected Providers
- **Frontend Hosting**: Vercel (Edge Network)
  - Zero-cost deployment tier for Next.js applications (`passenger-web`, `admin-portal`, `operations-portal`).
  - Automatic HTTPS, global CDN asset caching, and zero maintenance overhead.
- **Backend Hosting**: Render (Web Service Free Tier)
  - Containerized deployment of `railconnect-core` Docker image.
  - Native environment variable management and TLS termination.
- **Database**: Cloud PostgreSQL (Supabase / Render Managed Free Tier)
  - Secure TLS connection (`sslmode=require`), automated schema migration via Flyway.
- **Payment Simulator**: Vercel-hosted Dhruva Bank (`https://dhruva-bank.vercel.app`).
