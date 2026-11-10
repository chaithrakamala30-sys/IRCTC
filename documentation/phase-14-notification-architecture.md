# Phase 14: Notification Architecture Specification

## 1. Overview
RailConnect Phase 14 implements a centralized, multi-channel notification architecture designed to inform passengers and operators in real time. It adheres strictly to the ₹0 infrastructure budget by leveraging PostgreSQL as both the inbox repository and transactional outbox.

## 2. Architecture Components
- **Transactional Outbox (`notification_outbox`)**: Guarantees zero lost notification events even during service restarts.
- **In-App Notification Center (`notifications`)**: User inbox with unread tracking, category filters, and deep links.
- **Device & Subscription Registry (`notification_devices`)**: Tracks browser WebPush and optional FCM device tokens per user.
- **Multi-Channel Dispatcher (`NotificationService`)**: Evaluates user preferences and mandatory policy rules before delivering across In-App, WebPush/FCM, and future email channels.
- **Background Worker**: Executes outbox polling with exponential backoff and dead-letter queue semantics.
