# Phase 14: Firebase Cloud Messaging Configuration Guide

## 1. Zero-Cost & Optional Status
RailConnect provides optional Firebase Cloud Messaging (FCM) integration. If FCM is not enabled, the application functions autonomously using internal in-app notifications and standards-compliant browser push without claiming mock deliveries are real.

## 2. Setup Instructions (Optional)
To activate FCM:
1. Create a Firebase project in the free Spark tier.
2. Obtain `firebase-messaging-sw.js` config keys and service account JSON for backend dispatch.
3. Supply `FIREBASE_PROJECT_ID` and credentials in environment variables.
4. The system validates registration tokens and manages device rotations.
