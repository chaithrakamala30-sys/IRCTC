# Phase 14: Push Notification & Service Worker Setup

## 1. Browser Web Push Integration
RailConnect uses the standard Web Push API with a service worker (`/sw-push.js`).
1. **Explicit Permission**: Prompted only on deliberate user action from the Notification Preferences screen or Notification Center.
2. **Device Registration**: The generated token/endpoint is registered to `notification_devices` with user association.
3. **Multi-Device Support**: Multiple devices or browsers per account can receive notifications independently.
4. **Deep-Link Navigation**: Clicking on the notification automatically brings the user to the destination (`/tickets`, `/notifications`, etc.).
