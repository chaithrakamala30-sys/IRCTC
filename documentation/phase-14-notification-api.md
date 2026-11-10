# Phase 14: Notification API Reference

## Passenger Endpoints
- `GET /api/v1/notifications`: List passenger in-app notifications with filters (`category`, `unreadOnly`, pagination).
- `GET /api/v1/notifications/unread-count`: Returns the current count of unread notifications.
- `PATCH /api/v1/notifications/{id}/read`: Mark notification as read.
- `PATCH /api/v1/notifications/read-all`: Mark all notifications as read.
- `DELETE /api/v1/notifications/{id}`: Delete an individual notification.
- `GET /api/v1/notifications/preferences`: Get communication channel and topic preferences.
- `PUT /api/v1/notifications/preferences`: Update preferences.
- `POST /api/v1/notifications/devices`: Register browser or mobile device token.
- `DELETE /api/v1/notifications/devices/{token}`: Deregister device token.
- `POST /api/v1/notifications/test`: Trigger a verified test notification.

## Admin Endpoints
- `GET /api/v1/admin/notifications/statistics`: Outbox and delivery health metrics.
- `GET /api/v1/admin/notifications/templates`: List active templates.

## Operations Endpoints
- `POST /api/v1/operations/announcements`: Broadcast or target operational announcements to passengers on affected train runs.
