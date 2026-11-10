# Phase 14: Deployment & Zero-Cost Infrastructure Guide

## 1. Zero-Cost Infrastructure Commitment
- **PostgreSQL Database Storage**: Managed entirely in the existing free Supabase tier.
- **Push Notifications**: Standard W3C Web Push using browser service workers without paid intermediaries.
- **Transactional Outbox Worker**: Operates via Spring `@Scheduled` inside the existing backend container without external paid message brokers (e.g. AWS SQS, Confluent Kafka).

## 2. Deployment Instructions
1. Apply Flyway migration `V18__create_passenger_communication_and_notification_system.sql`.
2. Build and restart `railconnect-core` container.
3. Deploy frontend static sites (`passenger-web` and `admin-portal`) to Vercel.
