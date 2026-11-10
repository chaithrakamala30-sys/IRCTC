# Domain, DNS, and TLS / HTTPS Configuration

## 1. TLS & Certificate Provisioning
- **Frontend Portals (Vercel)**: Automatic Let's Encrypt TLS certificate provisioning with automatic renewal and global edge SSL termination.
- **Backend API (Render)**: Automatic TLS certificate on `*.onrender.com` with HTTPS enforced for all incoming requests.

## 2. Security Headers Enforced
- **Strict-Transport-Security (HSTS)**: `max-age=31536000; includeSubDomains; preload`
- **X-Frame-Options**: `DENY`
- **X-Content-Type-Options**: `nosniff`
- **Referrer-Policy**: `strict-origin-when-cross-origin`
- **Content-Security-Policy**: Configured to restrict frame embedding and script injection while permitting Dhruva Bank demonstration redirects.
