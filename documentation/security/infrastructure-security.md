# Infrastructure & Container Security

## 1. Hosting Architecture
- **Zero-Cost Deployment Model**:
  - Backend API: Hosted on Render container instance.
  - Passenger Web & Admin Portal: Deployed on Vercel Edge networks.
  - Database: Cloud-hosted PostgreSQL instance with Flyway migrations.

## 2. Container Hardening
- Container runs as non-privileged non-root user.
- Minimal Linux base image minimizing package attack surface.
- Docker ports expose only internal application communication; debug and profiling tools are stripped from release images.
