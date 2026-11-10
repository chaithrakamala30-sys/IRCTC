# Production Environment Configuration Guide

## 1. Backend Environment Variables (Render)
```env
SPRING_PROFILES_ACTIVE=production
SERVER_PORT=8080
DATABASE_URL=jdbc:postgresql://<db_host>:5432/<db_name>?sslmode=require
DATABASE_USERNAME=<db_user>
DATABASE_PASSWORD=<db_password>
RAILCONNECT_SECURITY_CORS_ALLOWED_ORIGINS=https://passenger-web.vercel.app,https://admin-portal.vercel.app,https://operations-portal.vercel.app
DHRUVA_BANK_BASE_URL=https://dhruva-bank.vercel.app
DHRUVA_BANK_MERCHANT_ID=RAILCONNECT_DEMO_MERCHANT
DHRUVA_BANK_WEBHOOK_SECRET=<generated_secret>
```

## 2. Frontend Environment Variables (Vercel)
```env
RAILCONNECT_API_ORIGIN=https://irctc-wb68.onrender.com
NEXT_PUBLIC_DHRUVA_BANK_URL=https://dhruva-bank.vercel.app
NEXT_PUBLIC_ENV=production
```
