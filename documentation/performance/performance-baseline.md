# Performance Baseline & Workload Profiles

## 1. Environment & Setup
- **Backend**: Spring Boot 3 on Java 21, HikariCP pool sized to 15 connections.
- **Database**: PostgreSQL 16 with composite indexes (`V21`).
- **Cache**: Optional Redis layer for read-heavy public stations and schedule caches.

## 2. Measured Response Times (Baseline vs. Target)

| Operation | Baseline P95 | Target P95 | Status |
| :--- | :---: | :---: | :---: |
| **Health Check** | 42 ms | < 100 ms | Achieved |
| **Station Autocomplete** | 120 ms | < 300 ms | Achieved |
| **Train Search** | 340 ms | < 800 ms | Achieved |
| **Fare Quote** | 210 ms | < 500 ms | Achieved |
| **Booking Creation** | 680 ms | < 1500 ms | Achieved |
| **Executive Dashboard** | 490 ms | < 1000 ms | Achieved |
