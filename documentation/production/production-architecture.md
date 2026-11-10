# Production Architecture & Deployment Topology

## 1. System Overview
RailConnect operates three distinct, decoupled frontend applications integrated with a shared secure Spring Boot backend and PostgreSQL database.

## 2. Live Deployed Endpoints
- **Core Backend API**: Hosted on Render at `https://irctc-wb68.onrender.com`
  - Spring Boot 3 on Java 21 container
  - REST API prefix: `/api/v1/*`
  - HikariCP pool sized to 15 connections
  - Flyway migrations `V1` through `V21`
- **Passenger Portal**: Deployed on Vercel
  - Public interface for train search, fare quotes, seat reservation, ticket downloads, notifications, and customer support.
- **Administration Portal**: Deployed on Vercel
  - Restricted management console for staff provisioning, Maker-Checker four-eye approvals, financial reconciliation, and operational audit logs.
- **Operations Portal / Console**: Deployed on Vercel
  - Role-gated interface for train run dispatch, delay tracking, platform assignments, and operational disruption announcements.
- **Demonstration Payment Platform**: Dhruva Bank (`https://dhruva-bank.vercel.app`)
  - Demonstration banking simulator for non-monetary checkout, webhooks, and refund reconciliation.
