# Dependency & Software Supply Chain Security

## 1. Automated Scanners & Gates
- **NPM Audit**: Scans `apps/passenger-web` and `apps/admin-portal` for known vulnerabilities in third-party packages. High and critical findings block merges.
- **Gradle Dependency Check**: Analyzes Spring Boot libraries against the National Vulnerability Database (NVD).
- **Gitleaks**: Scans commits and pull requests for exposed secrets, API keys, or private certificates.

## 2. Supply Chain Policies
- Critical packages are pinned with exact semantic versions.
- Lockfiles (`package-lock.json`) are committed to ensure deterministic reproducible builds.

