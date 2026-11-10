# CI/CD & Automated Pipeline Specification

## 1. Pipeline Stages (GitHub Actions)
- **Quality Gates**:
  - `backend.yml`: Compiles Java 21 Spring Boot app, runs unit tests, verifies Flyway migrations.
  - `frontend.yml`: Runs TypeScript typechecks and Next.js production builds.
  - `security.yml`: Gitleaks secret scanning and npm dependency vulnerability scans.
- **Automated Deployment**:
  - Pushes to `main` branch trigger automated zero-downtime deployments to Vercel and Render.
