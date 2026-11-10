# Advanced Reservation API Reference

This document provides endpoint contracts for Quota, Queue, Promotion, and Departure Chart operations in RailConnect.

---

## 1. Passenger Endpoints

### Get Public Quotas
- **GET** `/api/v1/quotas`
- **Response**: `200 OK`
  ```json
  [
    {
      "quotaId": "11111111-1111-1111-1111-111111111001",
      "quotaCode": "GENERAL",
      "displayName": "General Quota",
      "description": "Standard public reservation quota with open booking access.",
      "active": true
    }
  ]
  ```

### Get Live Booking Reservation Status
- **GET** `/api/v1/bookings/{bookingId}/reservation-status`
- **Response**: `200 OK`
  ```json
  {
    "bookingId": "...",
    "bookingReference": "RC-GRPYZBFLQAXG",
    "pnr": "RC-TK-9023418921",
    "trainNumber": "12301",
    "trainName": "Howrah Rajdhani Express",
    "serviceDate": "2026-11-15",
    "coachClassCode": "3A",
    "quotaCode": "GENERAL",
    "overallStatus": "CONFIRMED",
    "passengers": [
      {
        "bookingPassengerId": "...",
        "passengerNumber": 1,
        "passengerName": "Dhanush K",
        "reservationStatus": "CONFIRMED",
        "positionNumber": 0,
        "coachNumber": "B1",
        "berthNumber": "21",
        "berthType": "LOWER_BERTH"
      }
    ],
    "history": [
      {
        "previousState": "RAC",
        "newState": "CONFIRMED",
        "reason": "CANCELLATION_PROMOTION",
        "details": "Promoted to confirmed berth on cancellation",
        "occurredAt": "2026-11-10T14:30:00Z"
      }
    ]
  }
  ```

---

## 2. Administration Endpoints

- **GET** `/api/v1/admin/quotas`: List all configured quotas with criteria.
- **GET** `/api/v1/admin/train-runs/{trainRunId}/quota-allocations`: List quota caps and live confirmed/RAC/WL counts.
- **POST** `/api/v1/admin/quota-allocations`: Configure quota allocation and capacities.
- **GET** `/api/v1/admin/train-runs/{trainRunId}/queues`: Inspect active RAC and Waitlist queue depths.
- **POST** `/api/v1/admin/train-runs/{trainRunId}/process-promotions`: Manually trigger or sweep promotions.

---

## 3. Operations Endpoints

- **GET** `/api/v1/operations/charts`: List prepared departure charts.
- **GET** `/api/v1/operations/charts/{chartId}`: Retrieve detailed coach-wise passenger manifest.
- **POST** `/api/v1/operations/train-runs/{trainRunId}/prepare-chart`: Generate snapshot and initial manifest.
- **POST** `/api/v1/operations/charts/{chartId}/finalize`: Freeze and lock departure chart.
- **POST** `/api/v1/operations/charts/{chartId}/corrections`: Authorize post-chart passenger berth reassignment with audit reason.
